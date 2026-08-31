package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CalendarSystemCatalogTest {

    @Test
    fun `every system has a unique, non-blank id and display name`() {
        val ids = CALENDAR_SYSTEMS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        CALENDAR_SYSTEMS.forEach {
            assertTrue(it.id.isNotBlank())
            assertTrue(it.displayName.isNotBlank())
        }
    }

    @Test
    fun `only the hindu panchang system has no icu calendar keyword`() {
        CALENDAR_SYSTEMS.forEach {
            if (it.id == HINDU_PANCHANG_ID) {
                assertNull(it.icuCalendarKeyword)
            } else {
                assertTrue("${it.id} needs an icu keyword", !it.icuCalendarKeyword.isNullOrBlank())
            }
        }
    }

    @Test
    fun `calendarSystemFor finds a real system and returns null for an unknown id`() {
        assertEquals("Hindu (Panchang)", calendarSystemFor(HINDU_PANCHANG_ID)?.displayName)
        assertNull(calendarSystemFor("no-such-system"))
    }
}

class CalendarSystemTileCodecTest {

    @Test
    fun `round-trips a picked system`() {
        val encoded = CalendarSystemTile.encode("islamic")
        assertEquals("islamic", CalendarSystemTile.decode(encoded))
    }

    @Test
    fun `decode rejects a string with no calsys prefix`() {
        assertNull(CalendarSystemTile.decode("islamic"))
    }

    @Test
    fun `decode rejects a not-yet-picked or unknown system id`() {
        assertNull(CalendarSystemTile.decode("calsys:"))
        assertNull(CalendarSystemTile.decode("calsys:no-such-system"))
    }
}

class FormatRomanDateTest {

    @Test
    fun `renders the weekday and month as lowercase text, not numbers`() {
        // 2026-08-30 00:00 UTC.
        val text = formatRomanDate(1788048000000L, TimeZone.getTimeZone("UTC"))
        assertEquals("sunday, 30 august 2026", text)
    }
}
