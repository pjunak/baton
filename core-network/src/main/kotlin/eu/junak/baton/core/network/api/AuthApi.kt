package eu.junak.baton.core.network.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Auth endpoints. `login` sets the `music_session` cookie (captured by the
 * shared cookie jar, which then authenticates every later REST call AND the
 * WebSocket). Paths are prefixed with `api/`; the BaseUrlInterceptor grafts them
 * onto the configured server.
 */
interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): UserInfo

    @GET("api/auth/me")
    suspend fun me(): UserInfo

    @POST("api/auth/logout")
    suspend fun logout()
}

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class UserInfo(val id: Int, val username: String)
