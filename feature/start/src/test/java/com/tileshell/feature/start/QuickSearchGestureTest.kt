package com.tileshell.feature.start

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the two-finger swipe-down recognizer ([isQuickSearchSwipe]). */
class QuickSearchGestureTest {

    @Test
    fun `past threshold and mostly vertical triggers`() {
        assertTrue(isQuickSearchSwipe(avgDy = 60f, avgDx = 5f, thresholdPx = 40f))
    }

    @Test
    fun `short of threshold does not trigger`() {
        assertFalse(isQuickSearchSwipe(avgDy = 20f, avgDx = 0f, thresholdPx = 40f))
    }

    @Test
    fun `mostly horizontal travel does not trigger even past threshold`() {
        assertFalse(isQuickSearchSwipe(avgDy = 45f, avgDx = 80f, thresholdPx = 40f))
    }

    @Test
    fun `upward travel never triggers`() {
        assertFalse(isQuickSearchSwipe(avgDy = -60f, avgDx = 0f, thresholdPx = 40f))
    }
}
