package com.exitsense.app.rules

import com.exitsense.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class TimeRuleEvaluatorTest {

    private fun calendar(dayOfWeek: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }

    private fun profile(
        scheduleType: ScheduleType,
        activeDays: Set<Int> = emptySet(),
        startH: Int = 8, startM: Int = 0,
        endH: Int = 10, endM: Int = 0
    ) = ReminderProfile(
        id = 1L, name = "Test",
        scheduleType = scheduleType,
        activeDays = activeDays,
        startTimeHour = startH, startTimeMinute = startM,
        endTimeHour = endH, endTimeMinute = endM
    )

    @Test
    fun `weekday schedule matches monday morning`() {
        val now = calendar(Calendar.MONDAY, 9, 0)
        assertTrue(TimeRuleEvaluator.isWithinSchedule(profile(ScheduleType.WEEKDAYS), now))
    }

    @Test
    fun `weekday schedule does not match saturday`() {
        val now = calendar(Calendar.SATURDAY, 9, 0)
        assertFalse(TimeRuleEvaluator.isWithinSchedule(profile(ScheduleType.WEEKDAYS), now))
    }

    @Test
    fun `weekend schedule matches saturday`() {
        val now = calendar(Calendar.SATURDAY, 10, 0)
        assertTrue(TimeRuleEvaluator.isWithinSchedule(profile(ScheduleType.WEEKENDS), now))
    }

    @Test
    fun `all days schedule matches any day`() {
        listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.SATURDAY, Calendar.SUNDAY)
            .forEach { day ->
                val now = calendar(day, 9, 0)
                assertTrue(TimeRuleEvaluator.isWithinSchedule(profile(ScheduleType.ALL_DAYS), now))
            }
    }

    @Test
    fun `custom schedule matches only specified days`() {
        val p = profile(ScheduleType.CUSTOM, activeDays = setOf(1, 3, 5)) // Mon, Wed, Fri
        assertTrue(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.MONDAY, 9, 0)))
        assertFalse(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.TUESDAY, 9, 0)))
        assertTrue(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.WEDNESDAY, 9, 0)))
    }

    @Test
    fun `time before window returns false`() {
        val now = calendar(Calendar.MONDAY, 7, 30)
        assertFalse(TimeRuleEvaluator.isWithinSchedule(profile(ScheduleType.WEEKDAYS), now))
    }

    @Test
    fun `time after window returns false`() {
        val now = calendar(Calendar.MONDAY, 10, 30)
        assertFalse(TimeRuleEvaluator.isWithinSchedule(profile(ScheduleType.WEEKDAYS), now))
    }

    @Test
    fun `time at exact start boundary returns true`() {
        val now = calendar(Calendar.MONDAY, 8, 0)
        assertTrue(TimeRuleEvaluator.isWithinSchedule(profile(ScheduleType.WEEKDAYS), now))
    }

    @Test
    fun `overnight window handled correctly`() {
        // Window 22:00–02:00 (overnight)
        val p = profile(ScheduleType.ALL_DAYS, startH = 22, startM = 0, endH = 2, endM = 0)
        assertTrue(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.MONDAY, 23, 0)))
        assertTrue(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.MONDAY, 1, 0)))
        assertFalse(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.MONDAY, 12, 0)))
    }

    @Test
    fun `overnight weekday schedule after midnight belongs to previous day`() {
        val p = profile(ScheduleType.WEEKDAYS, startH = 22, endH = 2)
        assertTrue(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.FRIDAY, 23, 0)))
        assertTrue(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.SATURDAY, 1, 0)))
        assertFalse(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.MONDAY, 1, 0)))
        assertFalse(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.MONDAY, 12, 0)))
    }

    @Test
    fun `overnight custom schedule wraps from sunday to monday`() {
        val p = profile(ScheduleType.CUSTOM, activeDays = setOf(7), startH = 22, endH = 2)
        assertTrue(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.MONDAY, 1, 30)))
        assertFalse(TimeRuleEvaluator.isWithinSchedule(p, calendar(Calendar.SUNDAY, 1, 30)))
    }

    // ── Reminder windows ─────────────────────────────────────────────────────

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }

    @Test
    fun `reminder from yesterday does not block an earlier departure today`() {
        val yesterday = at(2026, Calendar.SEPTEMBER, 16, 8, 30).timeInMillis
        val p = profile(ScheduleType.ALL_DAYS).copy(lastNotifiedAt = yesterday)
        // 23h45m later — the old 24h cooldown would have suppressed this
        assertFalse(TimeRuleEvaluator.wasNotifiedInCurrentWindow(p, at(2026, Calendar.SEPTEMBER, 17, 8, 15)))
    }

    @Test
    fun `second departure in the same window is suppressed`() {
        val earlier = at(2026, Calendar.SEPTEMBER, 17, 8, 5).timeInMillis
        val p = profile(ScheduleType.ALL_DAYS).copy(lastNotifiedAt = earlier)
        assertTrue(TimeRuleEvaluator.wasNotifiedInCurrentWindow(p, at(2026, Calendar.SEPTEMBER, 17, 9, 40)))
    }

    @Test
    fun `overnight window after midnight starts on the previous day`() {
        val p = profile(ScheduleType.ALL_DAYS, startH = 22, endH = 2)
        val start = TimeRuleEvaluator.currentWindowStart(p, at(2026, Calendar.SEPTEMBER, 17, 1, 0))
        assertEquals(at(2026, Calendar.SEPTEMBER, 16, 22, 0).timeInMillis, start)
        val notifiedBeforeMidnight = p.copy(lastNotifiedAt = at(2026, Calendar.SEPTEMBER, 16, 23, 0).timeInMillis)
        assertTrue(TimeRuleEvaluator.wasNotifiedInCurrentWindow(notifiedBeforeMidnight, at(2026, Calendar.SEPTEMBER, 17, 1, 0)))
    }

    @Test
    fun `outside the schedule there is no current window`() {
        assertNull(TimeRuleEvaluator.currentWindowStart(profile(ScheduleType.ALL_DAYS), at(2026, Calendar.SEPTEMBER, 17, 12, 0)))
    }

    @Test
    fun `schedule counts as starting soon within the lead time`() {
        val p = profile(ScheduleType.ALL_DAYS) // 08:00–10:00
        assertTrue(TimeRuleEvaluator.isActiveOrStartingSoon(p, 15, at(2026, Calendar.SEPTEMBER, 17, 7, 50)))
        assertFalse(TimeRuleEvaluator.isActiveOrStartingSoon(p, 15, at(2026, Calendar.SEPTEMBER, 17, 7, 30)))
        assertTrue(TimeRuleEvaluator.isActiveOrStartingSoon(p, 15, at(2026, Calendar.SEPTEMBER, 17, 9, 0)))
        assertFalse(TimeRuleEvaluator.isActiveOrStartingSoon(p, 15, at(2026, Calendar.SEPTEMBER, 17, 10, 30)))
    }
}
