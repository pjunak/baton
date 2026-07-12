package eu.junak.baton.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How `skip_next` advances. Mutually exclusive with repeat by construction. */
@Serializable
enum class LoopMode {
    @SerialName("off")
    OFF,

    @SerialName("follow")
    FOLLOW,

    @SerialName("queue")
    QUEUE,

    @SerialName("track")
    TRACK,
}

@Serializable
enum class ShuffleMode {
    @SerialName("off")
    OFF,

    @SerialName("random")
    RANDOM,
}

@Serializable
enum class CrossfadeType {
    @SerialName("linear")
    LINEAR,

    @SerialName("equal_power")
    EQUAL_POWER,

    @SerialName("cut")
    CUT,
}

/** The "main player" lane: current track, what's next, what already played. */
@Serializable
data class AmbientState(
    val currentTrackId: Int? = null,
    val queue: List<Int> = emptyList(),
    val history: List<Int> = emptyList(),
    val positionMs: Int = 0,
    val loop: LoopMode = LoopMode.OFF,
    val shuffle: ShuffleMode = ShuffleMode.OFF,
    val sourcePlaylistId: Int? = null,
)

/**
 * A short audio override that takes over while present. `duckTo` controls
 * ambient during the interrupt: null = ambient pauses; 0..1 = ambient keeps
 * playing at that volume multiplier (a cinematic duck).
 */
@Serializable
data class InterruptState(
    val currentTrackId: Int,
    val queue: List<Int> = emptyList(),
    val positionMs: Int = 0,
    val returnToAmbient: Boolean = true,
    val fadeInMs: Int = 0,
    val fadeOutMs: Int = 0,
    val duckTo: Double? = null,
)

/** A repeating SFX driven by a server-side timer. `id` is the stop handle. */
@Serializable
data class LoopingSfx(
    val id: String,
    val name: String,
    val soundboardId: String,
    val itemPath: String,
    val intervalS: Double,
    val volume: Double = 1.0,
)

/** A currently-connected device. `isOutput` is the persistent designation. */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val clientId: String,
    val name: String,
    val isOutput: Boolean = false,
)

@Serializable
data class PositionReport(
    val deviceId: String,
    val positionMs: Int,
    val reportedAt: Double,
)

/**
 * Canonical playback state. The server is the sole writer; the app replaces its
 * local copy on every `state_changed` and dead-reckons between updates.
 */
@Serializable
data class PlayerState(
    val revision: Int = 0,
    /** Monotonic counter bumped ONLY on deliberate position moves (play, seek,
     *  skip, loop restart, interrupt fire/advance/end). The seek contract:
     *  reposition the active lane iff this changed — NEVER infer seeks by
     *  comparing positions, which are re-stamped on every broadcast. */
    val positionEpoch: Int = 0,
    val isPlaying: Boolean = false,
    val volume: Double = 1.0,
    val activeModeId: String? = null,
    val activeOutputDeviceIds: List<String> = emptyList(),
    val deviceVolumes: Map<String, Double> = emptyMap(),
    val activeSoundboardId: String? = null,
    val activePresetIds: List<String> = emptyList(),
    val crossfadeMs: Int = 0,
    val crossfadeType: CrossfadeType = CrossfadeType.LINEAR,
    val ambient: AmbientState = AmbientState(),
    val interrupt: InterruptState? = null,
    val loopingSfx: List<LoopingSfx> = emptyList(),
    val lastPositionReport: PositionReport? = null,
    val connectedDevices: List<DeviceInfo> = emptyList(),
)
