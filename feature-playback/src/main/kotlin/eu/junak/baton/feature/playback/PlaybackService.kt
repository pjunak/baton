package eu.junak.baton.feature.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.AndroidEntryPoint
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.PlayerState
import eu.junak.baton.core.model.ServerMessage
import eu.junak.baton.core.model.Track
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
 * Plays the music lane, layers fire-and-forget SFX, and puts transport controls
 * (prev / play-pause / next / stop) on the notification — all routing back to the server.
 * A full Media3 MediaSession (hardware media-button + rich lock-screen widget) is a follow-up.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject lateinit var syncClient: SyncClient
    @Inject lateinit var mediaUrls: MediaUrls
    @Inject lateinit var libraryApi: LibraryApi
    @Inject lateinit var playbackController: PlaybackController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var loadedTrackId: Int? = null
    private var lastServerPositionMs: Int = -1

    /** Live one-shot SFX players (fire-and-forget, layered over the music); each frees itself on end. */
    private val sfxPlayers = mutableListOf<MediaPlayer>()

    /** Track metadata cache for the notification, plus the last (track, playing) we posted. */
    private val trackCache = mutableMapOf<Int, Track>()
    private var notifiedTrackId: Int? = null
    private var notifiedPlaying: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        player = ExoPlayer.Builder(this)
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
            player.setMediaItem(MediaItem.fromUri(url))
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

    /** Re-post the notification when the track or play state changes; resolve title/artist lazily. */
    private fun maybeRefreshNotification(trackId: Int?, playing: Boolean) {
        if (trackId == notifiedTrackId && playing == notifiedPlaying) return
        notifiedTrackId = trackId
        notifiedPlaying = playing
        val cached = trackId?.let { trackCache[it] }
        postNotification(cached, playing)
        if (trackId != null && cached == null) {
            scope.launch {
                val resolved = runCatching { libraryApi.track(trackId) }.getOrNull() ?: return@launch
                trackCache[trackId] = resolved
                if (notifiedTrackId == trackId) postNotification(resolved, notifiedPlaying)
            }
        }
    }

    private fun postNotification(track: Track?, playing: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(track, playing))
    }

    private fun buildNotification(track: Track? = null, playing: Boolean = true): Notification {
        // Launch the app on tap without depending on :app — resolve its launcher intent by package.
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val title = track?.effectiveTitle?.takeIf { it.isNotBlank() } ?: "Baton speaker"
        val text = when {
            track == null -> "This phone is playing the session audio"
            playing -> track.artist.ifBlank { "Unknown artist" }
            else -> "Paused · ${track.artist.ifBlank { "Unknown artist" }}"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_previous, "Previous", serviceAction(ACTION_PREV, 2))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                serviceAction(ACTION_PLAY_PAUSE, 3),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", serviceAction(ACTION_NEXT, 4))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", serviceAction(ACTION_STOP, 0))
            .build()
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
        const val SEEK_THRESHOLD_MS = 1500L
    }
}
