package eu.junak.baton.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.junak.baton.core.model.ProtocolJson
import eu.junak.baton.core.network.cookie.SessionCookieJar
import eu.junak.baton.core.network.interceptor.BaseUrlInterceptor
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Provides the ONE OkHttpClient (with the persistent cookie jar + runtime
 * base-URL rewriting) and the Retrofit instance built on it. API service
 * interfaces are provided separately (ApiModule) so this stays focused on the
 * shared transport.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WS_PING_INTERVAL_SECONDS = 20L

    @Provides
    @Singleton
    fun provideJson(): Json = ProtocolJson

    @Provides
    @Singleton
    fun provideCookieJar(jar: SessionCookieJar): CookieJar = jar

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: CookieJar,
        baseUrlInterceptor: BaseUrlInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC: request/response lines only — never headers or bodies, so
            // the session cookie is not written to logs.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(logging)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(WS_PING_INTERVAL_SECONDS, TimeUnit.SECONDS) // keep the WS alive
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            // Placeholder host; BaseUrlInterceptor rewrites every request to the
            // server the user configured at runtime.
            .baseUrl("https://baton.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
