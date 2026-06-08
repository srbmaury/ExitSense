package com.exitsense.app.sensors.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import com.exitsense.app.sensors.WifiProvider
import com.exitsense.app.sensors.WifiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WifiProvider {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _wifiState = MutableStateFlow(WifiState())
    override val wifiState: StateFlow<WifiState> = _wifiState

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
            val ssid = getCurrentSsid()
            _wifiState.update { WifiState(isConnected = true, ssid = ssid, justDisconnected = false) }
        }

        override fun onLost(network: Network) {
            val caps = connectivityManager.getNetworkCapabilities(network)
            // Only react to Wi-Fi loss; caps may be null when network is already gone
            if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                _wifiState.update { prev ->
                    WifiState(isConnected = false, ssid = prev.ssid, justDisconnected = true)
                }
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
            val ssid = getCurrentSsid()
            _wifiState.update { it.copy(ssid = ssid, justDisconnected = false) }
        }
    }

    override fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
        // Set initial state
        _wifiState.value = WifiState(
            isConnected = isCurrentlyConnectedToWifi(),
            ssid = getCurrentSsid(),
            justDisconnected = false
        )
    }

    override fun stopMonitoring() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSsid(): String? {
        return try {
            val info = wifiManager.connectionInfo
            val raw = info?.ssid
            if (raw.isNullOrBlank() || raw == "<unknown ssid>") null
            else raw.removeSurrounding("\"")
        } catch (_: Exception) {
            null
        }
    }

    private fun isCurrentlyConnectedToWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
