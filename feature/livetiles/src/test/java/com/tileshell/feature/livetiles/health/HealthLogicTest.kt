package com.tileshell.feature.livetiles.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HealthLogicTest {

    @Test
    fun `a day only grows, old days are dropped, history stays in order`() {
        var h = recordDay(emptyList(), 10, 500)
        h = recordDay(h, 10, 300)
        assertEquals(500, stepsOn(h, 10))
        h = recordDay(h, 10, 900)
        assertEquals(900, stepsOn(h, 10))
        h = recordDay(h, 9, 100)
        assertEquals(listOf(9L, 10L), h.map { it.epochDay })
        val many = (1L..130L).fold(emptyList<DaySteps>()) { acc, d -> recordDay(acc, d, 1) }
        assertEquals(KEEP_DAYS, many.size)
        assertEquals(130L, many.last().epochDay)
    }

    @Test
    fun `estimates from steps`() {
        assertEquals(0.7055, strideMeters(170), 1e-3)
        assertEquals(4.7, distanceKm(6_700, 170), 0.05)
        assertEquals(252, caloriesKcal(6_312))
        assertEquals(63, activeMinutes(6_312))
        assertEquals(0.5f, goalProgress(4_000, 8_000), 1e-6f)
        assertEquals(1f, goalProgress(12_000, 8_000), 1e-6f)
        assertEquals(0f, goalProgress(100, 0), 1e-6f)
    }

    @Test
    fun `the streak counts days at goal back from today, or from yesterday`() {
        val h = listOf(DaySteps(5, 9_000), DaySteps(6, 8_000), DaySteps(7, 8_500), DaySteps(8, 2_000))
        // Today (8) is short, so it counts from yesterday: 7, 6, 5.
        assertEquals(3, streak(h, 8, 8_000))
        // Reaching it today adds it.
        assertEquals(4, streak(recordDay(h, 8, 8_000), 8, 8_000))
        assertEquals(0, streak(h, 20, 8_000))
        assertEquals(0, streak(h, 8, 0))
    }

    @Test
    fun `a week starts on monday and has stats for the days so far`() {
        val monday = LocalDate.of(2026, 10, 5).toEpochDay()
        assertEquals(monday, weekStart(monday))
        assertEquals(monday, weekStart(monday + 6))
        val h = listOf(DaySteps(monday, 7_000), DaySteps(monday + 1, 10_000), DaySteps(monday + 2, 8_000))
        val w = weekStats(h, monday, monday + 2, 8_000)
        assertEquals(listOf(7_000, 10_000, 8_000, null, null, null, null), w.days)
        assertEquals(25_000, w.total)
        assertEquals(8_333, w.average)
        assertEquals(1, w.bestIndex)
        assertEquals(2, w.daysAtGoal)
        val empty = weekStats(emptyList(), monday, monday - 1, 8_000)
        assertNull(empty.bestIndex)
        assertEquals(0, empty.average)
    }
}
