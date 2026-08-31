package com.tileshell.core.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeStockChangeTest {

    @Test
    fun `a price above previous close is a positive change and percent`() {
        val (change, percent) = computeStockChange(price = 110.0, previousClose = 100.0)
        assertEquals(10.0, change, 0.0001)
        assertEquals(10.0, percent, 0.0001)
    }

    @Test
    fun `a price below previous close is negative`() {
        val (change, percent) = computeStockChange(price = 95.0, previousClose = 100.0)
        assertEquals(-5.0, change, 0.0001)
        assertEquals(-5.0, percent, 0.0001)
    }

    @Test
    fun `a zero previous close yields zero percent, not a crash`() {
        val (_, percent) = computeStockChange(price = 10.0, previousClose = 0.0)
        assertEquals(0.0, percent, 0.0001)
    }
}

class FormatStockPriceTest {

    @Test
    fun `an everyday price uses two decimals, thousands-grouped`() {
        assertEquals("1,287.00", formatStockPrice(1287.0, ""))
        assertEquals("$319.70", formatStockPrice(319.70, "USD"))
    }

    @Test
    fun `a sub-1 value uses four decimals so a small currency-pair rate isn't rounded away`() {
        assertEquals("$0.0105", formatStockPrice(0.0105, "USD"))
    }

    @Test
    fun `exactly zero still uses two decimals, not four`() {
        assertEquals("0.00", formatStockPrice(0.0, ""))
    }
}

private fun tradingPeriod(startSec: Long, endSec: Long) = JSONObject().put(
    "regular",
    JSONObject().put("start", startSec).put("end", endSec),
)

private fun chartMeta(
    symbol: String = "AAPL",
    price: Double? = 319.7,
    prevClose: Double? = 314.58,
    dayHigh: Double = 322.37,
    dayLow: Double = 315.45,
    currency: String = "USD",
    longName: String = "Apple Inc.",
    tradingPeriod: JSONObject? = tradingPeriod(1000, 2000),
) = JSONObject().apply {
    put("symbol", symbol)
    price?.let { put("regularMarketPrice", it) }
    prevClose?.let { put("chartPreviousClose", it) }
    put("regularMarketDayHigh", dayHigh)
    put("regularMarketDayLow", dayLow)
    put("currency", currency)
    put("longName", longName)
    tradingPeriod?.let { put("currentTradingPeriod", it) }
}

private fun chartBody(meta: JSONObject, closes: List<Double?> = emptyList()) = JSONObject().put(
    "chart",
    JSONObject().put(
        "result",
        JSONArray().put(
            JSONObject().apply {
                put("meta", meta)
                put(
                    "indicators",
                    JSONObject().put(
                        "quote",
                        JSONArray().put(
                            JSONObject().put(
                                "close",
                                JSONArray().apply { closes.forEach { if (it == null) put(JSONObject.NULL) else put(it) } },
                            ),
                        ),
                    ),
                )
            },
        ),
    ),
).toString()

class ParseStockQuoteTest {

    @Test
    fun `parses price, change and day range from a real-shaped chart response`() {
        val body = chartBody(chartMeta())
        val quote = parseStockQuote("AAPL", body, nowMillis = 1500_000L)
        assertEquals("AAPL", quote?.symbol)
        assertEquals("Apple Inc.", quote?.displayName)
        assertEquals(319.7, quote?.price ?: 0.0, 0.0001)
        assertEquals(314.58, quote?.previousClose ?: 0.0, 0.0001)
        assertTrue((quote?.change ?: 0.0) > 0)
        assertEquals(322.37, quote?.dayHigh ?: 0.0, 0.0001)
        assertEquals(315.45, quote?.dayLow ?: 0.0, 0.0001)
    }

    @Test
    fun `missing price yields null, not a crash`() {
        val body = chartBody(chartMeta(price = null))
        assertNull(parseStockQuote("AAPL", body, nowMillis = 1500_000L))
    }

    @Test
    fun `missing previous close yields null`() {
        val body = chartBody(chartMeta(prevClose = null))
        assertNull(parseStockQuote("AAPL", body, nowMillis = 1500_000L))
    }

    @Test
    fun `garbage body yields null, not a crash`() {
        assertNull(parseStockQuote("AAPL", "not json", nowMillis = 0L))
    }
}

class IsMarketOpenTest {

    @Test
    fun `now inside the regular trading window is open`() {
        val meta = JSONObject().put("currentTradingPeriod", tradingPeriod(1000, 2000))
        assertTrue(isMarketOpen(meta, nowMillis = 1500_000L))
    }

