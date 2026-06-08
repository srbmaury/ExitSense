package com.exitsense.app.sensors

import kotlinx.coroutines.flow.StateFlow

data class PressureData(
    val currentPressure: Float? = null,
    val baselinePressure: Float? = null,
    val isDescending: Boolean = false,
    val isAvailable: Boolean = false
)

interface PressureProvider {
    val pressureData: StateFlow<PressureData>
    fun startMonitoring()
    fun stopMonitoring()
    fun calibrateBaseline()
}
