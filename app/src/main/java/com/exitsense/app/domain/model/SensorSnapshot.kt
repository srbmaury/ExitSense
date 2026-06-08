package com.exitsense.app.domain.model

data class SensorSnapshot(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val wifiConnected: Boolean = false,
    val connectedSsid: String? = null,
    val motionType: MotionType = MotionType.STILL,
    val screenState: ScreenState = ScreenState.OFF,
    val pressure: Float? = null,
    val confidenceScore: Float = 0f
)
