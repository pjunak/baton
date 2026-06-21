package eu.junak.baton.ui.console

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.LoopMode
import eu.junak.baton.core.model.ShuffleMode
import eu.junak.baton.core.model.Track
import eu.junak.baton.core.network.ServerConfig
import eu.junak.baton.core.network.api.LibraryApi
import eu.junak.baton.core.network.auth.AuthRepository
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.core.sync.SyncClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The console: reconciles to the server's live [eu.junak.baton.core.model.PlayerState]
 * via [SyncClient], resolves track metadata for the now-playing card and the queue,
 * and sends transport/volume/seek/shuffle/loop actions back. The server is the sole
 * writer of state; everything here is either a projection of it or a command to it.
 *
 * Playback position is *dead-reckoned*: the server only reports `positionMs` at
 * discrete moments (seek, track change, pause), so we snap our clock to each report
 * and tick it forward locally while playing — the seek bar then moves smoothly
 * without flooding the socket with position queries.
 */
@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val syncClient: SyncClient,
    private val libraryApi: LibraryApi,
    private val authRepository: AuthRepository,
    private val serverConfig: ServerConfig,
) : ViewModel() {

    /** One queue slot: the server's track id plus its resolved metadata (null if unknown/deleted). */
    data class QueueEntry(val trackId: Int, val track: Track?)

    data class UiState(
        val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
        val isPlaying: Boolean = false,
        val nowPlaying: Track? = null,
        val volume: Double = 1.0,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val loop: LoopMode = LoopMode.OFF,
        val shuffle: ShuffleMode = ShuffleMode.OFF,
        val queue: List<QueueEntry> = emptyList(),
    )

    private val trackCache = mutableMapOf<Int, Track>()
    private val nowPlaying = MutableStateFlow<Track?>(null)
    private val queueEntries = MutableStateFlow<List<QueueEntry>>(emptyList())

    /** Dead-reckoned position: snapped to the server on each report, ticked locally between. */
    private val position = MutableStateFlow(0)

    val uiState: StateFlow<UiState> =
        combine(
            syncClient.status,
            syncClient.state,
            nowPlaying,
            queueEntries,
            position,
        ) { status, state, track, queue, posMs ->
            UiState(
                status = status,
                isPlaying = state?.isPlaying == true,
                nowPlaying = track,
                volume = state?.volume ?: 1.0,
                positionMs = posMs,
                durationMs = ((track?.lengthS ?: 0.0) * 1000).toInt(),
                loop = state?.ambient?.loop ?: LoopMode.OFF,
                shuffle = state?.ambient?.shuffle ?: ShuffleMode.OFF,
                queue = queue,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    init {
        if (syncClient.status.value == ConnectionStatus.DISCONNECTED) {
            syncClient.connect(deviceName())
        }
        observeCurrentTrack()
        observeQueue()
        observeServerPosition()
        startPositionTicker()
    }

    private fun observeCurrentTrack() {
        viewModelScope.launch {
            syncClient.state
                .map { it?.ambient?.currentTrackId }
                .distinctUntilChanged()
                .collect { trackId -> nowPlaying.value = trackId?.let { resolveTrack(it) } }
        }
    }

    private fun observeQueue() {
        viewModelScope.launch {
            syncClient.state
                .map { it?.ambient?.queue ?: emptyList() }
                .distinctUntilChanged()
                .collect { ids -> queueEntries.value = resolveQueue(ids) }
        }
    }

    /** Snap the local clock whenever the server reports a fresh position (seek, track change, pause). */
    private fun observeServerPosition() {
        viewModelScope.launch {
            syncClient.state
                .map { it?.ambient?.let { a -> a.currentTrackId to a.positionMs } }
                .distinctUntilChanged()
                .collect { position.value = it?.second ?: 0 }
        }
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val playing = syncClient.state.value?.isPlaying == true
                val durMs = ((nowPlaying.value?.lengthS ?: 0.0) * 1000).toInt()
                if (playing && durMs > 0) {
                    position.update { (it + TICK_MS.toInt()).coerceAtMost(durMs) }
                }
            }
        }
    }

    private suspend fun resolveTrack(id: Int): Track? {
        trackCache[id]?.let { return it }
        return runCatching { libraryApi.track(id) }.getOrNull()?.also { trackCache[id] = it }
    }

    /** Batch-resolve queue ids (one round trip for the misses), preserving order and duplicates. */
    private suspend fun resolveQueue(ids: List<Int>): List<QueueEntry> {
        if (ids.isEmpty()) return emptyList()
        val missing = ids.filter { it !in trackCache }.distinct()
        if (missing.isNotEmpty()) {
            runCatching { libraryApi.tracks(missing.joinToString(",")) }
                .getOrNull()
                ?.forEach { trackCache[it.id] = it }
        }
        return ids.map { QueueEntry(it, trackCache[it]) }
    }

    // --- transport --------------------------------------------------------

    fun playPause() {
        syncClient.send(if (uiState.value.isPlaying) Action.Pause else Action.Resume)
    }

    fun skipNext() = syncClient.send(Action.AmbientSkipNext)

    fun skipPrevious() = syncClient.send(Action.AmbientSkipPrev)

    fun setVolume(volume: Double) {
        syncClient.send(Action.SetVolume(volume.coerceIn(0.0, 1.0)))
    }

    fun seekTo(positionMs: Int) {
        val clamped = positionMs.coerceAtLeast(0)
        position.value = clamped // optimistic: move the thumb now, the server will confirm
        syncClient.send(Action.AmbientSeek(clamped))
    }

    fun cycleShuffle() {
        val next = when (uiState.value.shuffle) {
            ShuffleMode.OFF -> ShuffleMode.RANDOM
            ShuffleMode.RANDOM -> ShuffleMode.WEIGHTED
            ShuffleMode.WEIGHTED -> ShuffleMode.OFF
        }
        syncClient.send(Action.AmbientSetShuffle(next))
    }

    fun cycleLoop() {
        val next = when (uiState.value.loop) {
            LoopMode.OFF -> LoopMode.FOLLOW
            LoopMode.FOLLOW -> LoopMode.QUEUE
            LoopMode.QUEUE -> LoopMode.TRACK
            LoopMode.TRACK -> LoopMode.OFF
        }
        syncClient.send(Action.AmbientSetLoop(next))
    }

    fun clearQueue() = syncClient.send(Action.AmbientClearQueue)

    /** Remove the slot at [index] — index-based so duplicate track ids in the queue stay unambiguous. */
    fun removeFromQueue(index: Int) {
        val ids = syncClient.state.value?.ambient?.queue ?: return
        if (index !in ids.indices) return
        syncClient.send(Action.AmbientSetQueue(ids.toMutableList().apply { removeAt(index) }))
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            syncClient.disconnect()
            authRepository.logout()
            serverConfig.forget()
            onDone()
        }
    }

    private fun deviceName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Baton"

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 500L
    }
}
