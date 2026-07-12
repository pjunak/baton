package eu.junak.baton.core.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Read-only playlist metadata. The Session tab needs it to resolve interrupt
 * templates (which reference playlists **by name**, per the mode YAML) into the
 * `playlist_id` the `fire_interrupt_playlist` action takes — and to badge
 * templates whose reference no longer resolves instead of firing nothing.
 */
interface PlaylistsApi {

    /** Playlists scoped to one mode (playlists are strictly per-mode). */
    @GET("api/playlists")
    suspend fun list(@Query("mode_id") modeId: String): List<PlaylistMeta>
}

@Serializable
data class PlaylistMeta(
    val id: Int,
    val name: String,
    val modeId: String? = null,
)
