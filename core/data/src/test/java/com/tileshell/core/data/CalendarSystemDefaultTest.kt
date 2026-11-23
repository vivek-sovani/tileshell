package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSystemDefaultTest {
    @Test
    fun `a region maps to a known system, case-insensitively`() {
        assertEquals(HINDU_PANCHANG_ID, defaultCalendarSystemFor("IN"))
        assertEquals("islamic", defaultCalendarSystemFor("sa"))
        assertEquals("hebrew", defaultCalendarSystemFor("IL"))
        assertEquals("chinese", defaultCalendarSystemFor("CN"))
    }

    @Test
    fun `most regions have no default and a missing one has none`() {
        assertNull(defaultCalendarSystemFor("US"))
        assertNull(defaultCalendarSystemFor("GB"))
        assertNull(defaultCalendarSystemFor(null))
        assertNull(defaultCalendarSystemFor(""))
    }

    @Test
    fun `every default is a system the tile can show`() {
        val regions = listOf("IN", "SA", "IL", "CN", "IR", "TH", "ET", "EG", "NP", "ID")
        regions.forEach { assertTrue(it, calendarSystemFor(defaultCalendarSystemFor(it)!!) != null) }
    }
}
