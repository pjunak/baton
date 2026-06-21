package eu.junak.baton.core.network.auth

import eu.junak.baton.core.network.api.AuthApi
import eu.junak.baton.core.network.api.LoginRequest
import eu.junak.baton.core.network.api.UserInfo
import eu.junak.baton.core.network.cookie.SessionCookieJar
import eu.junak.baton.core.network.data.NetworkStore
import eu.junak.baton.core.network.url.ServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Result of the pre-login reachability probe. */
sealed interface ProbeResult {
    data object Reachable : ProbeResult

    data class Unreachable(val message: String) : ProbeResult
}

/** Result of a sign-in attempt. */
sealed interface LoginResult {
    data class Success(val user: UserInfo) : LoginResult

    data object InvalidCredentials : LoginResult

    data class Error(val message: String) : LoginResult
}

/**
 * Auth + connection operations. Login relies on the configured server (set via
 * [eu.junak.baton.core.network.ServerConfig] before calling), and the session
 * cookie it sets is persisted by [SessionCookieJar]. The app never stores the
 * password — only that revocable session token.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val cookieJar: SessionCookieJar,
    private val networkStore: NetworkStore,
    @param:Named("probe") private val probeClient: OkHttpClient,
) {
    /** This device's stable id for the WebSocket `register`. */
    val clientId: String get() = networkStore.clientId

    /** Pre-login reachability + TLS check against a user-entered base URL. */
    suspend fun probe(baseUrl: HttpUrl): ProbeResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(ServerUrl.apiUrl(baseUrl, "health")).get().build()
        try {
            probeClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ProbeResult.Reachable
                } else {
                    ProbeResult.Unreachable("Server responded with HTTP ${response.code}.")
                }
            }
        } catch (e: IOException) {
            ProbeResult.Unreachable(e.message ?: "Could not reach the server.")
        }
    }

    suspend fun login(username: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                LoginResult.Success(authApi.login(LoginRequest(username, password)))
            } catch (e: HttpException) {
                if (e.code() == HTTP_UNAUTHORIZED) {
                    LoginResult.InvalidCredentials
                } else {
                    LoginResult.Error("Sign-in failed (HTTP ${e.code()}).")
                }
            } catch (e: IOException) {
                LoginResult.Error(e.message ?: "Network error during sign-in.")
            }
        }

    /** The signed-in user, or null if the session is absent/expired/unreachable. */
    suspend fun currentUser(): UserInfo? = withContext(Dispatchers.IO) {
        try {
            authApi.me()
        } catch (_: HttpException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        try {
            authApi.logout()
        } catch (_: Exception) {
            // Best-effort server-side logout; local state is cleared regardless.
        } finally {
            cookieJar.clear()
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
