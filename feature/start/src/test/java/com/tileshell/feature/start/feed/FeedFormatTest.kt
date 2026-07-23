package com.tileshell.feature.start.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/** Unit tests for the pure feed helpers (date, pager-commit, search-url). */
class FeedFormatTest {

    @Test
    fun `glance date is a single uppercase line with no year`() {
        // 16 June 2026 is a Tuesday.
        val cal: Calendar = GregorianCalendar(2026, Calendar.JUNE, 16)
        val glance = feedGlanceDate(cal)
        assertEquals("TUESDAY · 16 JUNE", glance.dateLine)
    }

    @Test
    fun `clock formats 12-hour with lowercase suffix`() {
        assertEquals("9:05 am", feedClock12(GregorianCalendar(2026, Calendar.JUNE, 16, 9, 5)))
        assertEquals("12:00 pm", feedClock12(GregorianCalendar(2026, Calendar.JUNE, 16, 12, 0)))
        assertEquals("12:30 am", feedClock12(GregorianCalendar(2026, Calendar.JUNE, 16, 0, 30)))
        assertEquals("11:59 pm", feedClock12(GregorianCalendar(2026, Calendar.JUNE, 16, 23, 59)))
    }

    @Test
    fun `glance clock omits the am_pm suffix`() {
        assertEquals("9:05", feedGlanceClock(GregorianCalendar(2026, Calendar.JUNE, 16, 9, 5)))
        assertEquals("12:00", feedGlanceClock(GregorianCalendar(2026, Calendar.JUNE, 16, 12, 0)))
        assertEquals("12:30", feedGlanceClock(GregorianCalendar(2026, Calendar.JUNE, 16, 0, 30)))
        assertEquals("11:59", feedGlanceClock(GregorianCalendar(2026, Calendar.JUNE, 16, 23, 59)))
    }

    @Test
    fun `commit advances one page past the 0_28 threshold`() {
        assertEquals(1f, pagerCommitTarget(base = 0f, pos = 0.4f), 0f)   // toward apps
        assertEquals(-1f, pagerCommitTarget(base = 0f, pos = -0.4f), 0f) // toward feed
    }

    @Test
    fun `commit springs back below the threshold`() {
        assertEquals(0f, pagerCommitTarget(base = 0f, pos = 0.2f), 0f)
        assertEquals(0f, pagerCommitTarget(base = 0f, pos = -0.27f), 0f)
    }

    @Test
    fun `commit from a side page returns to start`() {
        assertEquals(0f, pagerCommitTarget(base = -1f, pos = -0.6f), 0f) // feed → start
        assertEquals(0f, pagerCommitTarget(base = 1f, pos = 0.5f), 0f)   // apps → start
    }

    @Test
    fun `commit clamps to the valid page range`() {
        assertEquals(1f, pagerCommitTarget(base = 1f, pos = 1.5f), 0f)
        assertEquals(-1f, pagerCommitTarget(base = -1f, pos = -1.5f), 0f)
    }

    @Test
    fun `greeting buckets by hour with boundaries`() {
        assertEquals("good night", greetingFor(0))
        assertEquals("good night", greetingFor(4))
        assertEquals("good morning", greetingFor(5))
        assertEquals("good morning", greetingFor(11))
        assertEquals("good afternoon", greetingFor(12))
        assertEquals("good afternoon", greetingFor(16))
        assertEquals("good evening", greetingFor(17))
        assertEquals("good evening", greetingFor(20))
        assertEquals("good night", greetingFor(21))
        assertEquals("good night", greetingFor(23))
    }

    @Test
    fun `search url encodes and trims the query`() {
        assertEquals(
            "https://www.google.com/search?q=hello+world",
            googleSearchUrl("  hello world  "),
        )
        assertEquals(
            "https://www.google.com/search?q=caf%C3%A9+%26+co",
            googleSearchUrl("café & co"),
        )
    }
}
