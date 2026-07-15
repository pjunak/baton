package eu.junak.baton.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the device has a VALIDATED default network — i.e. one the OS
 * has confirmed reaches the internet, which is the point where DNS actually
 * works. At app open / Doze wake the network stack often lags the UI by a
 * moment; anything that fires a request in that window gets an
 * "Unable to resolve host" failure that would have succeeded a second later.
 * Consumers gate on [online] instead of attempting into that window, and react
 * to its rising edge to (re)connect the instant connectivity is really there.
 *
 * App-lifetime singleton; the callback is deliberately never unregistered.
 */
@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(currentlyValidated())

    /** True while the current default network is validated (internet-reachable). */
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /** The network the callback currently tracks. On a handover (Wi-Fi -> cell)
     *  the new network's onCapabilitiesChanged can arrive BEFORE the old one's
     *  onLost — only the tracked network's loss may flip us offline. */
    @Volatile private var trackedNetwork: Network? = null

    init {
        // Fires immediately with the current default network on registration,
        // correcting the constructor snapshot if it raced the stack coming up.
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trackedNetwork = network
                _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                if (network == trackedNetwork) _online.value = false
            }
        })
    }

    private fun currentlyValidated(): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
