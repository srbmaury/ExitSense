package com.exitsense.app.sensors

import kotlinx.coroutines.flow.StateFlow

data class WifiState(
    val isConnected: Boolean = false,
    val ssid: String? = null,
    val networkId: Int = -1,
    val justDisconnected: Boolean = false
)

interface WifiProvider {
    val wifiState: StateFlow<WifiState>
    /** SSIDs found by the most recent scan; empty until [triggerScan] is called. */
    val scanResults: StateFlow<List<String>>
    fun startMonitoring()
    fun stopMonitoring()
    fun refresh()
    /** Initiates a WiFi scan and updates [scanResults] when complete. */
    fun triggerScan()
}
