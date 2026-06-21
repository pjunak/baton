package eu.junak.baton.core.network.cookie

import eu.junak.baton.core.network.data.NetworkStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the single `music_session` cookie (encrypted, via [NetworkStore]) and
 * replays it on every request — to REST *and* the WebSocket, since both share
 * this OkHttpClient. That shared cookie is exactly how the backend authenticates
 * the `/api/ws` upgrade, so login "just works" across both transports.
 *
 * Only the session cookie is tracked (the sole cookie the backend sets); the
 * persisted form is just its value.
 */
@Singleton
class SessionCookieJar @Inject constructor(
    private val store: NetworkStore,
) : CookieJar {

    @Volatile
    private var cached: Cookie? = null

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val session = cookies.lastOrNull { it.name == SESSION_COOKIE } ?: return
        cached = session
        store.writeSessionCookie(session.value)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookie = cached ?: restore(url)?.also { cached = it } ?: return emptyList()
        return listOf(cookie)
    }

    /** Drop the session (logout / auth failure). */
    fun clear() {
        cached = null
        store.writeSessionCookie(null)
    }

    private fun restore(url: HttpUrl): Cookie? {
        val value = store.readSessionCookie() ?: return null
        return Cookie.Builder()
            .name(SESSION_COOKIE)
            .value(value)
            .domain(url.host)
            .path("/")
            .secure()
            .httpOnly()
            .build()
    }

    private companion object {
        const val SESSION_COOKIE = "music_session"
    }
}