    @Test
    fun `now before or after the window is closed`() {
        val meta = JSONObject().put("currentTradingPeriod", tradingPeriod(1000, 2000))
        assertFalse(isMarketOpen(meta, nowMillis = 500_000L))
        assertFalse(isMarketOpen(meta, nowMillis = 2500_000L))
    }

    @Test
    fun `no trading period field is treated as closed, not a crash`() {
        assertFalse(isMarketOpen(JSONObject(), nowMillis = 1500_000L))
    }
}

class ParseSparklinePointsTest {

    @Test
    fun `reads the intraday close series in order`() {
        val body = chartBody(chartMeta(), closes = listOf(317.32, 316.78, 316.5))
        assertEquals(listOf(317.32, 316.78, 316.5), parseSparklinePoints(body))
    }

    @Test
    fun `null points (no trade yet that interval) are dropped, not crashed on`() {
        val body = chartBody(chartMeta(), closes = listOf(317.32, null, 316.5))
        assertEquals(listOf(317.32, 316.5), parseSparklinePoints(body))
    }

    @Test
    fun `garbage body yields an empty list`() {
        assertEquals(emptyList<Double>(), parseSparklinePoints("not json"))
    }
}

private fun searchQuote(symbol: String, quoteType: String = "EQUITY", shortname: String? = null, longname: String? = null, exchange: String = "NMS") =
    JSONObject().apply {
        put("symbol", symbol)
        put("quoteType", quoteType)
        shortname?.let { put("shortname", it) }
        longname?.let { put("longname", it) }
        put("exchange", exchange)
    }

class ParseStockSearchResultsTest {

    @Test
    fun `keeps only equities, preferring shortname over longname`() {
        val body = JSONObject().put(
            "quotes",
            JSONArray(
                listOf(
                    searchQuote("AAPL", shortname = "Apple Inc.", longname = "Apple Incorporated"),
                    searchQuote("XYZ", quoteType = "CRYPTOCURRENCY"),
                ),
            ),
        ).toString()
        val results = parseStockSearchResults(body)
        assertEquals(1, results.size)
        assertEquals("AAPL", results[0].symbol)
        assertEquals("Apple Inc.", results[0].displayName)
    }

    @Test
    fun `falls back to longname, then the symbol itself, when shortname is blank`() {
        val body = JSONObject().put(
            "quotes",
            JSONArray(listOf(searchQuote("TCS.NS", longname = "Tata Consultancy Services"), searchQuote("XYZ.NS"))),
        ).toString()
        val results = parseStockSearchResults(body)
        assertEquals("Tata Consultancy Services", results[0].displayName)
        assertEquals("XYZ.NS", results[1].displayName)
    }

    @Test
    fun `no quotes field or garbage body yields an empty list`() {
        assertEquals(emptyList<StockSearchResult>(), parseStockSearchResults(JSONObject().toString()))
        assertEquals(emptyList<StockSearchResult>(), parseStockSearchResults("not json"))
    }
}

class IndexTickerForTest {

    @Test
    fun `an NSE or BSE symbol tracks NIFTY 50`() {
        assertEquals("^NSEI", indexTickerFor("RELIANCE.NS"))
        assertEquals("^NSEI", indexTickerFor("RELIANCE.BO"))
        assertEquals("NIFTY 50", indexDisplayNameFor("TCS.NS"))
    }

    @Test
    fun `anything else tracks the S&P 500`() {
        assertEquals("^GSPC", indexTickerFor("AAPL"))
        assertEquals("S&P 500", indexDisplayNameFor("JPM"))
    }
}

class StockTileCodecTest {

    @Test
    fun `round-trips a single stock selection`() {
        val encoded = StockTile.encodeSingle("AAPL", "Apple Inc.")
        assertEquals(StockTile.Selection.Single("AAPL", "Apple Inc."), StockTile.decode(encoded))
    }

    @Test
    fun `round-trips a category selection`() {
        val encoded = StockTile.encodeCategory("in-banking", "Banking")
        assertEquals(StockTile.Selection.Category("in-banking", "Banking"), StockTile.decode(encoded))
    }

    @Test
    fun `decode rejects a string with no stock prefix`() {
        assertNull(StockTile.decode("single|AAPL|Apple Inc."))
    }

    @Test
    fun `decode rejects a not-yet-picked or malformed tile`() {
        assertNull(StockTile.decode("stock:"))
        assertNull(StockTile.decode("stock:single||"))
        assertNull(StockTile.decode("stock:unknownkind|AAPL|Apple"))
    }

