package eu.junak.baton.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches a track's cover-art bytes (for the playback notification / media
 * session). Lives here so feature modules keep consuming plain types and
 * never grow an OkHttp dependency — same boundary as [MediaUrls].
 */
@Singleton
class CoverArtLoader @Inject constructor(
    private val mediaUrls: MediaUrls,
    private val client: OkHttpClient,
) {
    /**
     * Raw image bytes of the track's cover, or null when the server has none
     * (404), no server is configured, or the fetch fails — callers treat null
     * as "show no artwork", never as an error.
     */
    suspend fun coverBytes(trackId: Int): ByteArray? = withContext(Dispatchers.IO) {
        val url = mediaUrls.cover(trackId) ?: return@withContext null
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()
            }
        }.getOrNull()
    }
}
