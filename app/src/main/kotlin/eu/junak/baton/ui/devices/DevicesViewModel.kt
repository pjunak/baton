package eu.junak.baton.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.core.sync.SyncClient
import eu.junak.baton.feature.playback.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Manage every connected output: toggle which devices are active outputs
 * ([Action.SetActiveOutputs]) and trim per-device volume ([Action.SetDeviceVolume]).
 * This phone appears here too; toggling it routes through [PlaybackController] (the
 * same path as the Console's "Play on this phone" switch) so the local engine reacts.
 */
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val syncClient: SyncClient,
    private val playbackController: PlaybackController,
) : ViewModel() {

    data class DeviceRow(
        val deviceId: String,
        val name: String,
        val isThisDevice: Boolean,
        val isActiveOutput: Boolean,
        val volume: Float,
    )

    data class UiState(
        val devices: List<DeviceRow> = emptyList(),
        val connected: Boolean = false,
    )

    val uiState: StateFlow<UiState> =
        combine(syncClient.state, syncClient.status, playbackController.enabled) { state, status, localEnabled ->
            val active = state?.activeOutputDeviceIds.orEmpty().toSet()
            val volumes = state?.deviceVolumes.orEmpty()
            val myId = playbackController.deviceId
            val devices = state?.connectedDevices.orEmpty().map { device ->
                val isThisDevice = device.deviceId == myId
                DeviceRow(
                    deviceId = device.deviceId,
                    name = device.name,
                    isThisDevice = isThisDevice,
                    isActiveOutput = device.deviceId in active || (isThisDevice && localEnabled),
                    volume = (volumes[device.deviceId] ?: 1.0).toFloat(),
                )
            }
            UiState(devices = devices, connected = status == ConnectionStatus.CONNECTED)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    fun toggleOutput(deviceId: String, on: Boolean) {
        if (deviceId == playbackController.deviceId) {
            playbackController.setEnabled(on) // this phone — drive the local engine + server in one place
            return
        }
        val current = syncClient.state.value?.activeOutputDeviceIds.orEmpty()
        val next = if (on) (current + deviceId).distinct() else current - deviceId
        syncClient.send(Action.SetActiveOutputs(next))
    }

    fun setDeviceVolume(deviceId: String, volume: Float) {
        syncClient.send(Action.SetDeviceVolume(deviceId, volume.toDouble().coerceIn(0.0, 1.0)))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
