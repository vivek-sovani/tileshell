package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchCalibrationTest {

    @Test
    fun `with nothing learned the offset is the default four dp`() {
        val c = TouchCalibration()
        val (bx, by) = c.bias(0)
        assertEquals(0f, bx, 0.001f)
        assertEquals(4f, by, 0.001f)
        assertNull(c.summary())
    }

    @Test
    fun `the offset follows where touches land, shrunk towards the default`() {
        val c = TouchCalibration()
        repeat(40) { c.observe(2, 3f, -8f) }
        val (bx, by) = c.bias(2)
        // 40 touches at (3, -8) against 40 pseudo-touches at (0, -4): halfway.
        assertEquals(-1.5f, bx, 0.01f)
        assertEquals(6f, by, 0.01f)
        // Other zones are untouched.
        assertEquals(4f, c.bias(3).second, 0.001f)
        repeat(2000) { c.observe(2, 3f, -8f) }
        assertEquals(7.8f, c.bias(2).second, 0.3f)
    }

    @Test
    fun `the offset is limited`() {
        val c = TouchCalibration()
        repeat(500) { c.observe(0, 30f, -40f) }
        val (bx, by) = c.bias(0)
        assertEquals(-10f, bx, 0.001f)
        assertEquals(10f, by, 0.001f)
    }

    @Test
    fun `old touches fade so a changed grip is followed`() {
        val c = TouchCalibration()
        repeat(2000) { c.observe(1, 0f, -8f) }
        repeat(3000) { c.observe(1, 0f, 0f) }
        assertEquals(0f, c.bias(1).second, 0.8f)
    }

    @Test
    fun `it survives being saved and read back, and bad text is ignored`() {
        val c = TouchCalibration()
        repeat(100) { c.observe(5, 2f, -6f) }
        val d = TouchCalibration()
        d.decode(c.encode())
        assertEquals(c.bias(5).first, d.bias(5).first, 0.0001f)
        assertEquals(c.bias(5).second, d.bias(5).second, 0.0001f)
        d.decode("garbage")
        assertNull(d.summary())
        d.decode("1,2;3")
        assertNull(d.summary())
    }

    @Test
    fun `zones are left or right half by row`() {
        assertEquals(0, TouchCalibration.zoneOf(10f, 100f, 0))
        assertEquals(1, TouchCalibration.zoneOf(90f, 100f, 0))
        assertEquals(4, TouchCalibration.zoneOf(10f, 100f, 2))
        assertEquals(7, TouchCalibration.zoneOf(90f, 100f, 5))
    }

    private val a = Pt(100f, 100f)
    private val b = Pt(130f, 100f)
    private val far = Pt(400f, 100f)

    @Test
    fun `a touch counts once the next key shows it stood`() {
        val c = TouchCalibration()
        val l = TapLearner(c)
        l.onLetter(0, Pt(102f, 95f), a, 30f, 0)
        assertNull(c.summary())
        l.onLetter(0, Pt(131f, 95f), b, 30f, 100)
        assertEquals(1, c.summary()!!.touches)
        l.onOther()
        assertEquals(2, c.summary()!!.touches)
        assertEquals(-5f, c.summary()!!.meanDy, 0.01f)
    }

    @Test
    fun `a letter deleted at once is not a measure of the thumb`() {
        val c = TouchCalibration()
        val l = TapLearner(c)
        l.onLetter(0, Pt(110f, 100f), a, 30f, 0)
        l.onBackspace(300)
        l.onOther()
        assertNull(c.summary())
    }

    @Test
    fun `a slip and its neighbouring retry is firm evidence for the intended key`() {
        val c = TouchCalibration()
        val l = TapLearner(c)
        // Touch at x=116 went to a (centre 100) and was deleted; b (centre 130) was typed next:
        // the touch was meant for b, 14 dp to its left.
        l.onLetter(0, Pt(116f, 100f), a, 30f, 0)
        l.onBackspace(300)
        l.onLetter(0, Pt(130f, 100f), b, 30f, 900)
        val s = c.summary()!!
        assertEquals(3, s.touches)
        assertEquals(-14f, s.meanDx, 0.01f)
    }

    @Test
    fun `a retry on a far key or after too long is not taken as a slip`() {
        val c = TouchCalibration()
        val l = TapLearner(c)
        l.onLetter(0, Pt(116f, 100f), a, 30f, 0)
        l.onBackspace(300)
        l.onLetter(0, far, far, 30f, 900)
        assertNull(c.summary())
        l.onBackspace(1000)
        l.onLetter(0, Pt(116f, 100f), a, 30f, 1100)
        l.onBackspace(1300)
        l.onLetter(0, Pt(130f, 100f), b, 30f, 9000)
        l.onOther()
        // Only the last touch stands, at weight 1: no slip evidence was taken.
        assertEquals(1, c.summary()!!.touches)
    }

    @Test
    fun `a second backspace clears the slip`() {
        val c = TouchCalibration()
        val l = TapLearner(c)
        l.onLetter(0, Pt(116f, 100f), a, 30f, 0)
        l.onBackspace(300)
        l.onBackspace(400)
        l.onLetter(0, Pt(130f, 100f), b, 30f, 800)
        l.onOther()
        assertEquals(1, c.summary()!!.touches)
    }
}
