package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MarketHoursTest {

    private fun millisAt(zone: String, y: Int, m: Int, d: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(y, m, d, hour, minute, 0, 0, ZoneId.of(zone)).toInstant().toEpochMilli()

    // --- session resolution ---

    @Test
    fun `bare ticker resolves to the US session`() {
        val s = marketSessionFor("AAPL")
        assertEquals(ZoneId.of("America/New_York"), s.zone)
        assertEquals(9 * 60 + 30, s.openMinuteOfDay)
        assertEquals(16 * 60, s.closeMinuteOfDay)
    }

    @Test
    fun `indian suffixes resolve to IST`() {
        assertEquals(ZoneId.of("Asia/Kolkata"), marketSessionFor("RELIANCE.NS").zone)
        assertEquals(ZoneId.of("Asia/Kolkata"), marketSessionFor("TCS.BO").zone)
    }

    @Test
    fun `london and tokyo resolve to their own zones`() {
        assertEquals(ZoneId.of("Europe/London"), marketSessionFor("VOD.L").zone)
        assertEquals(ZoneId.of("Asia/Tokyo"), marketSessionFor("7203.T").zone)
    }

    @Test
    fun `unknown suffix falls back to the US session rather than failing`() {
        assertEquals(ZoneId.of("America/New_York"), marketSessionFor("FOO.ZZZ").zone)
    }

    @Test
    fun `blank symbol falls back to the US session`() {
        assertEquals(ZoneId.of("America/New_York"), marketSessionFor("").zone)
        assertEquals(ZoneId.of("America/New_York"), marketSessionFor("   ").zone)
    }

    @Test
    fun `crypto pairs are always open`() {
        assertTrue(marketSessionFor("BTC-USD").alwaysOpen)
        // Sunday 3am UTC — no equity market anywhere is open.
        assertTrue(isMarketOpenFor("BTC-USD", millisAt("UTC", 2026, 3, 8, 3, 0)))
    }

    @Test
    fun `futures and fx trade all day on weekdays but not weekends`() {
        // Wednesday 11pm New York — an equities session is long closed.
        assertTrue(isMarketOpenFor("GC=F", millisAt("America/New_York", 2026, 3, 11, 23, 0)))
        assertTrue(isMarketOpenFor("EURUSD=X", millisAt("America/New_York", 2026, 3, 11, 2, 0)))
        // Saturday.
        assertFalse(isMarketOpenFor("GC=F", millisAt("America/New_York", 2026, 3, 14, 12, 0)))
    }

    // --- open/closed, in the market's own timezone ---

    @Test
    fun `US stock is open at 10am eastern and shut at 10am india time`() {
        // Wednesday 2026-03-11, 10:00 New York -> open.
        assertTrue(isMarketOpenFor("AAPL", millisAt("America/New_York", 2026, 3, 11, 10, 0)))
        // The same instant expressed as 9am IST is 11:30pm ET the previous day -> shut.
        // This is precisely the case the old device-local check got backwards.
        assertFalse(isMarketOpenFor("AAPL", millisAt("Asia/Kolkata", 2026, 3, 11, 9, 0)))
    }

    @Test
    fun `indian stock is open at 10am IST regardless of device timezone`() {
        assertTrue(isMarketOpenFor("RELIANCE.NS", millisAt("Asia/Kolkata", 2026, 3, 11, 10, 0)))
        assertFalse(isMarketOpenFor("RELIANCE.NS", millisAt("Asia/Kolkata", 2026, 3, 11, 16, 0)))
    }

    @Test
    fun `session edges are inclusive of open and exclusive of close`() {
        assertTrue(isMarketOpenFor("RELIANCE.NS", millisAt("Asia/Kolkata", 2026, 3, 11, 9, 15)))
        assertFalse(isMarketOpenFor("RELIANCE.NS", millisAt("Asia/Kolkata", 2026, 3, 11, 9, 14)))
        assertTrue(isMarketOpenFor("RELIANCE.NS", millisAt("Asia/Kolkata", 2026, 3, 11, 15, 29)))
        assertFalse(isMarketOpenFor("RELIANCE.NS", millisAt("Asia/Kolkata", 2026, 3, 11, 15, 30)))
    }

    @Test
    fun `weekends are closed`() {
        // 2026-03-14 is a Saturday, 2026-03-15 a Sunday.
        assertFalse(isMarketOpenFor("AAPL", millisAt("America/New_York", 2026, 3, 14, 12, 0)))
        assertFalse(isMarketOpenFor("AAPL", millisAt("America/New_York", 2026, 3, 15, 12, 0)))
    }

    @Test
    fun `tel aviv trades sunday and not friday`() {
        assertTrue(isMarketOpenFor("TEVA.TA", millisAt("Asia/Jerusalem", 2026, 3, 15, 12, 0)))
        assertFalse(isMarketOpenFor("TEVA.TA", millisAt("Asia/Jerusalem", 2026, 3, 13, 12, 0)))
    }

    // --- sleeping until the next open ---

    @Test
    fun `already open means no wait`() {
        assertEquals(0L, millisUntilMarketOpen("AAPL", millisAt("America/New_York", 2026, 3, 11, 10, 0)))
    }

    @Test
    fun `before the open waits exactly until the bell`() {
        val now = millisAt("America/New_York", 2026, 3, 11, 8, 0)
        // 08:00 -> 09:30 is 90 minutes.
        assertEquals(90L * 60 * 1000, millisUntilMarketOpen("AAPL", now))
    }

    @Test
    fun `after the close waits to the next trading day, capped`() {
        val now = millisAt("America/New_York", 2026, 3, 11, 17, 0)
        val wait = millisUntilMarketOpen("AAPL", now)
        // Next open is 16.5h away, beyond the cap.
        assertEquals(MAX_CLOSED_SLEEP_MS, wait)
    }

    @Test
    fun `weekend wait is capped rather than spanning the whole weekend`() {
        val now = millisAt("America/New_York", 2026, 3, 14, 12, 0)
        assertEquals(MAX_CLOSED_SLEEP_MS, millisUntilMarketOpen("AAPL", now))
    }

    @Test
    fun `crypto never waits`() {
        assertEquals(0L, millisUntilMarketOpen("BTC-USD", millisAt("UTC", 2026, 3, 15, 3, 0)))
    }

    // --- the delay a live tile should actually use ---

    @Test
    fun `open market uses the configured interval`() {
        val now = millisAt("America/New_York", 2026, 3, 11, 10, 0)
        assertEquals(60_000L, nextMarketRefreshDelayMs("AAPL", 60_000L, now))
    }

    @Test
    fun `closed market sleeps to the open instead of polling`() {
        val now = millisAt("America/New_York", 2026, 3, 11, 8, 0)
        assertEquals(90L * 60 * 1000, nextMarketRefreshDelayMs("AAPL", 60_000L, now))
    }

    @Test
    fun `closed market never returns less than the configured interval`() {
        // One minute before the open, with a five-minute configured interval.
        val now = millisAt("America/New_York", 2026, 3, 11, 9, 29)
        assertEquals(5L * 60_000, nextMarketRefreshDelayMs("AAPL", 5L * 60_000, now))
    }
}
