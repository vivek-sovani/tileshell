package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyMetricsTest {

    @Test
    fun `the default choice is the canvas's measurements`() {
        val m = KeyMetrics.of(KeySize.NORMAL, KeySpacing.NORMAL, landscape = false, sixRows = false)
        assertEquals(50f, m.keyHeight, 0.001f)
        assertEquals(6f, m.keyGap, 0f)
        assertEquals(8f, m.rowGap, 0f)
        assertEquals(4f, m.side, 0f)
        // 4 rows: 2 above, 4 keys, 3 gaps, 12 below.
        assertEquals(2f + 200f + 24f + 12f, m.areaHeight(4), 0.001f)
    }

    @Test
    fun `size scales the key height and spacing sets the gaps`() {
        val m = KeyMetrics.of(KeySize.LARGE, KeySpacing.TIGHT, landscape = false, sixRows = false)
        assertEquals(56f, m.keyHeight, 0.001f)
        assertEquals(2f, m.keyGap, 0f)
        assertEquals(3f, m.rowGap, 0f)
    }

    @Test
    fun `landscape keeps the keyboard from taking the screen`() {
        val big = KeyMetrics.of(KeySize.HUGE, KeySpacing.NORMAL, landscape = true, sixRows = false)
        assertEquals(44f, big.keyHeight, 0.001f)
        assertEquals(36f, KeyMetrics.of(KeySize.SMALL, KeySpacing.NORMAL, landscape = true, sixRows = false).keyHeight, 0.001f)
    }

    @Test
    fun `six Devanagari rows are held to a height that fits`() {
        assertEquals(48f, KeyMetrics.of(KeySize.NORMAL, KeySpacing.NORMAL, false, true).keyHeight, 0.001f)
        assertEquals(56f, KeyMetrics.of(KeySize.HUGE, KeySpacing.NORMAL, false, true).keyHeight, 0.001f)
        val tallest = KeyMetrics.of(KeySize.HUGE, KeySpacing.WIDE, false, true).areaHeight(6)
        assertTrue("six rows $tallest dp", tallest < 420f)
        assertEquals(39.6f, KeyMetrics.of(KeySize.HUGE, KeySpacing.NORMAL, true, true).keyHeight, 0.001f)
    }

    @Test
    fun `every choice has sensible gaps`() {
        for (s in KeySpacing.entries) assertTrue(s.keyGap in 1f..12f && s.rowGap in 1f..14f && s.side in 0f..8f)
        for (k in KeySize.entries) assertTrue(k.scale in 0.8f..1.3f)
    }
}
