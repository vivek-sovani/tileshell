package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketsHubTest {

    private fun quote(symbol: String, pct: Double) = StockQuote(
        symbol = symbol, displayName = symbol, currency = "INR", price = 100.0, previousClose = 100.0,
        change = pct, changePercent = pct, dayHigh = 100.0, dayLow = 100.0, marketOpen = true,
    )

    @Test
    fun `gainers are the largest positive changes first`() {
        val q = listOf(quote("A", 1.0), quote("B", 4.5), quote("C", -2.0), quote("D", 0.0), quote("E", 2.2))
        assertEquals(listOf("B", "E", "A"), topMovers(q, gainers = true).map { it.symbol })
    }

    @Test
    fun `losers are the largest negative changes first and never include risers`() {
        val q = listOf(quote("A", 1.0), quote("B", -4.5), quote("C", -2.0), quote("D", 0.0))
        assertEquals(listOf("B", "C"), topMovers(q, gainers = false).map { it.symbol })
    }

    @Test
    fun `movers are capped`() {
        val q = (1..10).map { quote("S$it", it.toDouble()) }
        assertEquals(6, topMovers(q, gainers = true).size)
        assertEquals(2, topMovers(q, gainers = true, limit = 2).size)
    }

    @Test
    fun `mover symbols come from the region's baskets without repeats`() {
        val india = moverSymbols("india")
        assertTrue(india.isNotEmpty())
        assertTrue(india.all { it.symbol.endsWith(".NS") })
        assertEquals(india.size, india.map { it.symbol }.toSet().size)
        assertTrue(moverSymbols("us").none { it.symbol.endsWith(".NS") })
    }

    @Test
    fun `watchlist round trips`() {
        val list = listOf(WatchSymbol("TCS.NS", "Tata Consultancy"), WatchSymbol("GC=F", "Gold"))
        assertEquals(list, decodeWatchlist(encodeWatchlist(list)))
    }

    @Test
    fun `never saved is null, an emptied list is empty`() {
        assertNull(decodeWatchlist(null))
        assertEquals(emptyList<WatchSymbol>(), decodeWatchlist(encodeWatchlist(emptyList())))
    }

    @Test
    fun `tabs and newlines in a name cannot break the format`() {
        val list = listOf(WatchSymbol("X", "Bad\tName\nHere"))
        assertEquals("Bad Name Here", decodeWatchlist(encodeWatchlist(list))!!.single().displayName)
    }

    @Test
    fun `a missing name falls back to the symbol and repeats are dropped`() {
        val decoded = decodeWatchlist("ITC.NS\nITC.NS\t ITC Ltd")!!
        assertEquals(listOf(WatchSymbol("ITC.NS", "ITC.NS")), decoded)
    }

    @Test
    fun `adding twice or past the cap changes nothing`() {
        val one = watchlistWith(emptyList(), WatchSymbol("A", "A"))
        assertEquals(one, watchlistWith(one, WatchSymbol("A", "Again")))
        val full = (1..MAX_WATCHLIST).map { WatchSymbol("S$it", "S$it") }
        assertEquals(full, watchlistWith(full, WatchSymbol("NEW", "New")))
    }

    @Test
    fun `removing drops just that symbol`() {
        val list = listOf(WatchSymbol("A", "A"), WatchSymbol("B", "B"))
        assertEquals(listOf(WatchSymbol("B", "B")), watchlistWithout(list, "A"))
    }

    @Test
    fun `kinds follow the symbol's shape`() {
        assertEquals("stocks", watchKind("TCS.NS"))
        assertEquals("stocks", watchKind("AAPL"))
        assertEquals("commodities", watchKind("GC=F"))
        assertEquals("currencies", watchKind("USDINR=X"))
        assertEquals("crypto", watchKind("BTC-USD"))
    }

    @Test
    fun `indian indices follow the NSE session, not New York's`() {
        // Tue 2026-10-06 06:00 UTC = 11:30 IST (NSE open, NYSE closed).
        val tuesdayNoonIst = java.time.ZonedDateTime.of(2026, 10, 6, 11, 30, 0, 0, java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        assertTrue(isMarketInSession("^NSEI", tuesdayNoonIst))
        assertTrue(isMarketInSession("RELIANCE.NS", tuesdayNoonIst))
        assertEquals(false, isMarketInSession("^GSPC", tuesdayNoonIst))
    }

    @Test
    fun `refresh waits for the open when every market is shut`() {
        // Sat 2026-10-10 12:00 IST: NSE and NYSE shut; the next open is days away, capped at six hours.
        val saturday = java.time.ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        assertEquals(MAX_CLOSED_SLEEP_MS, marketsRefreshDelayMs(listOf("^NSEI", "^GSPC", "RELIANCE.NS"), 60_000L, saturday))
    }

    @Test
    fun `refresh uses the user's rate while any market is trading`() {
        val tuesdayNoonIst = java.time.ZonedDateTime.of(2026, 10, 6, 11, 30, 0, 0, java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        assertEquals(60_000L, marketsRefreshDelayMs(listOf("^GSPC", "^NSEI"), 60_000L, tuesdayNoonIst))
        assertEquals(60_000L, marketsRefreshDelayMs(emptyList(), 60_000L, tuesdayNoonIst))
    }
}
