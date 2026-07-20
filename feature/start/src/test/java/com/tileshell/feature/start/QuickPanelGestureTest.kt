package com.tileshell.feature.start

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the two-finger swipe-up recognizer ([isQuickPanelSwipe]). */
class QuickPanelGestureTest {

    @Test
    fun `past threshold and mostly vertical upward triggers`() {
        assertTrue(isQuickPanelSwipe(avgDy = -60f, avgDx = 5f, thresholdPx = 40f))
    }

    @Test
    fun `short of threshold does not trigger`() {
        assertFalse(isQuickPanelSwipe(avgDy = -20f, avgDx = 0f, thresholdPx = 40f))
    }

    @Test
    fun `mostly horizontal travel does not trigger even past threshold`() {
        assertFalse(isQuickPanelSwipe(avgDy = -45f, avgDx = 80f, thresholdPx = 40f))
    }

    @Test
    fun `downward travel never triggers`() {
        assertFalse(isQuickPanelSwipe(avgDy = 60f, avgDx = 0f, thresholdPx = 40f))
    }
}
