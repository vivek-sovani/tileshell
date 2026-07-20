package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the quick panel's screen-timeout cycling ([nextScreenTimeoutPreset]/[screenTimeoutLabel]). */
class ScreenTimeoutTest {

    @Test
    fun `advances to the next larger preset`() {
        assertEquals(30_000L, nextScreenTimeoutPreset(15_000L))
        assertEquals(60_000L, nextScreenTimeoutPreset(30_000L))
    }

    @Test
    fun `a value between two presets advances past the next preset`() {
        assertEquals(120_000L, nextScreenTimeoutPreset(45_000L))
    }

    @Test
    fun `wraps around after the last preset`() {
        assertEquals(15_000L, nextScreenTimeoutPreset(1_800_000L))
    }

    @Test
    fun `a value past every preset wraps to the first`() {
        assertEquals(15_000L, nextScreenTimeoutPreset(9_999_999L))
    }

    @Test
    fun `labels seconds, minutes, and hours`() {
        assertEquals("15s", screenTimeoutLabel(15_000L))
        assertEquals("2m", screenTimeoutLabel(120_000L))
        assertEquals("1h", screenTimeoutLabel(3_600_000L))
    }
}
