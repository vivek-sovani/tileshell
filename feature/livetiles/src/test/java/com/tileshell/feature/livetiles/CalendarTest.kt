package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarFormatTest {

    @Test
    fun `time line is start plus compact duration`() {
        assertEquals("10:00 · 30m", eventTimeLine(10, 0, 30))
        assertEquals("9:00 · 1h", eventTimeLine(9, 0, 60))
        assertEquals("14:30 · 1h 30m", eventTimeLine(14, 30, 90))
    }

    @Test
    fun `minutes are zero-padded but hours are not`() {
        assertEquals("9:05 · 5m", eventTimeLine(9, 5, 5))
    }

    @Test
    fun `non-positive or all-day durations drop the duration`() {
        assertEquals("8:00", eventTimeLine(8, 0, 0))
        assertEquals("8:00", eventTimeLine(8, 0, -10))
        assertEquals("0:00", eventTimeLine(0, 0, 24 * 60))
    }

    @Test
    fun `calendar event derives duration from begin and end`() {
        // 30-minute event; only the duration is asserted (start time is TZ-local).
        val event = calendarEvent("Standup", beginMillis = 0L, endMillis = 30 * 60_000L)
        assertEquals("Standup", event.title)
        assertEquals(true, event.timeLine.endsWith("· 30m"))
    }

    @Test
    fun `blank titles become untitled`() {
        val event = calendarEvent("  ", beginMillis = 0L, endMillis = 60_000L)
        assertEquals("(untitled)", event.title)
    }

    @Test
    fun `today formats lowercase weekday and month with the day number`() {
        // Sunday (Calendar.DAY_OF_WEEK = 1), 14 June (month0 = 5).
        val today = calendarToday(dayOfWeek = 1, dayOfMonth = 14, month0 = 5)
        assertEquals("sunday", today.weekday)
        assertEquals(14, today.day)
        assertEquals("june", today.month)
    }

    @Test
    fun `today maps the last weekday and month`() {
        // Saturday (7), 31 December (month0 = 11).
        val today = calendarToday(dayOfWeek = 7, dayOfMonth = 31, month0 = 11)
        assertEquals("saturday", today.weekday)
        assertEquals("december", today.month)
    }
}
