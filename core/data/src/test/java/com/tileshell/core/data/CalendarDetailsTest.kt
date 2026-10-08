package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A made-up calendar: 30-day months, 12 to a year, month 0 starting on epoch day 0. */
private class FakeSource(private val leapMonth0: Int? = null) : CalendarSource {
    override fun fieldsOn(epochDay: Long): CalFields {
        val n = Math.floorMod(epochDay, 360L).toInt()
        val month0 = n / 30
        return CalFields(day = n % 30 + 1, month0 = month0, year = Math.floorDiv(epochDay, 360L).toInt() + 100, leapMonth = month0 == leapMonth0)
    }
    override fun monthLength(epochDay: Long) = 30
    override fun monthsInYear(epochDay: Long) = 12
    override fun monthName(epochDay: Long) = "Month${fieldsOn(epochDay).month0}"
}

class CalendarDetailsTest {
    @Test fun `chinese years are named by element and animal`() {
        assertEquals("wood rat", chineseYearName(1))
        assertEquals("wood snake", chineseYearName(42)) // 2025
        assertEquals("fire horse", chineseYearName(43)) // 2026
        assertEquals("water pig", chineseYearName(60))
        assertEquals("wood rat", chineseYearName(61))
    }

    @Test fun `chinese months are worded and leap months say so`() {
        assertEquals("first month", chineseMonthName(0, false))
        assertEquals("leap sixth month", chineseMonthName(5, true))
    }

    @Test fun `era labels`() {
        assertEquals("AH", eraLabelFor("islamic"))
        assertEquals("BE", eraLabelFor("buddhist"))
        assertEquals("", eraLabelFor("chinese"))
    }

    @Test fun `first weekday offset counts from monday`() {
        // 2026-10-08 is a Thursday; day 8 of a month starting 2026-10-01, a Thursday.
        val epoch = java.time.LocalDate.of(2026, 10, 8).toEpochDay()
        assertEquals(3, firstWeekdayOffset(epoch, 8))
        assertEquals(0, firstWeekdayOffset(java.time.LocalDate.of(2026, 9, 28).toEpochDay(), 1)) // a Monday
    }

    @Test fun `describing a day gives month facts`() {
        val d = describeSystemDay("islamic", FakeSource(), 44L) // day 15 of month 1
        assertEquals(15, d.day)
        assertEquals("month1", d.monthName)
        assertEquals(2, d.monthNumber)
        assertEquals(12, d.monthsInYear)
        assertEquals(15, d.daysLeftInMonth)
        assertEquals("100 AH", d.yearText)
        assertEquals(null, d.yearNote)
    }

    @Test fun `a chinese day names the year animal and drops the cycle number`() {
        val d = describeSystemDay("chinese", FakeSource(), 0L)
        assertEquals("", d.yearText)
        assertEquals("year of the water rabbit", d.yearNote) // fake year 100 -> index 39
    }

    @Test fun `upcoming specials are soonest first and today counts`() {
        val table = SPECIAL_DAYS.getValue("persian")
        // Day 0 of the fake calendar is month 0 day 1: nowruz, today.
        val up = upcomingSpecialDays("persian", FakeSource(), 0L, count = 2)
        assertEquals("nowruz", up[0].name)
        assertEquals(0, up[0].daysAway)
        assertEquals("sizdah bedar", up[1].name)
        assertEquals(12, up[1].daysAway)
        assertTrue(table.isNotEmpty())
    }

    @Test fun `no specials for a system without any, or beyond the horizon`() {
        assertTrue(upcomingSpecialDays("hindu", FakeSource(), 0L).isEmpty())
        assertTrue(upcomingSpecialDays("coptic", FakeSource(), 100L, horizonDays = 3).isEmpty())
    }

    @Test fun `chinese specials skip leap months`() {
        // Month 4 (fifth month) as a leap month: dragon boat day 5 must not match.
        val up = upcomingSpecialDays("chinese", FakeSource(leapMonth0 = 4), 120L, count = 1, horizonDays = 20)
        assertTrue(up.isEmpty())
    }

    @Test fun `days away wording`() {
        assertEquals("today", daysAwayText(0))
        assertEquals("tomorrow", daysAwayText(1))
        assertEquals("in 12 days", daysAwayText(12))
    }
}
