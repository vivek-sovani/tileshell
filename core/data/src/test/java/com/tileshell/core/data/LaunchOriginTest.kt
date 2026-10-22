package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchOriginTest {
    @Test fun freshOriginIsTakenOnce() {
        LaunchOrigin.set(10f, 20f, 110f, 220f, color = 0xFF112233.toInt(), cornerPx = 6f, now = 1_000)
        val o = LaunchOrigin.take(now = 1_500)
        assertEquals(LaunchOrigin.Bounds(10, 20, 110, 220), o?.bounds)
        assertEquals(0xFF112233.toInt(), o?.color)
        assertEquals(6f, o?.cornerPx)
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

    @Test fun launchedListenerHearsOnlyRealOrigins() {
        var heard: LaunchOrigin.Launched? = null
        LaunchOrigin.onLaunched = { heard = it }
        LaunchOrigin.notifyLaunched(null, "a.b")
        assertNull(heard)
        LaunchOrigin.set(0f, 0f, 10f, 10f, now = 1_000)
        LaunchOrigin.notifyLaunched(LaunchOrigin.take(now = 1_001), "a.b")
        assertEquals("a.b", heard?.packageName)
        LaunchOrigin.onLaunched = null
    }
}
