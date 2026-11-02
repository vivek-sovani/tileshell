package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TapReplayTest {

    private val layout = TapLayout(mapOf("a" to Pt(100f, 100f), "b" to Pt(200f, 100f), "c" to Pt(300f, 100f)), 100f)

    private fun tap(hit: String, x: Float = 0f, y: Float = 0f) = TapRecord(x, y, hit, layout)

    @Test
    fun `clean typing has no wrong taps`() {
        val s = TapReplay.analyse(listOf(tap("a", 100f, 100f), tap("b", 200f, 100f)), "ab")
        assertEquals(2, s.taps)
        assertEquals(0, s.wrong)
        assertEquals(0f, s.meanDx, 0.0001f)
    }

    @Test
    fun `a slip and its retry count as one wrong tap`() {
        val taps = listOf(tap("a", 100f, 100f), tap("c", 290f, 100f), tap(TapLog.BACKSPACE), tap("b", 200f, 100f))
        val s = TapReplay.analyse(taps, "ab")
        assertEquals(3, s.taps)
        assertEquals(1, s.wrong)
        assertEquals(1, s.backspaces)
        assertEquals(listOf(("b" to "c") to 1), s.confusions)
    }

    @Test
    fun `offsets are in key widths from the intended key's centre`() {
        // Meant "a" (100, 100), landed 20 right and 10 low; meant "b", landed 20 right, 30 low.
        val s = TapReplay.analyse(listOf(tap("a", 120f, 110f), tap("b", 220f, 130f)), "ab")
        assertEquals(0.2f, s.meanDx, 0.0001f)
        assertEquals(0.2f, s.meanDy, 0.0001f)
        assertEquals(0f, s.sigmaX, 0.0001f)
        assertEquals(0.1414f, s.sigmaY, 0.001f)
    }

    @Test
    fun `case does not matter and taps past the end are ignored`() {
        val s = TapReplay.analyse(listOf(tap("A", 100f, 100f), tap("b", 200f, 100f)), "a")
        assertEquals(1, s.taps)
        assertEquals(0, s.wrong)
    }
}
