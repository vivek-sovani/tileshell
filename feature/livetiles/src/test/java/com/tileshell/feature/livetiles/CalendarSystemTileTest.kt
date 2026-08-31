package com.tileshell.feature.livetiles

import com.tileshell.core.data.Paksha
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [tithiMoonFraction] — the Hindu Panchang tile's own exact tithi-to-moon-phase mapping. */
class CalendarSystemTileTest {

    @Test
    fun `shukla pratipada (day after new moon) is just past new`() {
        assertEquals(0.5 / 30.0, tithiMoonFraction(Paksha.SHUKLA, 1), 1e-9)
    }

    @Test
    fun `shukla purnima (full moon day) is near the 0-5 full-moon fraction`() {
        assertEquals(14.5 / 30.0, tithiMoonFraction(Paksha.SHUKLA, 15), 1e-9)
    }

    @Test
    fun `krishna pratipada (day after full moon) is just past full`() {
        assertEquals(15.5 / 30.0, tithiMoonFraction(Paksha.KRISHNA, 1), 1e-9)
    }

    @Test
    fun `krishna amavasya (new moon day) wraps to just under 1-0 (near new)`() {
        assertEquals(29.5 / 30.0, tithiMoonFraction(Paksha.KRISHNA, 15), 1e-9)
    }

    @Test
    fun `fraction increases monotonically through a full shukla-then-krishna cycle`() {
        val shukla = (1..15).map { tithiMoonFraction(Paksha.SHUKLA, it) }
        val krishna = (1..15).map { tithiMoonFraction(Paksha.KRISHNA, it) }
        val all = shukla + krishna
        for (i in 1 until all.size) {
            assert(all[i] > all[i - 1]) { "fraction should strictly increase across the cycle" }
        }
        assert(all.all { it in 0.0..1.0 })
    }
}
