package eu.junak.baton.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.junak.baton.core.network.api.AuthApi
import eu.junak.baton.core.network.api.LibraryApi
import eu.junak.baton.core.network.api.ModesApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/** Retrofit service instances + the bare client used for the pre-login probe. */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    private const val PROBE_TIMEOUT_SECONDS = 10L

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideLibraryApi(retrofit: Retrofit): LibraryApi = retrofit.create(LibraryApi::class.java)

    @Provides
    @Singleton
    fun provideModesApi(retrofit: Retrofit): ModesApi = retrofit.create(ModesApi::class.java)

    /**
     * Bare client for the setup-time `/api/health` probe: no cookie jar and no
     * base-URL rewriting, because the probe hits the exact URL the user just
     * typed (before any server is configured).
     */
    @Provides
    @Singleton
    @Named("probe")
    fun provideProbeClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}
