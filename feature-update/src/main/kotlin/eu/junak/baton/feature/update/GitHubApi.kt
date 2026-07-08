package eu.junak.baton.feature.update

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The slice of GitHub's REST API the updater needs: the latest published release of
 * the configured repo ([BuildConfig.UPDATE_REPO]). Wire fields are snake_case; the
 * shared ProtocolJson (SnakeCase + ignoreUnknownKeys) maps them onto these DTOs.
 */
interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GitHubRelease
}

@Serializable
data class GitHubRelease(
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val htmlUrl: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long = 0,
    val contentType: String? = null,
)
