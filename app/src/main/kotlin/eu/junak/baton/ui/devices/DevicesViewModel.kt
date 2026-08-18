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
 * Manage every connected output: choose one output by default, optionally add
 * more outputs ([Action.SetActiveOutputs]), and trim per-device volume
 * ([Action.SetDeviceVolume]).
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
            val defaultVolume = state?.defaultDeviceVolume
            val myId = playbackController.deviceId
            val devices = state?.connectedDevices.orEmpty().map { device ->
                val isThisDevice = device.deviceId == myId
                DeviceRow(
                    deviceId = device.deviceId,
                    name = device.name,
                    isThisDevice = isThisDevice,
                    isActiveOutput = device.deviceId in active || (isThisDevice && localEnabled),
                    volume = if (defaultVolume == null) {
                        ((state?.volume ?: 1.0) * (volumes[device.deviceId] ?: 1.0)).toFloat()
                    } else {
                        (volumes[device.deviceId] ?: defaultVolume).toFloat()
                    },
                )
            }
            UiState(devices = devices, connected = status == ConnectionStatus.CONNECTED)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    fun toggleOutput(deviceId: String, on: Boolean, allowMultiple: Boolean) {
        val current = effectiveActiveOutputs()
        applyActiveOutputs(nextActiveOutputs(current, deviceId, on, allowMultiple))
    }

    fun setMultipleOutputs(enabled: Boolean) {
        if (enabled) return
        val current = effectiveActiveOutputs()
        if (current.size > 1) applyActiveOutputs(collapseToSingleOutput(current))
    }

    fun setDeviceVolume(deviceId: String, volume: Float) {
        syncClient.setDeviceVolume(deviceId, volume.toDouble())
    }

    private fun effectiveActiveOutputs(): List<String> {
        val current = syncClient.state.value?.activeOutputDeviceIds.orEmpty()
        val myId = playbackController.deviceId
        return if (playbackController.enabled.value && myId !in current) current + myId else current
    }

    /** Keep local phone playback and canonical server membership in one transition. */
    private fun applyActiveOutputs(next: List<String>) {
        val phoneEnabled = playbackController.enabled.value
        val shouldEnablePhone = playbackController.deviceId in next
        if (phoneEnabled != shouldEnablePhone) {
            playbackController.setEnabled(shouldEnablePhone, outputDeviceIds = next)
        } else {
            val current = syncClient.state.value?.activeOutputDeviceIds.orEmpty()
            if (next != current) syncClient.send(Action.SetActiveOutputs(next))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun nextActiveOutputs(
    current: List<String>,
    deviceId: String,
    on: Boolean,
    allowMultiple: Boolean,
): List<String> =
    if (on) {
        if (allowMultiple) (current + deviceId).distinct() else listOf(deviceId)
    } else {
        current - deviceId
    }

internal fun collapseToSingleOutput(current: List<String>): List<String> = current.take(1)
