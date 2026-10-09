package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DragEdgeLogicTest {
    @Test
    fun `edge direction follows the finger, not the tile`() {
        assertEquals(-1, edgeDirectionOf(5f, 400f, 20f))
        assertEquals(1, edgeDirectionOf(395f, 400f, 20f))
        // A finger on a last-column tile (the tile ends near the edge) is not at the edge.
        assertEquals(0, edgeDirectionOf(360f, 400f, 20f))
        assertEquals(0, edgeDirectionOf(200f, 400f, 20f))
    }

    @Test
    fun `a page is carried only after the finger has rested at the edge`() {
        assertFalse(carriesToNextPage(1, sinceMs = 1_000, nowMs = 1_100, dwellMs = 350))
        assertTrue(carriesToNextPage(1, sinceMs = 1_000, nowMs = 1_350, dwellMs = 350))
        assertFalse(carriesToNextPage(0, sinceMs = 0, nowMs = 10_000, dwellMs = 350))
    }

    @Test
    fun `corner zones shrink on a small tile and stay full on a big one`() {
        // A 90dp small tile: 27% of 90 = 24.3 (under 30).
        assertEquals(24.3f, cornerZoneSize(90f, 90f, 30f), 0.01f)
        // A wide tile: its shorter side is large enough for the full zone.
        assertEquals(30f, cornerZoneSize(190f, 90f * 2, 30f), 0.01f)
        // The shorter side decides.
        assertEquals(24.3f, cornerZoneSize(400f, 90f, 30f), 0.01f)
    }
}
