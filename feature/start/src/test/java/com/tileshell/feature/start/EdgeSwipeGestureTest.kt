package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the single-finger edge-swipe-down recognizer. */
class EdgeSwipeGestureTest {

    @Test
    fun `touch at the very left edge is the left zone`() {
        assertEquals(EdgeZone.LEFT, edgeZoneFor(startX = 0f, screenWidthPx = 1000f, zonePx = 48f))
    }

    @Test
    fun `touch just inside the left zone width is still the left zone`() {
        assertEquals(EdgeZone.LEFT, edgeZoneFor(startX = 48f, screenWidthPx = 1000f, zonePx = 48f))
    }

    @Test
    fun `touch at the very right edge is the right zone`() {
        assertEquals(EdgeZone.RIGHT, edgeZoneFor(startX = 1000f, screenWidthPx = 1000f, zonePx = 48f))
    }

    @Test
    fun `touch just inside the right zone width is still the right zone`() {
        assertEquals(EdgeZone.RIGHT, edgeZoneFor(startX = 952f, screenWidthPx = 1000f, zonePx = 48f))
    }

    @Test
    fun `touch in the middle of the screen is neither zone`() {
        assertEquals(EdgeZone.NONE, edgeZoneFor(startX = 500f, screenWidthPx = 1000f, zonePx = 48f))
    }

    @Test
    fun `past threshold and mostly vertical triggers`() {
        assertTrue(isEdgeSwipeDown(dy = 60f, dx = 5f, thresholdPx = 40f))
    }

    @Test
    fun `short of threshold does not trigger`() {
        assertFalse(isEdgeSwipeDown(dy = 20f, dx = 0f, thresholdPx = 40f))
    }

    @Test
    fun `mostly horizontal travel does not trigger even past threshold`() {
        assertFalse(isEdgeSwipeDown(dy = 45f, dx = 80f, thresholdPx = 40f))
    }

    @Test
    fun `upward travel never triggers isEdgeSwipeDown`() {
        assertFalse(isEdgeSwipeDown(dy = -60f, dx = 0f, thresholdPx = 40f))
    }

    @Test
    fun `isEdgeSwipeUp triggers past threshold and mostly vertical`() {
        assertTrue(isEdgeSwipeUp(dy = -60f, dx = 5f, thresholdPx = 40f))
    }

    @Test
    fun `isEdgeSwipeUp short of threshold does not trigger`() {
        assertFalse(isEdgeSwipeUp(dy = -20f, dx = 0f, thresholdPx = 40f))
    }

    @Test
    fun `isEdgeSwipeUp mostly horizontal travel does not trigger`() {
        assertFalse(isEdgeSwipeUp(dy = -45f, dx = 80f, thresholdPx = 40f))
    }

    @Test
    fun `downward travel never triggers isEdgeSwipeUp`() {
        assertFalse(isEdgeSwipeUp(dy = 60f, dx = 0f, thresholdPx = 40f))
    }
}
