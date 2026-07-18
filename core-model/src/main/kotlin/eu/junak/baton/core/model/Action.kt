package eu.junak.baton.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A client → server command, mirrored from the backend's `Action` discriminated
 * union (`protocol.py`). Serialize with the [Action] base type so the `type`
 * discriminator is written, e.g. `ProtocolJson.encodeToString<Action>(action)`.
 *
 * Field value-range constraints (volume 0..1, fade ≤ 10 000 ms, …) are enforced
 * server-side; this model only describes shape. Guests may send only [Register];
 * everything else requires an authenticated socket.
 */
@Serializable
sealed interface Action {

    @Serializable
    @SerialName("register")
    data class Register(
        val name: String,
        val clientId: String,
        val protocolVersion: Int = 2,
    ) : Action

    @Serializable
    @SerialName("set_volume")
    /** Legacy group-volume action. New clients use [SetDeviceVolume]. */
    data class SetVolume(val volume: Double) : Action

    @Serializable
    @SerialName("pause")
    data object Pause : Action

    @Serializable
    @SerialName("resume")
    data object Resume : Action

    @Serializable
    @SerialName("set_active_mode")
    data class SetActiveMode(val modeId: String?) : Action

    @Serializable
    @SerialName("set_active_outputs")
    data class SetActiveOutputs(val deviceIds: List<String> = emptyList()) : Action

    @Serializable
    @SerialName("set_device_volume")
    data class SetDeviceVolume(val deviceId: String, val volume: Double) : Action

    @Serializable
    @SerialName("position_report")
    data class PositionReport(val positionMs: Int) : Action

    // --- ambient lane -----------------------------------------------------

    @Serializable
    @SerialName("ambient_play_track")
    data class AmbientPlayTrack(val trackId: Int) : Action

    @Serializable
    @SerialName("ambient_set_queue")
    data class AmbientSetQueue(val trackIds: List<Int> = emptyList()) : Action

    @Serializable
    @SerialName("ambient_enqueue")
    data class AmbientEnqueue(val trackId: Int, val position: Int? = null) : Action

    @Serializable
    @SerialName("ambient_clear_queue")
    data object AmbientClearQueue : Action

    @Serializable
    @SerialName("ambient_skip_next")
    data object AmbientSkipNext : Action

    @Serializable
    @SerialName("ambient_skip_prev")
    data object AmbientSkipPrev : Action

    @Serializable
    @SerialName("ambient_seek")
    data class AmbientSeek(val positionMs: Int) : Action

    @Serializable
    @SerialName("ambient_set_loop")
    data class AmbientSetLoop(val loop: LoopMode) : Action

    @Serializable
    @SerialName("ambient_set_shuffle")
    data class AmbientSetShuffle(val shuffle: ShuffleMode) : Action

    @Serializable
    @SerialName("ambient_stop")
    data object AmbientStop : Action

    @Serializable
    @SerialName("ambient_play_playlist")
    data class AmbientPlayPlaylist(val playlistId: Int, val startIndex: Int = 0) : Action

    @Serializable
    @SerialName("ambient_play_folder")
    data class AmbientPlayFolder(val path: String = "", val startIndex: Int = 0) : Action

    // --- mode-scoped session settings -------------------------------------

    @Serializable
    @SerialName("set_active_soundboard")
    data class SetActiveSoundboard(val soundboardId: String?) : Action

    @Serializable
    @SerialName("set_active_presets")
    data class SetActivePresets(val presetIds: List<String> = emptyList()) : Action

    @Serializable
    @SerialName("set_crossfade")
    data class SetCrossfade(
        val crossfadeMs: Int,
        val crossfadeType: CrossfadeType? = null,
    ) : Action

    // --- interrupt lane ---------------------------------------------------

    @Serializable
    @SerialName("fire_interrupt_track")
    data class FireInterruptTrack(
        val trackId: Int,
        val returnToAmbient: Boolean = true,
        val fadeInMs: Int = 0,
        val fadeOutMs: Int = 0,
        val duckTo: Double? = null,
    ) : Action

    @Serializable
    @SerialName("fire_interrupt_playlist")
    data class FireInterruptPlaylist(
        val playlistId: Int,
        val returnToAmbient: Boolean = true,
        val fadeInMs: Int = 0,
        val fadeOutMs: Int = 0,
        val duckTo: Double? = null,
    ) : Action

    @Serializable
    @SerialName("interrupt_skip_next")
    data object InterruptSkipNext : Action

    @Serializable
    @SerialName("interrupt_seek")
    data class InterruptSeek(val positionMs: Int) : Action

    @Serializable
    @SerialName("cancel_interrupt")
    data object CancelInterrupt : Action

    // --- SFX (fire-and-forget) + loops + cues -----------------------------

    @Serializable
    @SerialName("fire_sfx")
    data class FireSfx(
        val soundboardId: String,
        val itemPath: String,
        val volume: Double = 1.0,
    ) : Action

    @Serializable
    @SerialName("start_loop")
    data class StartLoop(
        val id: String,
        val name: String,
        val soundboardId: String,
        val itemPath: String,
        val intervalS: Double,
        val volume: Double = 1.0,
    ) : Action

    @Serializable
    @SerialName("stop_loop")
    data class StopLoop(val id: String) : Action

    @Serializable
    @SerialName("fire_cue")
    data class FireCue(val cueId: String) : Action
}
