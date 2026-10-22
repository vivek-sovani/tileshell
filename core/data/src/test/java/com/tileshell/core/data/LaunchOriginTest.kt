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
}
