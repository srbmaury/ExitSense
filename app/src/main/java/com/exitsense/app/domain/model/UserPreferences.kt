package com.exitsense.app.domain.model

import com.exitsense.app.rules.DEFAULT_EXIT_THRESHOLD

data class UserPreferences(
    val homeWifiSsid: String = "",
    val homeNetworkIds: Set<Int> = emptySet(),
    val homeFloor: Int = 0,
    val isSetupComplete: Boolean = false,
    val isMonitoringEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val exitConfidenceThreshold: Float = DEFAULT_EXIT_THRESHOLD,
    val reminderSnoozeMinutes: Int = 2,
    val lastExitTimestamp: Long = 0L,
    val weatherEnabled: Boolean = false,
    val calendarEnabled: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartMinute: Int = 22 * 60,
    val quietHoursEndMinute: Int = 7 * 60
)
