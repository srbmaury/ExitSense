package com.exitsense.app.sensors

import kotlinx.coroutines.flow.StateFlow

data class WifiState(
    val isConnected: Boolean = false,
    val ssid: String? = null,
    val justDisconnected: Boolean = false
)

interface WifiProvider {
    val wifiState: StateFlow<WifiState>
    fun startMonitoring()
    fun stopMonitoring()
}
