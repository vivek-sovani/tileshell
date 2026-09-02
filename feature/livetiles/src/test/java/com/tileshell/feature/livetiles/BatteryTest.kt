package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryFaceTest {

    @Test
    fun `no percent means no data`() {
        val face = batteryFace(percent = null, isCharging = false, chargeTimeRemainingMillis = null)
        assertEquals(BatteryFace(hasData = false, percentText = "--", statusLine = "unavailable"), face)
    }

    @Test
    fun `full battery always reads fully charged`() {
        val face = batteryFace(percent = 100, isCharging = true, chargeTimeRemainingMillis = 5_000L)
        assertEquals("fully charged", face.statusLine)
    }

    @Test
    fun `charging with an estimate shows time to full, not time left`() {
        // "left" alone reads as ambiguous while charging (user-reported) —
        // could be misread as remaining battery life instead of time to 100%.
        val face = batteryFace(percent = 62, isCharging = true, chargeTimeRemainingMillis = (60 + 42) * 60_000L)
        assertEquals("62%", face.percentText)
        assertEquals("1h 42m to full", face.statusLine)
    }

    @Test
    fun `charging with no estimate yet just says charging`() {
        val face = batteryFace(percent = 40, isCharging = true, chargeTimeRemainingMillis = null)
        assertEquals("charging", face.statusLine)
    }

    @Test
    fun `not charging and not full says not charging`() {
        val face = batteryFace(percent = 55, isCharging = false, chargeTimeRemainingMillis = null)
        assertEquals("not charging", face.statusLine)
        assertEquals("55%", face.percentText)
    }

    @Test
    fun `duration formats hours and minutes, dropping a zero part`() {
        assertEquals("1h 42m", formatChargeDuration((60 + 42) * 60_000L))
        assertEquals("2h", formatChargeDuration(120 * 60_000L))
        assertEquals("9m", formatChargeDuration(9 * 60_000L))
    }

    @Test
    fun `battery icon key maps to the battery face at medium and up`() {
        assertEquals(LiveFace.BATTERY, LiveFace.forIconKey("battery", TileSize.MEDIUM))
        assertEquals(LiveFace.BATTERY, LiveFace.forIconKey("battery", TileSize.WIDE))
    }

    @Test
    fun `battery tile stays static at small`() {
        assertNull(LiveFace.forIconKey("battery", TileSize.SMALL))
    }

    @Test
    fun `battery face flips`() {
        assertTrue(LiveFace.BATTERY.flips)
    }
}
