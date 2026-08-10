package com.trevio.android.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors device network connectivity using [ConnectivityManager] and a [android.net.NetworkCallback].
 *
 * Exposes [isOnline] as a reactive [StateFlow] so the UI can show an offline banner
 * and surface friendly error messages when the device loses connectivity.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentConnection())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentConnection()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    /**
     * Start observing connectivity changes. Call once from [android.app.Application.onCreate].
     */
    fun startMonitoring() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, networkCallback)
    }

    /**
     * Stop observing connectivity changes and unregister the network callback.
     * Call from [android.app.Application.onTerminate] or during testing cleanup
     * to prevent callback leaks.
     */
    fun stopMonitoring() {
        val cm = connectivityManager ?: return
        try {
            cm.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Callback may not be registered — ignore.
        }
    }

    /**
     * Snapshot check of whether the device currently has a validated internet connection.
     */
    private fun checkCurrentConnection(): Boolean {
        val cm = connectivityManager ?: return true // Fail open if service unavailable
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
