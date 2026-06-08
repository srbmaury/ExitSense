package com.exitsense.app.rules

import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.model.ScheduleType
import java.util.Calendar

/**
 * Determines whether the current wall-clock time falls within a profile's active schedule.
 */
object TimeRuleEvaluator {

    fun isWithinSchedule(profile: ReminderProfile, now: Calendar = Calendar.getInstance()): Boolean {
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK) // 1=Sun…7=Sat in Java Calendar
        val isoDay = toIsoDay(dayOfWeek)              // convert to 1=Mon…7=Sun
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)

        val dayMatches = when (profile.scheduleType) {
            ScheduleType.WEEKDAYS -> isoDay in 1..5
            ScheduleType.WEEKENDS -> isoDay in 6..7
            ScheduleType.ALL_DAYS -> true
            ScheduleType.CUSTOM -> isoDay in profile.activeDays
        }
        if (!dayMatches) return false

        val currentMinutes = hour * 60 + minute
        val startMinutes = profile.startTimeHour * 60 + profile.startTimeMinute
        val endMinutes = profile.endTimeHour * 60 + profile.endTimeMinute

        // Handle overnight windows (e.g. 22:00–02:00) gracefully
        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    /** Java Calendar uses 1=Sun, 2=Mon … 7=Sat. ISO 8601 uses 1=Mon … 7=Sun. */
    private fun toIsoDay(javaDayOfWeek: Int): Int =
        if (javaDayOfWeek == Calendar.SUNDAY) 7 else javaDayOfWeek - 1
}