    @Test
    fun `round-trips a multi-stock custom list`() {
        val encoded = StockTile.encodeMultiStock(listOf("AAPL" to "Apple", "TCS.NS" to "Tata Consultancy Services"), "Apple & TCS")
        assertEquals(
            StockTile.Selection.MultiStock(listOf("AAPL" to "Apple", "TCS.NS" to "Tata Consultancy Services"), "Apple & TCS"),
            StockTile.decode(encoded),
        )
    }

    @Test
    fun `a multi-stock list with no valid entries decodes to null`() {
        assertNull(StockTile.decode("stock:multi||My List"))
    }
}

class MultiStockLabelTest {

    @Test
    fun `no names falls back to a generic label`() {
        assertEquals("custom list", multiStockLabel(emptyList()))
    }

    @Test
    fun `one name is used as-is`() {
        assertEquals("Apple", multiStockLabel(listOf("Apple")))
    }

    @Test
    fun `two names are joined with an ampersand`() {
        assertEquals("Apple & TCS", multiStockLabel(listOf("Apple", "TCS")))
    }

    @Test
    fun `three or more names show the first two plus a count of the rest`() {
        assertEquals("Apple, TCS +2 more", multiStockLabel(listOf("Apple", "TCS", "Reliance", "Infosys")))
    }
}

class StockCatalogTest {

    @Test
    fun `every category has a unique id`() {
        val ids = STOCK_CATEGORIES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every category has at least one symbol, and every symbol is non-blank`() {
        STOCK_CATEGORIES.forEach { category ->
            assertTrue("${category.id} should have symbols", category.symbols.isNotEmpty())
            category.symbols.forEach { assertTrue(it.symbol.isNotBlank()) }
        }
    }

    @Test
    fun `every category's region is one of the declared display-order regions`() {
        STOCK_CATEGORIES.forEach {
            assertTrue("${it.id}'s region '${it.region}' isn't in the display order", it.region in STOCK_CATEGORY_REGION_ORDER)
        }
    }

    @Test
    fun `every category has a non-blank sector index ticker and display name`() {
        STOCK_CATEGORIES.forEach {
            assertTrue("${it.id} needs an indexTicker", it.indexTicker.isNotBlank())
            assertTrue("${it.id} needs an indexDisplayName", it.indexDisplayName.isNotBlank())
        }
    }

    @Test
    fun `stockCategoryFor finds a real category and returns null for an unknown id`() {
        assertEquals("Banking", stockCategoryFor("in-banking")?.displayName)
        assertNull(stockCategoryFor("no-such-category"))
    }
}

private fun calendarAt(dayOfWeek: Int, hour: Int, minute: Int = 0): java.util.Calendar =
    java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.DAY_OF_WEEK, dayOfWeek)
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
    }

class IsMarketHoursNowTest {

    @Test
    fun `9am to just before 4pm on a weekday is market hours`() {
        assertTrue(isMarketHoursNow(calendarAt(java.util.Calendar.WEDNESDAY, 9)))
        assertTrue(isMarketHoursNow(calendarAt(java.util.Calendar.WEDNESDAY, 15, 59)))
    }

    @Test
    fun `before 9am or at-or-after 4pm on a weekday is closed`() {
        assertFalse(isMarketHoursNow(calendarAt(java.util.Calendar.WEDNESDAY, 8, 59)))
        assertFalse(isMarketHoursNow(calendarAt(java.util.Calendar.WEDNESDAY, 16)))
        assertFalse(isMarketHoursNow(calendarAt(java.util.Calendar.WEDNESDAY, 23)))
    }

    @Test
    fun `weekends are always closed, even during 9am-4pm`() {
        assertFalse(isMarketHoursNow(calendarAt(java.util.Calendar.SATURDAY, 12)))
        assertFalse(isMarketHoursNow(calendarAt(java.util.Calendar.SUNDAY, 12)))
    }
}

class EffectiveMarketRefreshMsTest {

    @Test
    fun `during market hours uses exactly the configured interval`() {
        assertEquals(60_000L, effectiveMarketRefreshMs(60_000L, calendarAt(java.util.Calendar.MONDAY, 10)))
    }

    @Test
    fun `outside market hours slows a fast configured interval down to the closed-market floor`() {
        assertEquals(CLOSED_MARKET_REFRESH_MS, effectiveMarketRefreshMs(60_000L, calendarAt(java.util.Calendar.MONDAY, 20)))
    }

    @Test
    fun `outside market hours never speeds up an already-slower configured interval`() {
        val slower = CLOSED_MARKET_REFRESH_MS * 2
        assertEquals(slower, effectiveMarketRefreshMs(slower, calendarAt(java.util.Calendar.SUNDAY, 12)))
    }
}
