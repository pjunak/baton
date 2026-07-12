package eu.junak.baton.feature.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.PlayerState
import eu.junak.baton.core.model.ServerMessage
import eu.junak.baton.core.model.Track
import eu.junak.baton.core.network.CoverArtLoader
import eu.junak.baton.core.network.MediaUrls
import eu.junak.baton.core.network.api.LibraryApi
import eu.junak.baton.core.sync.SyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * The phone-as-speaker engine: a foreground service hosting an [ExoPlayer] that
 * *reconciles* to the server's [PlayerState] — it never drives playback itself.
 * Per `clients/README.md`: the interrupt lane overrides ambient, stream the active
 * track, and seek only on a track change or a big position jump. Gated by
 * [PlaybackController.enabled]; when that goes false the service stops itself.
 *
 * Presents as a real media session: a MediaStyle notification (lock screen /
 * notification shade / hardware media buttons all included) whose transport
 * controls route to the SERVER, not the local player — pressing pause on the
 * lock screen pauses the whole room, exactly like the Console button. That's
 * [RemotePlayer]'s job: it wraps the reconciling ExoPlayer and intercepts every
 * transport command into a server action; the local player only ever *mirrors*
 * the server state that comes back. "Stop" (leave the speaker role) rides along
 * as a session custom command so Android 13+'s system controls show it too.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject lateinit var syncClient: SyncClient
    @Inject lateinit var mediaUrls: MediaUrls
    @Inject lateinit var libraryApi: LibraryApi
    @Inject lateinit var playbackController: PlaybackController
    @Inject lateinit var coverArtLoader: CoverArtLoader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var loadedTrackId: Int? = null
    private var lastServerPositionMs: Int = -1

    /** Live one-shot SFX players (fire-and-forget, layered over the music); each frees itself on end. */
    private val sfxPlayers = mutableListOf<MediaPlayer>()

    /** Track metadata + cover-art caches for the notification/session, plus the
     *  last (track, playing) we posted. Art entries can be null = "no art". */
    private val trackCache = mutableMapOf<Int, Track>()
    private val artCache = mutableMapOf<Int, CoverArt?>()
    private var notifiedTrackId: Int? = null
    private var notifiedPlaying: Boolean = false

    private class CoverArt(val bytes: ByteArray, val bitmap: Bitmap)

    /**
     * The player the media session sees. Transport commands become server actions
     * (the server broadcasts, [reconcile] applies the result to the real player);
     * everything else passes through so position/state reporting stays truthful.
     * Next/previous are force-advertised — the local player holds a single item,
     * which would otherwise hide the skip buttons the whole app exists for.
     */
    private inner class RemotePlayer(local: Player) : ForwardingPlayer(local) {
        override fun setPlayWhenReady(playWhenReady: Boolean) {
            syncClient.send(if (playWhenReady) Action.Resume else Action.Pause)
        }

        override fun play() = setPlayWhenReady(true)

        override fun pause() = setPlayWhenReady(false)

        override fun seekToNext() {
            syncClient.send(Action.AmbientSkipNext)
        }

        override fun seekToNextMediaItem() = seekToNext()

        override fun seekToPrevious() {
            syncClient.send(Action.AmbientSkipPrev)
        }

        override fun seekToPreviousMediaItem() = seekToPrevious()

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                )
                .build()

        override fun isCommandAvailable(command: Int): Boolean =
            getAvailableCommands().contains(command)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player = exo
        mediaSession = buildMediaSession(exo)

        scope.launch {
            combine(syncClient.state, playbackController.enabled, ::Pair).collect { (state, enabled) ->
                reconcile(state, enabled)
            }
        }

        // Fire-and-forget SFX: play each broadcast sound over the music while we're an output.
        scope.launch {
            syncClient.sfxEvents.collect { fireSfx(it) }
        }
    }

    private fun buildMediaSession(exo: ExoPlayer): MediaSession {
        val stopCommand = SessionCommand(CUSTOM_ACTION_STOP, Bundle.EMPTY)
        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult =
                MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                            .buildUpon()
                            .add(stopCommand)
                            .build(),
                    )
                    .build()

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == CUSTOM_ACTION_STOP) {
                    playbackController.setEnabled(false)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }
        return MediaSession.Builder(this, RemotePlayer(exo))
            .setId("baton")
            .setCallback(callback)
            .setCustomLayout(
                listOf(
                    CommandButton.Builder()
                        .setDisplayName("Stop speaker")
                        .setIconResId(R.drawable.ic_stat_stop)
                        .setSessionCommand(stopCommand)
                        .build(),
                ),
            )
            .apply { openAppIntent()?.let(::setSessionActivity) }
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notification transport → server actions (the local player only mirrors the server).
        when (intent?.action) {
            ACTION_STOP -> {
                playbackController.setEnabled(false)
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> {
                syncClient.send(if (syncClient.state.value?.isPlaying == true) Action.Pause else Action.Resume)
                return START_NOT_STICKY
            }
            ACTION_NEXT -> {
                syncClient.send(Action.AmbientSkipNext)
                return START_NOT_STICKY
            }
            ACTION_PREV -> {
                syncClient.send(Action.AmbientSkipPrev)
                return START_NOT_STICKY
            }
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        return START_NOT_STICKY
    }

    /**
     * Map server state onto the local player — see clients/README.md. Crucially this is
     * *edge-triggered*: volume is applied on every emission (cheap and glitch-free), but the player
     * is only re-seeked when the track changes or the server's `position_ms` actually changes. The
     * server re-stamps position only on real seeks/skips, so an unrelated change (a volume tweak, a
     * shuffle toggle, a queue edit) carries a *stale* position and must NOT trigger a seek — doing so
     * was what made playback hitch on every control.
     */
    private fun reconcile(state: PlayerState?, enabled: Boolean) {
        val player = this.player ?: return
        if (!enabled) {
            stopEverything()
            return
        }
        if (state == null) {
            player.playWhenReady = false
            return
        }

        // Volume: master × this device's trim. Applied on every emission (instant, never glitches).
        val trim = (state.deviceVolumes[playbackController.deviceId] ?: 1.0).toFloat()
        player.volume = (state.volume.toFloat() * trim).coerceIn(0f, 1f)

        val interrupt = state.interrupt
        val trackId = interrupt?.currentTrackId ?: state.ambient.currentTrackId
        val playing = interrupt != null || state.isPlaying
        val positionMs = interrupt?.positionMs ?: state.ambient.positionMs
        maybeRefreshNotification(trackId, playing)
        val url = trackId?.let(mediaUrls::stream)

        if (trackId == null || url == null) {
            if (loadedTrackId != null) {
                player.stop()
                loadedTrackId = null
            }
            player.playWhenReady = false
            return
        }

        if (trackId != loadedTrackId) {
            player.setMediaItem(mediaItemFor(trackId, url))
            player.prepare()
            player.seekTo(positionMs.toLong())
            loadedTrackId = trackId
            lastServerPositionMs = positionMs
        } else if (positionMs != lastServerPositionMs) {
            // A genuine server-side seek/skip — but only physically seek if we've actually drifted
            // (a position report that already matches us shouldn't rebuffer).
            lastServerPositionMs = positionMs
            if (abs(player.currentPosition - positionMs) > SEEK_THRESHOLD_MS) {
                player.seekTo(positionMs.toLong())
            }
        }
        player.playWhenReady = playing
    }

    private fun stopEverything() {
        player?.let {
            it.playWhenReady = false
            it.stop()
        }
        loadedTrackId = null
        releaseAllSfx()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Play one broadcast SFX as a transient [MediaPlayer] layered over the music — only while this
     * phone is a live output. Volume is the fire volume × master × this device's trim (matching the
     * web client). Each player frees itself on completion/error, so overlapping SFX simply stack.
     */
    private fun fireSfx(event: ServerMessage.SfxFired) {
        if (!playbackController.enabled.value) return
        val state = syncClient.state.value ?: return
        val url = mediaUrls.sfx(event.itemPath) ?: return
        val trim = (state.deviceVolumes[playbackController.deviceId] ?: 1.0).toFloat()
        val vol = (event.volume.toFloat() * state.volume.toFloat() * trim).coerceIn(0f, 1f)

        val mp = MediaPlayer()
        mp.setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        mp.setVolume(vol, vol)
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener { releaseSfx(it) }
        mp.setOnErrorListener { p, _, _ ->
            releaseSfx(p)
            true
        }
        sfxPlayers.add(mp)
        runCatching {
            mp.setDataSource(url)
            mp.prepareAsync()
        }.onFailure { releaseSfx(mp) }
    }

    private fun releaseSfx(mp: MediaPlayer) {
        mp.release()
        sfxPlayers.remove(mp)
    }

    private fun releaseAllSfx() {
        sfxPlayers.forEach { it.release() }
        sfxPlayers.clear()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shown while this phone is acting as an audio output" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Re-post the notification when the track or play state changes. Title/artist and
     * cover art resolve lazily; when they land, the notification is re-posted AND the
     * session's media item is enriched (same URI, richer metadata — media3 applies
     * that seamlessly, no rebuffer), which is what feeds the lock-screen widget and
     * Android 13+'s system media controls.
     */
    private fun maybeRefreshNotification(trackId: Int?, playing: Boolean) {
        if (trackId == notifiedTrackId && playing == notifiedPlaying) return
        notifiedTrackId = trackId
        notifiedPlaying = playing
        postNotification()
        if (trackId == null) return
        if (trackCache[trackId] != null && artCache.containsKey(trackId)) {
            pushSessionMetadata(trackId)
            return
        }
        scope.launch {
            if (trackCache[trackId] == null) {
                runCatching { libraryApi.track(trackId) }.getOrNull()?.let { trackCache[trackId] = it }
            }
            if (!artCache.containsKey(trackId)) {
                if (artCache.size >= ART_CACHE_MAX) {
                    artCache.keys.take(artCache.size - ART_CACHE_MAX + 1).forEach(artCache::remove)
                }
                artCache[trackId] = fetchCoverArt(trackId)
            }
            if (notifiedTrackId == trackId) {
                pushSessionMetadata(trackId)
                postNotification()
            }
        }
    }

    /** Fetch + decode the track's cover art; null for "server has none" or any failure. */
    private suspend fun fetchCoverArt(trackId: Int): CoverArt? {
        val bytes = coverArtLoader.coverBytes(trackId) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return CoverArt(bytes, bitmap)
    }

    /** Re-stamp the loaded media item with the metadata we now have — the session
     *  (lock screen, system controls, watches, Android Auto) reads it from there. */
    private fun pushSessionMetadata(trackId: Int) {
        val player = this.player ?: return
        if (loadedTrackId != trackId || player.mediaItemCount == 0) return
        val url = mediaUrls.stream(trackId) ?: return
        player.replaceMediaItem(0, mediaItemFor(trackId, url))
    }

    private fun mediaItemFor(trackId: Int, url: String): MediaItem {
        val track = trackCache[trackId]
        val art = artCache[trackId]
        val metadata = MediaMetadata.Builder()
            .setTitle(track?.effectiveTitle?.takeIf { it.isNotBlank() } ?: "Baton speaker")
            .setArtist(track?.artist?.takeIf { it.isNotBlank() })
            .setAlbumTitle(track?.album?.takeIf { it.isNotBlank() })
            .apply {
                if (art != null) setArtworkData(art.bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            .build()
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(trackId.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    private fun postNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun openAppIntent(): PendingIntent? =
        // Launch the app on tap without depending on :app — resolve its launcher intent by package.
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }

    private fun buildNotification(): Notification {
        val track = notifiedTrackId?.let { trackCache[it] }
        val art = notifiedTrackId?.let { artCache[it] }
        val playing = notifiedPlaying
        val title = track?.effectiveTitle?.takeIf { it.isNotBlank() } ?: "Baton speaker"
        val text = when {
            track == null -> "This phone is playing the session audio"
            playing -> track.artist.ifBlank { "Unknown artist" }
            else -> "Paused · ${track.artist.ifBlank { "Unknown artist" }}"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_baton)
            .setLargeIcon(art?.bitmap)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppIntent())
            // Actions for pre-13 renderers; Android 13+ derives its buttons from the
            // media session (RemotePlayer's commands + the Stop custom command).
            .addAction(android.R.drawable.ic_media_previous, "Previous", serviceAction(ACTION_PREV, 2))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                serviceAction(ACTION_PLAY_PAUSE, 3),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", serviceAction(ACTION_NEXT, 4))
            .addAction(R.drawable.ic_stat_stop, "Stop", serviceAction(ACTION_STOP, 0))
        mediaSession?.let {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(it).setShowActionsInCompactView(0, 1, 2),
            )
        }
        return builder.build()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onDestroy() {
        scope.cancel()
        releaseAllSfx()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "baton_playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "eu.junak.baton.feature.playback.STOP"
        const val ACTION_PLAY_PAUSE = "eu.junak.baton.feature.playback.PLAY_PAUSE"
        const val ACTION_NEXT = "eu.junak.baton.feature.playback.NEXT"
        const val ACTION_PREV = "eu.junak.baton.feature.playback.PREV"
        const val CUSTOM_ACTION_STOP = "eu.junak.baton.feature.playback.STOP_SPEAKER"
        const val SEEK_THRESHOLD_MS = 1500L
        const val ART_CACHE_MAX = 6
    }
}
