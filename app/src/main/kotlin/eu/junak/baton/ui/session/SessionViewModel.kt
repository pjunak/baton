package eu.junak.baton.ui.session

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.network.api.ModeDetail
import eu.junak.baton.core.network.api.ModeSummary
import eu.junak.baton.core.network.api.ModesApi
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.core.sync.SyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Session tab: mode (scene) switching, the active mode's soundboard (fire-and-
 * forget SFX), and live status for the interrupt lane + looping SFX. Mode lists and
 * soundboard contents come over REST ([ModesApi]); activating a mode, firing SFX and
 * stopping loops/interrupts are WebSocket [Action]s. The server owns all state — the
 * active mode, soundboard, interrupt and loops are read straight off the PlayerState.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val syncClient: SyncClient,
    private val modesApi: ModesApi,
) : ViewModel() {

    data class ModeItem(val id: String, val name: String, val active: Boolean)
    data class SoundItem(val name: String, val file: String, val icon: String?)
    data class SoundCategory(val name: String, val items: List<SoundItem>)
    data class LoopItem(val id: String, val name: String)

    data class UiState(
        val connected: Boolean = false,
        val modes: List<ModeItem> = emptyList(),
        val activeModeId: String? = null,
        val soundboardId: String? = null,
        val soundboardName: String? = null,
        val soundCategories: List<SoundCategory> = emptyList(),
        val interruptActive: Boolean = false,
        val loops: List<LoopItem> = emptyList(),
    )

    private val modes = MutableStateFlow<List<ModeSummary>>(emptyList())
    private val activeDetail = MutableStateFlow<ModeDetail?>(null)

    val uiState: StateFlow<UiState> =
        combine(syncClient.status, syncClient.state, modes, activeDetail) { status, state, modeList, detail ->
            val activeModeId = state?.activeModeId
            val board = detail?.let { d ->
                val id = state?.activeSoundboardId ?: d.defaultSoundboard
                id?.let { d.soundboards[it] } ?: d.soundboards.values.firstOrNull()
            }
            UiState(
                connected = status == ConnectionStatus.CONNECTED,
                modes = modeList.map { ModeItem(it.id, it.name, it.id == activeModeId) },
                activeModeId = activeModeId,
                soundboardId = board?.id,
                soundboardName = board?.name ?: board?.id,
                soundCategories = board?.categories?.map { cat ->
                    SoundCategory(cat.name, cat.items.map { SoundItem(it.name, it.file, it.icon) })
                } ?: emptyList(),
                interruptActive = state?.interrupt != null,
                loops = state?.loopingSfx?.map { LoopItem(it.id, it.name) } ?: emptyList(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    init {
        // The Console normally opens the socket first; connect defensively in case the
        // app was restored straight onto this tab (so Session works standalone too).
        if (syncClient.status.value == ConnectionStatus.DISCONNECTED) {
            syncClient.connect(deviceName())
        }
        refreshModes()
        // Pull the active mode's full detail (its soundboards) whenever it changes.
        viewModelScope.launch {
            syncClient.state.map { it?.activeModeId }.distinctUntilChanged().collect { id ->
                activeDetail.value = id?.let { runCatching { modesApi.detail(it) }.getOrNull() }
            }
        }
    }

    private fun refreshModes() {
        viewModelScope.launch {
            runCatching { modesApi.list() }.onSuccess { modes.value = it }
        }
    }

    /** Tap a mode to activate it; tap the active one again to clear back to no mode. */
    fun toggleMode(id: String) {
        val current = syncClient.state.value?.activeModeId
        syncClient.send(Action.SetActiveMode(if (current == id) null else id))
    }

    fun fireSfx(file: String) {
        val board = uiState.value.soundboardId ?: return
        syncClient.send(Action.FireSfx(soundboardId = board, itemPath = file))
    }

    fun cancelInterrupt() = syncClient.send(Action.CancelInterrupt)

    fun stopLoop(id: String) = syncClient.send(Action.StopLoop(id))

    private fun deviceName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Baton"

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
