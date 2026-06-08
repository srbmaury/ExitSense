package com.exitsense.app.sensors

import kotlinx.coroutines.flow.StateFlow

data class ChargerData(
    val lastUnpluggedAt: Long = 0L,
    val isAvailable: Boolean = true
)

interface ChargerStateProvider {
    val chargerData: StateFlow<ChargerData>
    fun startMonitoring()
    fun stopMonitoring()
}
