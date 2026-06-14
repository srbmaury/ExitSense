package com.exitsense.app.sensors.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.exitsense.app.sensors.WifiProvider
import com.exitsense.app.sensors.WifiState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
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
    private val refCount = AtomicInteger(0)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            // Don't call getNetworkCapabilities() here — on API 29+ it returns a redacted
            // WifiInfo (SSID = <unknown ssid>) outside a callback parameter context.
            // onCapabilitiesChanged fires immediately after and owns the SSID update.
            _wifiState.update { it.copy(isConnected = true, justDisconnected = false) }
        }

        override fun onLost(network: Network) {
            _wifiState.update { WifiState(isConnected = false, ssid = null, networkId = -1, justDisconnected = true) }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
            // caps is passed directly by the framework — WifiInfo is NOT redacted here.
            // getNetworkId() works without location; getSsid() requires it on API 29+.
            val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                caps.transportInfo as? WifiInfo else null
            val ssid = getSsid(caps)
            val networkId = wifiInfo?.networkId ?: -1
            _wifiState.update { it.copy(isConnected = true, ssid = ssid, networkId = networkId, justDisconnected = false) }
        }
    }

    override fun startMonitoring() {
        if (refCount.getAndIncrement() > 0) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        _wifiState.value = WifiState(
            isConnected = isCurrentlyConnectedToWifi(),
            ssid = getCurrentSsid(),
            justDisconnected = false
        )
        runCatching {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    override fun stopMonitoring() {
        if (refCount.decrementAndGet() > 0) return
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    override fun refresh() {
        // Synchronous getNetworkCapabilities() returns a redacted WifiInfo on API 29+.
        // The only reliable way to get the real SSID is inside a NetworkCallback parameter.
        // Register a one-shot callback: onCapabilitiesChanged fires immediately for the
        // currently connected WiFi and provides an unredacted WifiInfo.
        if (!isCurrentlyConnectedToWifi()) {
            _wifiState.update { it.copy(isConnected = false, ssid = null) }
            return
        }
        _wifiState.update { it.copy(isConnected = true) }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val handler = Handler(Looper.getMainLooper())
        val oneShot = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
                handler.removeCallbacksAndMessages(null)
                val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    caps.transportInfo as? WifiInfo else null
                val networkId = wifiInfo?.networkId ?: -1
                _wifiState.update { it.copy(ssid = getSsid(caps), networkId = networkId) }
                runCatching { connectivityManager.unregisterNetworkCallback(this) }
            }
            override fun onLost(network: Network) {
                handler.removeCallbacksAndMessages(null)
                _wifiState.update { WifiState(isConnected = false, ssid = null, networkId = -1) }
                runCatching { connectivityManager.unregisterNetworkCallback(this) }
            }
        }
        runCatching { connectivityManager.registerNetworkCallback(request, oneShot) }
        // Safety net: if the callback never fires (Doze, odd device state), unregister after 3 s
        // to avoid leaking one of the OS's limited NetworkCallback slots.
        handler.postDelayed({
            runCatching { connectivityManager.unregisterNetworkCallback(oneShot) }
        }, 3_000L)
    }

    // On API 29+, read SSID from NetworkCapabilities (requires ACCESS_FINE_LOCATION).
    // Falls back to the deprecated WifiManager API for older devices.
    @Suppress("DEPRECATION")
    private fun getSsid(caps: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = caps.transportInfo as? WifiInfo
            val raw = wifiInfo?.ssid
            if (!raw.isNullOrBlank() && raw != WifiManager.UNKNOWN_SSID && raw != "<unknown ssid>") {
                return raw.removeSurrounding("\"")
            }
        }
        return getCurrentSsid()
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSsid(): String? {
        return try {
            val raw = wifiManager.connectionInfo?.ssid
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
