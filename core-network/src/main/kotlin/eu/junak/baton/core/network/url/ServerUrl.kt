package eu.junak.baton.core.network.url

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Outcome of validating user-entered server input. */
sealed interface UrlValidation {
    data class Valid(val baseUrl: HttpUrl) : UrlValidation

    data class Invalid(val reason: String) : UrlValidation
}

/**
 * Turns what the user types in the setup wizard into a clean base URL, enforcing
 * the HTTPS-only policy, and builds API / WebSocket URLs under it. No connection
 * address is ever compiled in — this only operates on runtime input.
 */
object ServerUrl {

    fun normalize(input: String): UrlValidation {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return UrlValidation.Invalid("Enter a server address.")

        // Default to https when the user omits the scheme.
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"

        if (!withScheme.startsWith("https://", ignoreCase = true)) {
            return UrlValidation.Invalid(
                "Baton requires HTTPS. Put your server behind a reverse proxy " +
                    "with a valid certificate.",
            )
        }

        val parsed = withScheme.toHttpUrlOrNull()
            ?: return UrlValidation.Invalid("That doesn't look like a valid address.")

        // Drop any query/fragment; keep the path so sub-path mounts survive.
        val base = parsed.newBuilder().query(null).fragment(null).build()
        return UrlValidation.Valid(base)
    }

    /** API endpoint under the base: `<base>/api/<segments…>` (sub-path aware). */
    fun apiUrl(base: HttpUrl, vararg segments: String): HttpUrl {
        val prefix = base.toString().trimEnd('/')
        val tail = segments.joinToString("/")
        return "$prefix/api/$tail".toHttpUrl()
    }

    /**
     * WebSocket endpoint. OkHttp upgrades an https request to wss internally, so
     * this returns the https form of `<base>/api/ws` — pass it straight to
     * `OkHttpClient.newWebSocket`.
     */
    fun wsUrl(base: HttpUrl): HttpUrl = apiUrl(base, "ws")
}
