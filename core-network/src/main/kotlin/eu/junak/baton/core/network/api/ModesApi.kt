package eu.junak.baton.core.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Read-only access to the server's ambient *modes* (the scenes the operator
 * switches between) and everything authored inside them — soundboards, cues,
 * interrupt templates, EQ presets. Listing is REST; *activating* a mode,
 * *firing* SFX/cues/interrupts and *toggling* presets go over the WebSocket as
 * [eu.junak.baton.core.model.Action]s. Responses deserialize via the
 * snake_case-aware ProtocolJson (unknown keys are ignored, so these DTOs carry
 * only what the app renders).
 */
interface ModesApi {

    /** All configured modes (id + display name + a couple of defaults). */
    @GET("api/modes")
    suspend fun list(): List<ModeSummary>

    /** Full detail for one mode: soundboards, cues, interrupt templates. */
    @GET("api/modes/{id}")
    suspend fun detail(@Path("id") id: String): ModeDetail

    /** The mode's EQ presets (everything authored is per-mode). */
    @GET("api/modes/{id}/presets")
    suspend fun presets(@Path("id") id: String): List<PresetSummary>
}

@Serializable
data class ModeSummary(
    val id: String,
    val name: String,
    val defaultSoundboard: String? = null,
)

@Serializable
data class ModeDetail(
    val id: String,
    val name: String,
    val defaultSoundboard: String? = null,
    val soundboards: Map<String, Soundboard> = emptyMap(),
    val cues: Map<String, Cue> = emptyMap(),
    val interrupts: List<InterruptTemplate> = emptyList(),
)

/** A saved one-click setup (preset + playlist-from-timestamp + SFX + loops).
 *  The app only needs identity — `fire_cue` resolves everything server-side. */
@Serializable
data class Cue(
    val id: String,
    val name: String,
    val description: String? = null,
)

/** A pre-configured interrupt the operator can fire: either a playlist (by
 *  name, resolved against the mode's playlists) or a one-shot soundboard item
 *  (fired on the mode's default soundboard). */
@Serializable
data class InterruptTemplate(
    val name: String,
    val playlist: String? = null,
    val soundboardItem: String? = null,
    val fadeInMs: Int = 0,
    val fadeOutMs: Int = 0,
    val returnToAmbient: Boolean = true,
    val duckTo: Double? = null,
)

/** An EQ preset (id + label; the effect graph itself only matters to outputs
 *  with an effect chain — the web client — not to this remote). */
@Serializable
data class PresetSummary(
    val id: String,
    val name: String,
    val description: String? = null,
)

@Serializable
data class Soundboard(
    val id: String,
    val name: String? = null,
    val categories: List<SoundboardCategory> = emptyList(),
)

@Serializable
data class SoundboardCategory(
    val id: String,
    val name: String,
    val items: List<SoundboardItem> = emptyList(),
)

/** One playable sound. `file` is the `item_path` echoed back in `fire_sfx`. */
@Serializable
data class SoundboardItem(
    val file: String,
    val name: String,
    val icon: String? = null,
    val hotkey: String? = null,
)
