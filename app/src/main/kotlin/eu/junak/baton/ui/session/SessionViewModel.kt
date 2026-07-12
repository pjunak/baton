package eu.junak.baton.ui.session

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.network.api.InterruptTemplate
import eu.junak.baton.core.network.api.ModeDetail
import eu.junak.baton.core.network.api.ModeSummary
import eu.junak.baton.core.network.api.ModesApi
import eu.junak.baton.core.network.api.PlaylistMeta
import eu.junak.baton.core.network.api.PlaylistsApi
import eu.junak.baton.core.network.api.PresetSummary
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.core.sync.SyncClient
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * The Session tab — the DM's live-firing surface, mirroring the web Console's
 * per-mode panels: mode (scene) switching, one-tap **cues**, the active mode's
 * **soundboard** (tap = fire once, long-press = start a repeating loop), **EQ
 * preset** toggles, pre-configured **interrupts**, and live status for the
 * interrupt lane + looping SFX.
 *
 * Reads are REST ([ModesApi]/[PlaylistsApi]: mode list, the active mode's
 * detail + presets + playlists); every mutation is a WebSocket [Action]. The
 * server owns all state — active mode/presets, the interrupt and the loops are
 * read straight off the PlayerState, so this stays in lockstep with the web
 * client and other remotes.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val syncClient: SyncClient,
    private val modesApi: ModesApi,
    private val playlistsApi: PlaylistsApi,
) : ViewModel() {

    data class ModeItem(val id: String, val name: String, val active: Boolean)
    data class SoundItem(val name: String, val file: String, val icon: String?)
    data class SoundCategory(val name: String, val items: List<SoundItem>)
    data class LoopItem(val id: String, val name: String)
    data class CueItem(val id: String, val name: String, val description: String?)
    data class PresetItem(val id: String, val name: String, val active: Boolean)

    /** One fireable interrupt template. `detail` is the human meta line;
     *  `canFire` is false when its reference no longer resolves (missing
     *  playlist / no default soundboard) — shown instead of firing nothing. */
    data class InterruptItem(
        val name: String,
        val detail: String,
        val canFire: Boolean,
        val template: InterruptTemplate,
        val playlistId: Int?,
    )

    data class UiState(
        val connected: Boolean = false,
        val modes: List<ModeItem> = emptyList(),
        val activeModeId: String? = null,
        val cues: List<CueItem> = emptyList(),
        val soundboardId: String? = null,
        val soundboardName: String? = null,
        val soundCategories: List<SoundCategory> = emptyList(),
        val presets: List<PresetItem> = emptyList(),
        val interrupts: List<InterruptItem> = emptyList(),
        val interruptActive: Boolean = false,
        val loops: List<LoopItem> = emptyList(),
    )

    /** Everything fetched per active mode, replaced atomically on mode change. */
    private data class ModeData(
        val detail: ModeDetail? = null,
        val presets: List<PresetSummary> = emptyList(),
        val playlists: List<PlaylistMeta> = emptyList(),
    )

    private val modes = MutableStateFlow<List<ModeSummary>>(emptyList())
    private val modeData = MutableStateFlow(ModeData())

    val uiState: StateFlow<UiState> =
        combine(syncClient.status, syncClient.state, modes, modeData) { status, state, modeList, data ->
            val activeModeId = state?.activeModeId
            val detail = data.detail
            val board = detail?.let { d ->
                val id = state?.activeSoundboardId ?: d.defaultSoundboard
                id?.let { d.soundboards[it] } ?: d.soundboards.values.firstOrNull()
            }
            val activePresetIds = state?.activePresetIds.orEmpty().toSet()
            val playlistIdByName = data.playlists.associate { it.name to it.id }
            UiState(
                connected = status == ConnectionStatus.CONNECTED,
                modes = modeList.map { ModeItem(it.id, it.name, it.id == activeModeId) },
                activeModeId = activeModeId,
                cues = detail?.cues?.values
                    ?.map { CueItem(it.id, it.name, it.description) }
                    ?.sortedBy { it.name }
                    .orEmpty(),
                soundboardId = board?.id,
                soundboardName = board?.name ?: board?.id,
                soundCategories = board?.categories?.map { cat ->
                    SoundCategory(cat.name, cat.items.map { SoundItem(it.name, it.file, it.icon) })
                } ?: emptyList(),
                presets = data.presets.map { PresetItem(it.id, it.name, it.id in activePresetIds) },
                interrupts = detail?.interrupts
                    ?.map { interruptItem(it, playlistIdByName, detail.defaultSoundboard) }
                    .orEmpty(),
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
        // Pull everything the active mode scopes (soundboards/cues/interrupts,
        // EQ presets, playlists-for-interrupt-resolution) whenever it changes.
        viewModelScope.launch {
            syncClient.state.map { it?.activeModeId }.distinctUntilChanged().collect { id ->
                modeData.value = if (id == null) {
                    ModeData()
                } else {
                    coroutineScope {
                        val detail = async { runCatching { modesApi.detail(id) }.getOrNull() }
                        val presets = async { runCatching { modesApi.presets(id) }.getOrNull() }
                        val playlists = async { runCatching { playlistsApi.list(id) }.getOrNull() }
                        ModeData(
                            detail = detail.await(),
                            presets = presets.await().orEmpty(),
                            playlists = playlists.await().orEmpty(),
                        )
                    }
                }
            }
        }
    }

    private fun refreshModes() {
        viewModelScope.launch {
            runCatching { modesApi.list() }.onSuccess { modes.value = it }
        }
    }

    private fun interruptItem(
        template: InterruptTemplate,
        playlistIdByName: Map<String, Int>,
        defaultSoundboard: String?,
    ): InterruptItem {
        val playlistId = template.playlist?.let(playlistIdByName::get)
        val canFire: Boolean
        val detail: String
        when {
            template.playlist != null -> {
                canFire = playlistId != null
                detail = if (playlistId == null) {
                    "playlist \"${template.playlist}\" is missing"
                } else {
                    buildString {
                        append("playlist · ${template.playlist}")
                        template.duckTo?.let { append(" · ducks to ${(it * 100).roundToInt()}%") }
                    }
                }
            }
            template.soundboardItem != null -> {
                canFire = defaultSoundboard != null
                detail = if (defaultSoundboard == null) {
                    "needs a default soundboard"
                } else {
                    "sfx · ${template.soundboardItem}"
                }
            }
            else -> {
                canFire = false
                detail = "empty template"
            }
        }
        return InterruptItem(template.name, detail, canFire, template, playlistId)
    }

    /** Tap a mode to activate it; tap the active one again to clear back to no mode. */
    fun toggleMode(id: String) {
        val current = syncClient.state.value?.activeModeId
        syncClient.send(Action.SetActiveMode(if (current == id) null else id))
    }

    fun fireCue(id: String) = syncClient.send(Action.FireCue(id))

    fun fireSfx(file: String) {
        val board = uiState.value.soundboardId ?: return
        syncClient.send(Action.FireSfx(soundboardId = board, itemPath = file))
    }

    /** Start a server-driven repeating SFX (shows on every client's loops panel). */
    fun startLoop(name: String, file: String, intervalS: Double) {
        val board = uiState.value.soundboardId ?: return
        syncClient.send(
            Action.StartLoop(
                id = UUID.randomUUID().toString(),
                name = name,
                soundboardId = board,
                itemPath = file,
                intervalS = intervalS,
            ),
        )
    }

    fun togglePreset(id: String) {
        val active = syncClient.state.value?.activePresetIds.orEmpty()
        val next = if (id in active) active - id else active + id
        syncClient.send(Action.SetActivePresets(next))
    }

    fun clearPresets() = syncClient.send(Action.SetActivePresets(emptyList()))

    fun fireInterrupt(item: InterruptItem) {
        val template = item.template
        val sfxItem = template.soundboardItem
        when {
            item.playlistId != null -> syncClient.send(
                Action.FireInterruptPlaylist(
                    playlistId = item.playlistId,
                    returnToAmbient = template.returnToAmbient,
                    fadeInMs = template.fadeInMs,
                    fadeOutMs = template.fadeOutMs,
                    duckTo = template.duckTo,
                ),
            )
            sfxItem != null -> {
                val board = modeData.value.detail?.defaultSoundboard ?: return
                syncClient.send(Action.FireSfx(soundboardId = board, itemPath = sfxItem))
            }
        }
    }

    fun cancelInterrupt() = syncClient.send(Action.CancelInterrupt)

    fun stopLoop(id: String) = syncClient.send(Action.StopLoop(id))

    private fun deviceName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Baton"

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
