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
}
