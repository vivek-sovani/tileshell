package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryHistoryTest {

    private val h = 3_600_000L
    private fun s(t: Long, level: Int, charging: Boolean = false, screenOn: Boolean = false) =
        BatterySample(t, level, charging, screenOn)

    @Test
    fun `samples round-trip and junk is skipped`() {
        val a = BatterySample(1000, 64, charging = false, screenOn = true, currentMa = 410)
        assertEquals(a, decodeBatterySample(encodeBatterySample(a)))
        assertEquals(a.copy(currentMa = null), decodeBatterySample(encodeBatterySample(a.copy(currentMa = null))))
        assertNull(decodeBatterySample("garbage"))
        assertNull(decodeBatterySample("1000,140,0,0,"))
    }

    @Test
    fun `since unplug starts after the last charging sample, empty while charging`() {
        val log = listOf(s(0, 90), s(h, 100, charging = true), s(2 * h, 99), s(3 * h, 97))
        assertEquals(listOf(99, 97), samplesSinceUnplug(log).map { it.level })
        assertEquals(emptyList<BatterySample>(), samplesSinceUnplug(log + s(4 * h, 97, charging = true)))
    }

    @Test
    fun `drain rate needs half an hour and a 1 percent drop`() {
        val log = listOf(s(0, 100, charging = true), s(h, 90), s(3 * h, 84))
        assertEquals(3.0, drainRatePerHour(log, nowMillis = 3 * h)!!, 0.001)
        assertNull(drainRatePerHour(listOf(s(0, 90), s(10 * 60_000L, 89)), nowMillis = 10 * 60_000L))
        assertNull(drainRatePerHour(listOf(s(0, 90), s(h, 90)), nowMillis = h))
    }

    @Test
    fun `time left label`() {
        assertEquals("about 9 h left", timeLeftLabel(hoursLeft(64, 7.0)))
        assertEquals("about 45 min left", timeLeftLabel(0.75))
        assertNull(timeLeftLabel(hoursLeft(64, null)))
    }

    @Test
    fun `screen split attributes each gap to the earlier sample's screen state`() {
        val log = listOf(s(0, 100, charging = true), s(h, 100, screenOn = true), s(2 * h, 95), s(5 * h, 92))
        val split = screenSplit(log, nowMillis = 6 * h)
        assertEquals(ScreenSplit(onMillis = h, onDrop = 5, offMillis = 4 * h, offDrop = 3), split)
    }

    @Test
    fun `daily usage sums discharge drops, ignoring charging`() {
        val now = System.currentTimeMillis()
        val log = listOf(s(now - 3 * h, 80), s(now - 2 * h, 75), s(now - 90 * 60_000L, 75, charging = true), s(now - h, 90), s(now, 88))
        val days = dailyUsage(log, now)
        assertEquals(7, days.size)
        // 80→75 and 90→88; the drop across the charge is ignored. (Summed, so
        // the test holds even when those samples straddle midnight.)
        assertEquals(7, days.sumOf { it.second })
    }

    @Test
    fun `current is normalised to mA, positive while draining`() {
        assertEquals(410, normaliseCurrentMa(-410_000, charging = false))
        assertEquals(410, normaliseCurrentMa(410, charging = false))
        assertEquals(-2450, normaliseCurrentMa(2450, charging = true))
        assertNull(normaliseCurrentMa(0, charging = false))
    }

    @Test
    fun `screen rates average by screen state and skip charging and long gaps`() {
        val log = listOf(
            s(0, 100, screenOn = true), s(h, 90),            // on: 10% in 1 h
            s(3 * h, 86), s(3 * h + 1, 86, charging = true), // off: 4% in 2 h
            s(5 * h, 100), s(10 * h, 90),                     // 5 h gap: skipped
        )
        val (on, off) = screenRates(log)
        assertEquals(10.0, on!!, 0.001)
        assertEquals(2.0, off!!, 0.01)
    }

    @Test
    fun `while charging, the split covers the last stretch on battery`() {
        val log = listOf(s(0, 100, charging = true), s(h, 99, screenOn = true), s(2 * h, 90), s(4 * h, 80), s(4 * h + 1, 80, charging = true), s(5 * h, 95, charging = true))
        assertEquals(listOf(99, 90, 80, 80), lastDischargeRun(log).map { it.level })
        val split = screenSplit(log, nowMillis = 6 * h)
        assertEquals(9, split.onDrop)
        assertEquals(10, split.offDrop)
    }
}
