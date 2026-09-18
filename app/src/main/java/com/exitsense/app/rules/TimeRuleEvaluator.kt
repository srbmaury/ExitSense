package com.exitsense.app.rules

import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.model.ScheduleType
import java.util.Calendar

/**
 * Determines whether the current wall-clock time falls within a profile's active schedule.
 */
object TimeRuleEvaluator {

    fun isWithinSchedule(profile: ReminderProfile, now: Calendar = Calendar.getInstance()): Boolean {
        val isoDay = toIsoDay(now.get(Calendar.DAY_OF_WEEK)) // convert to 1=Mon…7=Sun
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = profile.startTimeHour * 60 + profile.startTimeMinute
        val endMinutes = profile.endTimeHour * 60 + profile.endTimeMinute

        if (startMinutes <= endMinutes) {
            return currentMinutes in startMinutes..endMinutes && dayMatches(profile, isoDay)
        }

        // Overnight window (e.g. 22:00–02:00): the part after midnight belongs to the previous day's schedule
        return when {
            currentMinutes >= startMinutes -> dayMatches(profile, isoDay)
            currentMinutes <= endMinutes -> dayMatches(profile, if (isoDay == 1) 7 else isoDay - 1)
            else -> false
        }
    }

    /**
     * Start (epoch ms) of the schedule window that [now] falls in, or null when outside the
     * schedule. For the after-midnight part of an overnight window this is the previous day.
     */
    fun currentWindowStart(profile: ReminderProfile, now: Calendar = Calendar.getInstance()): Long? {
        if (!isWithinSchedule(profile, now)) return null
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = profile.startTimeHour * 60 + profile.startTimeMinute
        val endMinutes = profile.endTimeHour * 60 + profile.endTimeMinute
        val start = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, profile.startTimeHour)
            set(Calendar.MINUTE, profile.startTimeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (startMinutes > endMinutes && currentMinutes < startMinutes) add(Calendar.DAY_OF_YEAR, -1)
        }
        return start.timeInMillis
    }

    /** True when a reminder was already shown during the schedule window [now] falls in. */
    fun wasNotifiedInCurrentWindow(profile: ReminderProfile, now: Calendar = Calendar.getInstance()): Boolean {
        val windowStart = currentWindowStart(profile, now) ?: return false
        return profile.lastNotifiedAt >= windowStart
    }

    /** True when the schedule is active now or starts within [leadMinutes]. */
    fun isActiveOrStartingSoon(
        profile: ReminderProfile,
        leadMinutes: Int,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        val soon = (now.clone() as Calendar).apply { add(Calendar.MINUTE, leadMinutes) }
        return isWithinSchedule(profile, now) || isWithinSchedule(profile, soon)
    }

    private fun dayMatches(profile: ReminderProfile, isoDay: Int): Boolean =
        when (profile.scheduleType) {
            ScheduleType.WEEKDAYS -> isoDay in 1..5
            ScheduleType.WEEKENDS -> isoDay in 6..7
            ScheduleType.ALL_DAYS -> true
            ScheduleType.CUSTOM -> isoDay in profile.activeDays
        }

    /** Java Calendar uses 1=Sun, 2=Mon … 7=Sat. ISO 8601 uses 1=Mon … 7=Sun. */
    private fun toIsoDay(javaDayOfWeek: Int): Int =
        if (javaDayOfWeek == Calendar.SUNDAY) 7 else javaDayOfWeek - 1
}
