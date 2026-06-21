package eu.junak.baton.core.network

import eu.junak.baton.core.network.data.NetworkStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The configured server base URL — reactive, so the UI can branch on
 * "connected vs needs setup" and the [eu.junak.baton.core.network.interceptor.BaseUrlInterceptor]
 * can rewrite requests. Backed by [NetworkStore]; seeded synchronously from disk
 * at construction.
 */
@Singleton
class ServerConfig @Inject constructor(
    private val store: NetworkStore,
) {
    private val _baseUrl = MutableStateFlow(store.serverUrl?.toHttpUrlOrNull())
    val baseUrl: StateFlow<HttpUrl?> = _baseUrl.asStateFlow()

    val isConfigured: Boolean get() = _baseUrl.value != null

    fun baseUrlOrNull(): HttpUrl? = _baseUrl.value

    fun setBaseUrl(url: HttpUrl) {
        store.serverUrl = url.toString()
        _baseUrl.value = url
    }

    fun forget() {
        store.serverUrl = null
        _baseUrl.value = null
    }
}
