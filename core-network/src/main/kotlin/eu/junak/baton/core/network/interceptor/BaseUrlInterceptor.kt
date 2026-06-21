package eu.junak.baton.core.network.interceptor

import eu.junak.baton.core.network.ServerConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit is built with a placeholder base URL; this swaps in the server the
 * user configured at runtime, so no connection address is ever compiled in.
 * Preserves the configured base path (sub-path reverse-proxy mounts) plus the
 * request's own path and query.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val serverConfig: ServerConfig,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = serverConfig.baseUrlOrNull() ?: return chain.proceed(request)

        // request.url is like https://baton.invalid/api/auth/login — keep the
        // path + query, graft them onto the configured base.
        val tail = request.url.encodedPath.removePrefix("/")
        val query = request.url.encodedQuery
        val rebuilt = buildString {
            append(base.toString().trimEnd('/'))
            append('/')
            append(tail)
            if (query != null) {
                append('?')
                append(query)
            }
        }.toHttpUrl()

        return chain.proceed(request.newBuilder().url(rebuilt).build())
    }
}
