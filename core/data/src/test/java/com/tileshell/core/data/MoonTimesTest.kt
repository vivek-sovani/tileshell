package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class MoonTimesTest {

    private val ist = ZoneOffset.ofHoursMinutes(5, 30)
    private fun ist(iso: String) = LocalDateTime.parse(iso).toInstant(ist).toEpochMilli()
    private val puneLat = 18.52
    private val puneLon = 73.86
    private val toleranceMs = 4 * 60_000L

    private fun assertNear(expected: Long, actual: Long?) {
        requireNotNull(actual)
        assertTrue("off by ${(actual - expected) / 60_000.0} min", kotlin.math.abs(actual - expected) <= toleranceMs)
    }

    // Reference: US Naval Observatory rise/set/transit API, Pune (18.52N 73.86E), IST.
    @Test
    fun `matches the USNO moonrise and moonset for Pune on 24 September 2026`() {
        val m = MoonTimes.nextMoonriseMoonset(ist("2026-09-24T00:00"), puneLat, puneLon)
        assertNear(ist("2026-09-24T04:05"), m.moonsetMillis)
        assertNear(ist("2026-09-24T16:59"), m.moonriseMillis)
    }

    @Test
    fun `next events roll to the following day once today's have passed`() {
        val m = MoonTimes.nextMoonriseMoonset(ist("2026-09-24T17:30"), puneLat, puneLon)
        assertNear(ist("2026-09-25T04:57"), m.moonsetMillis)
        assertNear(ist("2026-09-25T17:34"), m.moonriseMillis)
    }

    @Test
    fun `both events are found after now`() {
        val now = ist("2026-10-03T08:00")
        val m = MoonTimes.nextMoonriseMoonset(now, puneLat, puneLon)
        assertNear(ist("2026-10-03T12:56"), m.moonsetMillis)
        assertTrue(m.moonriseMillis!! > now)
    }

    @Test
    fun `tithi display number is 1-15 with amavasya as 30`() {
        assertEquals(1, TithiInfo(Paksha.SHUKLA, 1, "pratipada").displayNumber)
        assertEquals(14, TithiInfo(Paksha.KRISHNA, 14, "chaturdashi").displayNumber)
        assertEquals(15, TithiInfo(Paksha.SHUKLA, 15, "purnima").displayNumber)
        assertEquals(30, TithiInfo(Paksha.KRISHNA, 15, "amavasya").displayNumber)
    }

    @Test
    fun `tithi number renders in Devanagari numerals`() {
        assertEquals("१", PanchangDevanagari.tithiNumber(TithiInfo(Paksha.SHUKLA, 1, "pratipada")))
        assertEquals("२", PanchangDevanagari.tithiNumber(TithiInfo(Paksha.KRISHNA, 2, "dwitiya")))
        assertEquals("१४", PanchangDevanagari.tithiNumber(TithiInfo(Paksha.KRISHNA, 14, "chaturdashi")))
        assertEquals("१५", PanchangDevanagari.tithiNumber(TithiInfo(Paksha.SHUKLA, 15, "purnima")))
        assertEquals("३०", PanchangDevanagari.tithiNumber(TithiInfo(Paksha.KRISHNA, 15, "amavasya")))
    }
}
