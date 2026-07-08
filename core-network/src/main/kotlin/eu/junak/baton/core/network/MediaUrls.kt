package eu.junak.baton.core.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds absolute URLs for server-hosted media (cover art, audio streams) from the
 * configured [ServerConfig] base URL, respecting any reverse-proxy sub-path. Returns
 * null when no server is configured. Both endpoints are guest-accessible, so callers
 * (Coil for art, ExoPlayer for streams) need no auth header. Keeping the `HttpUrl`
 * handling here means feature modules consume plain `String` URLs and don't depend
 * on OkHttp.
 */
@Singleton
class MediaUrls @Inject constructor(
    private val serverConfig: ServerConfig,
) {
    /** Absolute URL of a track's cover art, or null if no server is configured. */
    fun cover(trackId: Int): String? = trackUrl(trackId, "cover")

    /** Absolute URL of a track's audio stream, or null if no server is configured. */
    fun stream(trackId: Int): String? = trackUrl(trackId, "stream")

    /**
     * Absolute URL of a soundboard SFX asset (`itemPath` relative to the server's SFX
     * library), or null if no server is configured. Guest-accessible, like the streams.
     */
    fun sfx(itemPath: String): String? =
        serverConfig.baseUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/sfx/file")
            ?.addQueryParameter("path", itemPath)
            ?.build()
            ?.toString()

    private fun trackUrl(trackId: Int, leaf: String): String? =
        serverConfig.baseUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/library/tracks/$trackId/$leaf")
            ?.build()
            ?.toString()
}
