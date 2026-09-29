package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Checked against NASA's eclipse catalogue (times UT). */
class EclipsesTest {
    private val pune = 18.52 to 73.86
    private val varanasi = 25.32 to 83.00

    private fun utc(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(y, m - 1, d, h, min)
    }.timeInMillis

    private fun inYear(y: Int, place: Pair<Double, Double> = pune) = Eclipses.between(utc(y, 1, 1), utc(y + 1, 1, 1), place.first, place.second)

    private fun near(e: Eclipse, expected: Long, minutes: Int = 20) =
        assertTrue("off by ${(e.maxMillis - expected) / 60000} min", kotlin.math.abs(e.maxMillis - expected) < minutes * 60_000L)

    @Test fun eclipsesOf2026() {
        val list = inYear(2026)
        // Annular 17 Feb, total lunar 3 Mar, total solar 12 Aug, partial lunar 28 Aug.
        assertEquals(4, list.size)
        near(list[0], utc(2026, 2, 17, 12, 12)); assertTrue(list[0].solar); assertEquals(Eclipse.Kind.ANNULAR, list[0].kind)
        near(list[1], utc(2026, 3, 3, 11, 33)); assertFalse(list[1].solar); assertEquals(Eclipse.Kind.TOTAL, list[1].kind)
        near(list[2], utc(2026, 8, 12, 17, 46)); assertTrue(list[2].solar); assertEquals(Eclipse.Kind.TOTAL, list[2].kind)
        near(list[3], utc(2026, 8, 28, 4, 12)); assertEquals(Eclipse.Kind.PARTIAL, list[3].kind)
        assertFalse("annular over antarctica", list[0].visible)
        assertFalse("12 aug solar is over spain", list[2].visible)
        assertFalse("28 aug lunar is after moonset in india", list[3].visible)
    }

    @Test fun lunarOf2025SeptemberSeenInIndia() {
        val e = inYear(2025).first { !it.solar && it.maxMillis > utc(2025, 9, 1) }
        near(e, utc(2025, 9, 7, 18, 11))
        assertEquals(Eclipse.Kind.TOTAL, e.localKind)
    }

    @Test fun solarOf2009TotalInVaranasiPartialInPune() {
        val v = inYear(2009, varanasi).first { it.solar && it.maxMillis > utc(2009, 7, 1) && it.maxMillis < utc(2009, 8, 1) }
        assertEquals(Eclipse.Kind.TOTAL, v.localKind)
        val p = inYear(2009, pune).first { it.maxMillis == v.maxMillis }
        assertEquals(Eclipse.Kind.PARTIAL, p.localKind)
    }

    @Test fun partialSolarOf2022SeenInPune() {
        val e = inYear(2022).first { it.solar && it.maxMillis > utc(2022, 10, 1) }
        assertTrue(e.visible)
        assertEquals(Eclipse.Kind.PARTIAL, e.localKind)
    }

    @Test fun march2026LunarRisesAfterTotalityInPune() {
        val e = inYear(2026).first { !it.solar && it.kind == Eclipse.Kind.TOTAL }
        assertTrue(e.visible)
        assertEquals(Eclipse.Kind.PARTIAL, e.localKind)
    }
}
