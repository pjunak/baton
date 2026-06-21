package eu.junak.baton.core.network.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.junak.baton.core.network.security.SecureStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous local store for the network layer: the configured server URL, this
 * device's stable `client_id`, and the session cookie (encrypted at rest via
 * [SecureStore]).
 *
 * Deliberately SharedPreferences rather than DataStore: OkHttp's [okhttp3.CookieJar]
 * calls in on a network thread with a *synchronous* contract, which DataStore's
 * suspend/Flow API can't satisfy cleanly. DataStore is used at the UI layer for
 * app settings instead.
 */
@Singleton
class NetworkStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secureStore: SecureStore,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("baton_network", Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_SERVER_URL) else editor.putString(KEY_SERVER_URL, value)
            editor.apply()
        }

    /** Stable per-install id the WebSocket `register` uses. Minted once, then persisted. */
    val clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_CLIENT_ID, it).apply()
        }

    fun readSessionCookie(): String? =
        prefs.getString(KEY_SESSION_COOKIE, null)?.let(secureStore::decrypt)

    fun writeSessionCookie(value: String?) {
        val editor = prefs.edit()
        if (value == null) {
            editor.remove(KEY_SESSION_COOKIE)
        } else {
            secureStore.encrypt(value)?.let { editor.putString(KEY_SESSION_COOKIE, it) }
        }
        editor.apply()
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_SESSION_COOKIE = "session_cookie"
    }
}
