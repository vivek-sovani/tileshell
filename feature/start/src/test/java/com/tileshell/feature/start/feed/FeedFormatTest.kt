package com.tileshell.feature.start.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/** Unit tests for the pure feed helpers (date, pager-commit, search-url). */
class FeedFormatTest {

    @Test
    fun `glance date formats weekday and compact sub`() {
        // 16 June 2026 is a Tuesday.
        val cal: Calendar = GregorianCalendar(2026, Calendar.JUNE, 16)
        val glance = feedGlanceDate(cal)
        assertEquals("Tuesday", glance.weekday)
        assertEquals("16 Jun 2026", glance.sub)
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
