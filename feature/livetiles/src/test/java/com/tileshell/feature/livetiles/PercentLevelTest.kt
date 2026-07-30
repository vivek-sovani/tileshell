package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the quick panel's brightness/volume tap-to-step cycling ([nextPercentLevel]). */
class PercentLevelTest {

    @Test
    fun `advances to the next larger level`() {
        assertEquals(10, nextPercentLevel(0))
        assertEquals(20, nextPercentLevel(10))
        assertEquals(100, nextPercentLevel(80))
    }

    @Test
    fun `a value between two levels advances to the very next level, not past it`() {
        assertEquals(40, nextPercentLevel(25))
        assertEquals(60, nextPercentLevel(45))
    }

    @Test
    fun `wraps around after the last level`() {
        assertEquals(0, nextPercentLevel(100))
    }

    @Test
    fun `a value past every level wraps to the first`() {
        assertEquals(0, nextPercentLevel(999))
    }

    @Test
    fun `an exact level value advances to the next one, never repeats`() {
        assertEquals(20, nextPercentLevel(10))
        assertEquals(40, nextPercentLevel(20))
    }
}
