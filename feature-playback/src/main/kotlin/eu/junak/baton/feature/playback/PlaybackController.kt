package eu.junak.baton.feature.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.network.data.NetworkStore
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.core.sync.SyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone-as-speaker on/off — owned at app scope so the Console switch and the
 * [PlaybackService] share one source of truth. Turning it on starts the foreground
 * playback service AND designates this device as an output on the server (so it
 * shows up in everyone's device list / the Devices tab).
 *
 * The local [enabled] flag is what actually gates audio, so it survives reconnects
 * (the server clears `active_output_device_ids` on disconnect) — matching the
 * reference client's "keep a local on/off" recommendation in `clients/README.md`.
 */
@Singleton
class PlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val syncClient: SyncClient,
    private val networkStore: NetworkStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _enabled = MutableStateFlow(false)

    /** Whether this phone is currently acting as an audio output. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** This device's protocol id (`device_id == client_id`). */
    val deviceId: String get() = networkStore.clientId

    init {
        // The server wipes `active_output_device_ids` when a socket drops, so
        // after any reconnect this phone kept playing (the local flag gates
        // audio) but vanished from every client's Devices/active list. Re-assert
        // on each CONNECTED edge; the register handshake was already enqueued on
        // the same ordered socket before the status flips, so the server can
        // resolve our client_id.
        scope.launch {
            syncClient.status.collect { status ->
                if (status != ConnectionStatus.CONNECTED || !_enabled.value) return@collect
                val current = syncClient.state.value?.activeOutputDeviceIds.orEmpty()
                if (deviceId !in current) {
                    syncClient.send(Action.SetActiveOutputs((current + deviceId).distinct()))
                }
            }
        }
    }

    /** Turn this phone into an audio output (or off). Idempotent. */
    fun setEnabled(on: Boolean) {
        if (_enabled.value == on) return
        _enabled.value = on
        if (on) {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        }
        // Best-effort: keep the server's output list honest so the Devices tab and
        // other clients reflect this phone. (No-op while disconnected; the local
        // flag above is the real gate.)
        val current = syncClient.state.value?.activeOutputDeviceIds.orEmpty()
        val next = if (on) (current + deviceId).distinct() else current - deviceId
        if (next != current) syncClient.send(Action.SetActiveOutputs(next))
    }

    fun toggle() = setEnabled(!_enabled.value)
}
