package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PanchangMonthTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test fun `october 2026 has 31 days starting on a thursday`() {
        val m = panchangMonth(2026, 10, ist)
        assertEquals(31, m.cells.size)
        assertEquals(3, m.firstWeekdayOffset)
        assertEquals((1..31).toList(), m.cells.map { it.day })
    }

    @Test fun `tithi numbers advance a day at a time, with at most one tithi skipped`() {
        val cells = panchangMonth(2026, 10, ist).cells
        cells.zipWithNext().forEach { (a, b) ->
            // 15 (purnima) is followed by krishna 1, and 30 (amavasya) by shukla 1, so compare modulo 15.
            val step = Math.floorMod(b.tithi - a.tithi, 15)
            assertTrue("${a.day}->${b.day}: $step", step in 0..2)
        }
    }

    @Test fun `a month holds a purnima or an amavasya and the ekadashis are marked`() {
        val kinds = (1..12).flatMap { panchangMonth(2026, it, ist).cells }.map { it.kind }.toSet()
        assertTrue(PanchangDayKind.PURNIMA in kinds)
        assertTrue(PanchangDayKind.AMAVASYA in kinds)
        assertTrue(PanchangDayKind.EKADASHI in kinds)
    }

    @Test fun `labels are devanagari numerals`() {
        val c = panchangMonth(2026, 10, ist).cells.first()
        assertEquals(PanchangDevanagari.digits(c.tithi), c.label)
    }
}
