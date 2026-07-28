package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the single-finger edge-swipe-down recognizer. */
class EdgeSwipeGestureTest {

    @Test
    fun `touch at the very left edge is the left zone`() {
        assertEquals(EdgeZone.LEFT, edgeZoneFor(startX = 0f, screenWidthPx = 1000f))
    }

    @Test
    fun `touch anywhere in the left half is the left zone`() {
        assertEquals(EdgeZone.LEFT, edgeZoneFor(startX = 400f, screenWidthPx = 1000f))
    }

    @Test
    fun `touch just left of the midpoint is still the left zone`() {
        assertEquals(EdgeZone.LEFT, edgeZoneFor(startX = 499f, screenWidthPx = 1000f))
    }

    @Test
    fun `touch at the very right edge is the right zone`() {
        assertEquals(EdgeZone.RIGHT, edgeZoneFor(startX = 1000f, screenWidthPx = 1000f))
    }

    @Test
    fun `touch anywhere in the right half is the right zone`() {
        assertEquals(EdgeZone.RIGHT, edgeZoneFor(startX = 600f, screenWidthPx = 1000f))
    }

    @Test
    fun `touch exactly at the midpoint is the right zone`() {
        assertEquals(EdgeZone.RIGHT, edgeZoneFor(startX = 500f, screenWidthPx = 1000f))
    }

    @Test
    fun `zero screen width is neither zone`() {
        assertEquals(EdgeZone.NONE, edgeZoneFor(startX = 0f, screenWidthPx = 0f))
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
    fun `upward travel never triggers`() {
        assertFalse(isEdgeSwipeDown(dy = -60f, dx = 0f, thresholdPx = 40f))
    }
}
