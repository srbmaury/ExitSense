package com.exitsense.app.domain.model

data class UserPreferences(
    val homeWifiSsid: String = "",
    val homeFloor: Int = 0,
    val isSetupComplete: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val exitConfidenceThreshold: Float = 70f,
    val reminderSnoozeMinutes: Int = 2,
    val lastExitTimestamp: Long = 0L
)
