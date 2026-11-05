package com.tileshell.core.design

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class TileTiltTest {
    @Test
    fun `pressedness runs from rest to fully dipped and stays in range`() {
        assertEquals(0f, pressedness(1f, 0.95f), 1e-6f)
        assertEquals(1f, pressedness(0.95f, 0.95f), 1e-6f)
        assertEquals(0.5f, pressedness(0.975f, 0.95f), 1e-5f)
        // A spring overshoot past either end does not push it out of 0..1.
        assertEquals(0f, pressedness(1.02f, 0.95f), 1e-6f)
        assertEquals(1f, pressedness(0.9f, 0.95f), 1e-6f)
        assertEquals(0f, pressedness(0.8f, 1f), 1e-6f)
    }

    @Test
    fun `highlight sits in the middle at rest and slides away from the finger`() {
        assertEquals(Offset(0.5f, 0.5f), highlightCentre(0f, 0f))
        val pressedBottomRight = highlightCentre(1f, 1f)
        assertEquals(0.1f, pressedBottomRight.x, 1e-6f)
        assertEquals(0.1f, pressedBottomRight.y, 1e-6f)
        val pressedTopLeft = highlightCentre(-1f, -1f)
        assertEquals(0.9f, pressedTopLeft.x, 1e-6f)
        // Out-of-range input is clamped, so the highlight never leaves the tile.
        assertEquals(0.1f, highlightCentre(5f, 0f).x, 1e-6f)
    }

    @Test
    fun `parallax moves the face against the lean and is bounded`() {
        val rest = parallaxShift(0f, 0f, 6f)
        assertEquals(0f, rest.x, 1e-6f)
        assertEquals(0f, rest.y, 1e-6f)
        val corner = parallaxShift(1f, -1f, 6f)
        assertEquals(-6f, corner.x, 1e-6f)
        assertEquals(6f, corner.y, 1e-6f)
        val clamped = parallaxShift(3f, 0f, 6f)
        assertEquals(-6f, clamped.x, 1e-6f)
        assertEquals(0f, clamped.y, 1e-6f)
    }
}
