package com.tileshell.core.data.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TaskRepeatTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `codes round trip`() {
        listOf(TaskRepeat.Once, TaskRepeat.Daily, TaskRepeat.Weekly, TaskRepeat.Monthly, TaskRepeat.Yearly, TaskRepeat.EveryDays(3))
            .forEach { assertEquals(it, TaskRepeat.decode(it.code)) }
    }

    @Test
    fun `bad codes read as once`() {
        assertEquals(TaskRepeat.Once, TaskRepeat.decode("fortnightly"))
        assertEquals(TaskRepeat.Once, TaskRepeat.decode("days:0"))
        assertEquals(TaskRepeat.Once, TaskRepeat.decode("days:x"))
        assertEquals(TaskRepeat.Daily, TaskRepeat.decode("days:1"))
    }

    @Test
    fun `once has no next date`() {
        assertNull(nextReminderAt(at(2026, 10, 1), TaskRepeat.Once, at(2026, 9, 30), zone))
    }

    @Test
    fun `ticking early moves one period ahead`() {
        assertEquals(at(2026, 10, 8), nextReminderAt(at(2026, 10, 1), TaskRepeat.Weekly, at(2026, 9, 30), zone))
        assertEquals(at(2026, 10, 4), nextReminderAt(at(2026, 10, 1), TaskRepeat.EveryDays(3), at(2026, 9, 30), zone))
        assertEquals(at(2027, 10, 1), nextReminderAt(at(2026, 10, 1), TaskRepeat.Yearly, at(2026, 9, 30), zone))
    }

    @Test
    fun `overdue by several periods jumps to the next future date`() {
        assertEquals(at(2026, 10, 3), nextReminderAt(at(2026, 9, 25), TaskRepeat.Daily, at(2026, 10, 2, 12), zone))
    }

    @Test
    fun `monthly clamps to the month's length`() {
        assertEquals(at(2027, 2, 28), nextReminderAt(at(2027, 1, 31), TaskRepeat.Monthly, at(2027, 1, 30), zone))
        assertEquals(at(2027, 3, 31), nextReminderAt(at(2027, 1, 31), TaskRepeat.Monthly, at(2027, 3, 1), zone))
    }

    @Test
    fun `reminder line`() {
        val now = at(2026, 9, 30, 20)
        assertEquals("tomorrow 9:00 am · weekly", reminderLine(at(2026, 10, 1), TaskRepeat.Weekly, now, zone))
        assertEquals("today 9:30 pm", reminderLine(at(2026, 9, 30, 21, 30), TaskRepeat.Once, at(2026, 9, 30, 18), zone))
        assertEquals("overdue · mon 28 sep 11:00 am", reminderLine(at(2026, 9, 28, 11), TaskRepeat.Once, now, zone))
        assertEquals("fri 1 jan 2027 12:05 am · every 3 days", reminderLine(at(2027, 1, 1, 0, 5), TaskRepeat.EveryDays(3), now, zone))
    }

    @Test
    fun `due only once past, open and not snoozed ahead`() {
        val now = at(2026, 9, 30, 10)
        assertTrue(isReminderDue(at(2026, 9, 30, 9), null, false, now))
        assertFalse(isReminderDue(at(2026, 9, 30, 11), null, false, now))
        assertFalse(isReminderDue(at(2026, 9, 30, 9), null, true, now))
        assertFalse(isReminderDue(at(2026, 9, 30, 9), at(2026, 9, 30, 10, 5), false, now))
        assertFalse(isReminderDue(null, null, false, now))
    }

    @Test
    fun `scheduled today keeps today and overdue, soonest first`() {
        val now = at(2026, 9, 30, 12)
        val times = listOf(at(2026, 9, 30, 18), at(2026, 10, 1, 9), at(2026, 9, 29, 11), at(2026, 9, 30, 23, 59))
        assertEquals(
            listOf(at(2026, 9, 29, 11), at(2026, 9, 30, 18), at(2026, 9, 30, 23, 59)),
            scheduledToday(times, { it }, now, zone),
        )
    }
}
