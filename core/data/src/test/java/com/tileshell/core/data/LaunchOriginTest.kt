package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchOriginTest {
    @Test fun freshOriginIsTakenOnce() {
        LaunchOrigin.set(10f, 20f, 110f, 220f, now = 1_000)
        val b = LaunchOrigin.take(now = 1_500)
        assertEquals(LaunchOrigin.Bounds(10, 20, 110, 220), b)
        assertNull(LaunchOrigin.take(now = 1_501))
    }

    @Test fun staleOriginIsIgnored() {
        LaunchOrigin.set(0f, 0f, 50f, 50f, now = 1_000)
        assertNull(LaunchOrigin.take(now = 1_000 + LaunchOrigin.VALID_MS + 1))
    }

    @Test fun emptyBoundsAreIgnored() {
        LaunchOrigin.set(5f, 5f, 5f, 40f, now = 1_000)
        assertNull(LaunchOrigin.take(now = 1_100))
    }

    @Test fun pivotIsTheNearestCell() {
        // Top-left tile on a 1000 x 2000 screen, bottom-right tile, and a tile in the middle.
        assertEquals(0 to 0, LaunchOrigin.pivotCell(LaunchOrigin.Bounds(0, 0, 200, 200), 1000, 2000))
        assertEquals(3 to 2, LaunchOrigin.pivotCell(LaunchOrigin.Bounds(800, 1800, 1000, 2000), 1000, 2000))
        assertEquals(2 to 1, LaunchOrigin.pivotCell(LaunchOrigin.Bounds(400, 1000, 600, 1200), 1000, 2000))
    }
}
