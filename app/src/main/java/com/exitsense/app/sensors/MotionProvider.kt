package com.exitsense.app.sensors

import com.exitsense.app.domain.model.MotionType
import kotlinx.coroutines.flow.StateFlow

interface MotionProvider {
    val currentMotion: StateFlow<MotionType>
    fun startMonitoring()
    fun stopMonitoring()
}
