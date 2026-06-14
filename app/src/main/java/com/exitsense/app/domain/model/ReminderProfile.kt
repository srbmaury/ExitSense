package com.exitsense.app.domain.model

data class ReminderProfile(
    val id: Long = 0,
    val name: String,
    val isActive: Boolean = true,
    val scheduleType: ScheduleType = ScheduleType.WEEKDAYS,
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5), // Monday=1 … Sunday=7
    val startTimeHour: Int = 8,
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 10,
    val endTimeMinute: Int = 0,
    val items: List<ReminderItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastNotifiedAt: Long = 0L
) {
    val startTimeFormatted: String
        get() = "%02d:%02d".format(startTimeHour, startTimeMinute)

    val endTimeFormatted: String
        get() = "%02d:%02d".format(endTimeHour, endTimeMinute)

    /** Enabled items sorted by effective priority — used for notification display. */
    fun notifiableItems(): List<ReminderItem> =
        items.filter { it.isEnabled }.sortedByDescending { it.effectivePriority }
}
