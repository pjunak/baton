package eu.junak.baton.core.network.api

import eu.junak.baton.core.model.Track
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Read-only library browsing. (Authoring — uploads, metadata edits, moves — is
 * intentionally out of the app; use the web UI.) All responses deserialize via
 * the shared snake_case-aware [eu.junak.baton.core.model.ProtocolJson] config.
 */
interface LibraryApi {

    /** Direct contents of one folder: subfolders + the tracks immediately in it. */
    @GET("api/library/tree")
    suspend fun tree(@Query("path") path: String = ""): TreeResponse

    /** The whole folder hierarchy in one response (powers a client-side tree). */
    @GET("api/library/folders")
    suspend fun folders(): FoldersResponse

    @GET("api/library/search")
    suspend fun search(
        @Query("q") query: String = "",
        @Query("limit") limit: Int = DEFAULT_LIMIT,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = "artist",
        @Query("order") order: String = "asc",
    ): SearchResponse

    @GET("api/library/tracks/{id}")
    suspend fun track(@Path("id") id: Int): Track

    /**
     * Batch-resolve many tracks in one round trip (the endpoint added to the
     * music backend). `ids` is comma-separated, e.g. `1,2,3`. Used to turn the
     * PlayerState queue/history id-lists into displayable tracks without N calls.
     */
    @GET("api/library/tracks")
    suspend fun tracks(@Query("ids") ids: String): List<Track>

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}

@Serializable
data class FolderOut(
    val name: String,
    val path: String,
    val trackCount: Int,
    val hasChildren: Boolean,
)

@Serializable
data class TreeResponse(
    val path: String,
    val folders: List<FolderOut> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

@Serializable
data class FoldersResponse(
    val folders: List<FolderOut> = emptyList(),
)

@Serializable
data class SearchResponse(
    val tracks: List<Track> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val sort: String = "artist",
    val order: String = "asc",
)
