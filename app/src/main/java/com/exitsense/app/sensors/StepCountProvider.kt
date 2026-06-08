package com.exitsense.app.sensors

import kotlinx.coroutines.flow.StateFlow

data class StepData(
    val stepsLastMinute: Int = 0,
    val isAvailable: Boolean = false
)

interface StepCountProvider {
    val stepData: StateFlow<StepData>
    fun startMonitoring()
    fun stopMonitoring()
}
