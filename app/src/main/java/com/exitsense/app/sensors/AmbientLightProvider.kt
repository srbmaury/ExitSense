package com.exitsense.app.sensors

import kotlinx.coroutines.flow.StateFlow

data class LightData(
    val luxLevel: Float? = null,
    val isOutdoor: Boolean = false,
    val isAvailable: Boolean = false
)

interface AmbientLightProvider {
    val lightData: StateFlow<LightData>
    fun startMonitoring()
    fun stopMonitoring()
}
