package com.nathanb.lock.service

import com.nathanb.lock.data.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ScheduleManagerTest {

    private val utc = TimeZone.getTimeZone("UTC")

    /** Build an epoch-millis instant in UTC for a given date + time. */
    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun `no days selected returns null`() {
        val now = instant(2024, 1, 1, 8, 0) // Monday
        assertNull(ScheduleManager.nextTriggerAtMillis(9 * 60, 0, now, utc))
    }

    @Test
    fun `later today on an enabled day is chosen`() {
        // Monday 2024-01-01 08:00, target 09:00 every day -> same day 09:00
        val now = instant(2024, 1, 1, 8, 0)
        val expected = instant(2024, 1, 1, 9, 0)
        assertEquals(expected, ScheduleManager.nextTriggerAtMillis(9 * 60, Schedule.ALL_DAYS, now, utc))
    }

    @Test
    fun `time already passed today rolls to tomorrow`() {
        // Monday 10:00, target 09:00 every day -> Tuesday 09:00
        val now = instant(2024, 1, 1, 10, 0)
        val expected = instant(2024, 1, 2, 9, 0)
        assertEquals(expected, ScheduleManager.nextTriggerAtMillis(9 * 60, Schedule.ALL_DAYS, now, utc))
    }

    @Test
    fun `weekday-only mask skips the weekend`() {
        // Friday 2024-01-05 20:00, weekdays only (Mon..Fri), target 09:00 -> Monday 2024-01-08 09:00
        val weekdays = 0b0011111
        val now = instant(2024, 1, 5, 20, 0)
        val expected = instant(2024, 1, 8, 9, 0)
        assertEquals(expected, ScheduleManager.nextTriggerAtMillis(9 * 60, weekdays, now, utc))
    }

    @Test
    fun `single-day mask past the time wraps to next week`() {
        // Only Monday selected (bit 0). It's Monday 2024-01-01 10:00, target 09:00 already passed.
        // Next Monday is 2024-01-08 09:00.
        val mondayOnly = 0b0000001
        val now = instant(2024, 1, 1, 10, 0)
        val expected = instant(2024, 1, 8, 9, 0)
        assertEquals(expected, ScheduleManager.nextTriggerAtMillis(9 * 60, mondayOnly, now, utc))
    }

    @Test
    fun `sunday bit is honored`() {
        // Sunday is bit 6. Saturday 2024-01-06 12:00, Sunday-only target 08:00 -> 2024-01-07 08:00
        val sundayOnly = 0b1000000
        val now = instant(2024, 1, 6, 12, 0)
        val expected = instant(2024, 1, 7, 8, 0)
        assertEquals(expected, ScheduleManager.nextTriggerAtMillis(8 * 60, sundayOnly, now, utc))
    }

    @Test
    fun `result is always strictly in the future`() {
        val now = instant(2024, 1, 1, 9, 0) // exactly the target time
        val next = ScheduleManager.nextTriggerAtMillis(9 * 60, Schedule.ALL_DAYS, now, utc)!!
        assertTrue(next > now)
    }
}
