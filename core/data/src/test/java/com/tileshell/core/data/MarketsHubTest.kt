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

    private val watch = listOf(
        WatchSymbol("RELIANCE.NS", "Reliance Industries"), WatchSymbol("TCS.NS", "Tata Consultancy Services"),
        WatchSymbol("HDFCBANK.NS", "HDFC Bank"), WatchSymbol("INFY.NS", "Infosys"), WatchSymbol("ITC.NS", "ITC"),
        WatchSymbol("GC=F", "Gold"), WatchSymbol("SI=F", "Silver"), WatchSymbol("USDINR=X", "USD / INR"),
    )

    @Test
    fun `kinds in the watchlist keep a fixed order and skip empty ones`() {
        assertEquals(listOf("stocks", "commodities", "currencies"), watchKinds(watch))
        assertEquals(listOf("commodities"), watchKinds(listOf(WatchSymbol("GC=F", "Gold"))))
        assertEquals(emptyList<String>(), watchKinds(emptyList()))
    }

    @Test
    fun `a tile shows the marked symbols of its kind in watchlist order`() {
        val marked = listOf("INFY.NS", "RELIANCE.NS", "GC=F")
        assertEquals(listOf("RELIANCE.NS", "INFY.NS"), tileSymbols("stocks", watch, marked).map { it.symbol })
        assertEquals(listOf("GC=F"), tileSymbols("commodities", watch, marked).map { it.symbol })
    }

    @Test
    fun `a tile with nothing marked shows the first few of its kind`() {
        assertEquals(listOf("RELIANCE.NS", "TCS.NS", "HDFCBANK.NS"), tileSymbols("stocks", watch, emptyList()).map { it.symbol })
        assertEquals(listOf("USDINR=X"), tileSymbols("currencies", watch, listOf("GC=F")).map { it.symbol })
        assertEquals(emptyList<WatchSymbol>(), tileSymbols("crypto", watch, emptyList()))
    }

    @Test
    fun `marks for symbols no longer on the watchlist are ignored`() {
        assertEquals(listOf("RELIANCE.NS", "TCS.NS", "HDFCBANK.NS"), tileSymbols("stocks", watch, listOf("GONE.NS")).map { it.symbol })
    }

    @Test
    fun `a tile is capped`() {
        val many = (1..20).map { WatchSymbol("S$it.NS", "S$it") }
        assertEquals(MAX_TILE_SYMBOLS, tileSymbols("stocks", many, many.map { it.symbol }).size)
    }

    @Test
    fun `the indices tile defaults to the NIFTY 50`() {
        assertEquals(listOf("^NSEI"), tileIndices(null).map { it.symbol })
        assertEquals(listOf("^NSEI"), tileIndices(emptyList()).map { it.symbol })
        assertEquals(listOf("^BSESN", "^GSPC"), tileIndices(listOf("^GSPC", "^BSESN", "^UNKNOWN")).map { it.symbol })
    }

    @Test
    fun `toggling a mark adds then removes it`() {
        val once = toggleMarked(emptyList(), "TCS.NS")
        assertEquals(listOf("TCS.NS"), once)
        assertEquals(emptyList<String>(), toggleMarked(once, "TCS.NS"))
    }

    @Test
    fun `tile labels use the name when short and the ticker when long`() {
        assertEquals("HDFC Bank", tileLabel(WatchSymbol("HDFCBANK.NS", "HDFC Bank")))
        assertEquals("RELIANCE", tileLabel(WatchSymbol("RELIANCE.NS", "Reliance Industries")))
        assertEquals("USD / INR", tileLabel(WatchSymbol("USDINR=X", "USD / INR")))
        assertEquals("USDINR", tileLabel(WatchSymbol("USDINR=X", "US dollar to Indian rupee")))
    }

    @Test
    fun `a markets tile's kind round trips and anything else is the indices tile`() {
        WATCH_KINDS.forEach { assertEquals(it, MarketsTile.decode(MarketsTile.encode(it))) }
        assertEquals(MarketsTile.INDICES, MarketsTile.decode(""))
        assertEquals(MarketsTile.INDICES, MarketsTile.decode("markets:nonsense"))
        assertEquals(MarketsTile.INDICES, MarketsTile.decode("sports:x|y|z"))
    }

    @Test
    fun `all caps company names are tidied, acronyms kept`() {
        assertEquals("Reliance Industries Ltd", tidyName("RELIANCE INDUSTRIES LTD"))
        assertEquals("HDFC Bank Ltd", tidyName("HDFC BANK LTD"))
        assertEquals("ICICI Bank Limited", tidyName("ICICI BANK LIMITED"))
        assertEquals("ITC Ltd", tidyName("ITC LTD"))
        assertEquals("Tata Consultancy Services Limited", tidyName("TATA CONSULTANCY SERVICES LIMITED"))
        assertEquals("Apple Inc.", tidyName("APPLE INC."))
        assertEquals("Coca-Cola Co", tidyName("COCA-COLA CO"))
        assertEquals("TCS", tidyName("TCS"))
    }

    @Test
    fun `names that are not all caps are left alone`() {
        assertEquals("Reliance Industries", tidyName("Reliance Industries"))
        assertEquals("USD / INR", tidyName("USD / INR"))
        assertEquals("S&P 500", tidyName("S&P 500"))
        assertEquals("", tidyName(""))
    }

    @Test
    fun `a stock added in capitals is stored tidy, and so is one saved in capitals earlier`() {
        val added = watchlistWith(emptyList(), WatchSymbol("WIPRO.NS", "WIPRO LIMITED"))
        assertEquals("Wipro Limited", added.single().displayName)
        assertEquals("Infosys Ltd", decodeWatchlist("INFY.NS\tINFOSYS LTD")!!.single().displayName)
    }

    private val searchBody = """
        {"quotes":[
          {"quoteType":"CURRENCY","symbol":"EURUSD=X","shortname":"EUR/USD","exchange":"CCY"},
          {"quoteType":"FUTURE","symbol":"GC=F","shortname":"Gold Dec 26","exchange":"CMX"},
          {"quoteType":"FUTURE","symbol":"GCZ26.CMX","shortname":"Gold Dec 2026","exchange":"CMX"},
          {"quoteType":"CRYPTOCURRENCY","symbol":"BTC-USD","shortname":"Bitcoin USD","exchange":"CCC"},
          {"quoteType":"EQUITY","symbol":"RELIANCE.NS","shortname":"RELIANCE INDUSTRIES LTD","exchange":"NSI"},
          {"quoteType":"ETF","symbol":"GLD","shortname":"SPDR Gold Shares","exchange":"PCX"},
          {"quoteType":"MUTUALFUND","symbol":"0P000134T6","shortname":"Some fund"},
          {"quoteType":"INDEX","symbol":"^SPEUROUSD","shortname":"S&P Euro"}
        ]}
    """.trimIndent()

    @Test
    fun `market search takes stocks, currency pairs, crypto and continuous commodities only`() {
        val r = parseMarketSearchResults(searchBody)
        assertEquals(listOf("EURUSD=X", "GC=F", "BTC-USD", "RELIANCE.NS", "GLD"), r.map { it.symbol })
        assertEquals(listOf("currency pair", "commodity", "crypto", "stock", "stock"), r.map { it.kind })
    }

    @Test
    fun `a commodity's contract month is dropped from its name`() {
        assertEquals("Gold", parseMarketSearchResults(searchBody).first { it.symbol == "GC=F" }.displayName)
        val dated = """{"quotes":[{"quoteType":"FUTURE","symbol":"MGC=F","shortname":"Micro Gold Futures,Dec-2026"}]}"""
        assertEquals("Micro Gold Futures", parseMarketSearchResults(dated).single().displayName)
    }

    @Test
    fun `junk search results are empty`() {
        assertEquals(emptyList<MarketSearchResult>(), parseMarketSearchResults("nope"))
        assertEquals(emptyList<MarketSearchResult>(), parseMarketSearchResults("{}"))
    }

    @Test
    fun `a currency pair has no currency sign on its price`() {
        assertEquals("", priceCurrency("EURINR=X", "INR"))
        assertEquals("INR", priceCurrency("TCS.NS", "INR"))
        assertEquals("USD", priceCurrency("GC=F", "USD"))
    }

    @Test
    fun `the quick adds include the usual currency pairs and commodities`() {
        val kinds = POPULAR_WATCH_SYMBOLS.map { watchKind(it.symbol) }.toSet()
        assertTrue(kinds.containsAll(setOf("commodities", "currencies", "crypto")))
        assertEquals(POPULAR_WATCH_SYMBOLS.size, POPULAR_WATCH_SYMBOLS.map { it.symbol }.toSet().size)
    }

    @Test
    fun `kinds read in the singular`() {
        assertEquals("stock", kindLabel("stocks"))
        assertEquals("commodity", kindLabel("commodities"))
        assertEquals("currency pair", kindLabel("currencies"))
        assertEquals("crypto", kindLabel("crypto"))
    }

    @Test
    fun `typing a name finds the matching quick add first`() {
        assertEquals(listOf("GC=F"), popularMatches("gold").map { it.symbol })
        assertEquals(listOf("EURUSD=X"), popularMatches("eurusd").map { it.symbol })
        assertEquals(listOf("EURUSD=X"), popularMatches("eur/usd").map { it.symbol })
        assertTrue(popularMatches("inr").size >= 3)
        assertEquals(emptyList<WatchSymbol>(), popularMatches(""))
        assertEquals(emptyList<WatchSymbol>(), popularMatches("zzzz"))
    }

    @Test
    fun `a chart range changes from first to last`() {
        assertEquals(10.0, seriesChangePercent(listOf(100.0, 90.0, 110.0))!!, 1e-9)
        assertEquals(null, seriesChangePercent(listOf(100.0)))
        assertEquals(null, seriesChangePercent(listOf(0.0, 5.0)))
    }
}
