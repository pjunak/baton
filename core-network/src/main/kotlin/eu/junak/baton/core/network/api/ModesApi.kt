package eu.junak.baton.core.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Read-only access to the server's ambient *modes* (the scenes the operator
 * switches between) and their soundboards. Listing is REST; *activating* a mode
 * and *firing* SFX go over the WebSocket as [eu.junak.baton.core.model.Action]s.
 * Responses deserialize via the snake_case-aware ProtocolJson (unknown keys —
 * cues, presets, interrupts, … — are ignored, so these DTOs stay minimal).
 */
interface ModesApi {

    /** All configured modes (id + display name + a couple of defaults). */
    @GET("api/modes")
    suspend fun list(): List<ModeSummary>

    /** Full detail for one mode, including its soundboards (categories → items). */
    @GET("api/modes/{id}")
    suspend fun detail(@Path("id") id: String): ModeDetail
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
