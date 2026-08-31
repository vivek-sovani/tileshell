package com.tileshell.core.data

import org.json.JSONObject
import java.net.URLEncoder

private const val YAHOO_CHART_BASE = "https://query1.finance.yahoo.com/v8/finance/chart"
private const val YAHOO_SEARCH_BASE = "https://query1.finance.yahoo.com/v1/finance/search"

/** A generic desktop-browser UA — Yahoo's endpoints 429 the JVM's default one (verified live). */
private val YAHOO_HEADERS = mapOf(
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
)

/** A live quote for one symbol, from Yahoo Finance's public chart endpoint. */
data class StockQuote(
    val symbol: String,
    val displayName: String,
    val currency: String,
    val price: Double,
    val previousClose: Double,
    val change: Double,
    val changePercent: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val marketOpen: Boolean,
)

data class StockSearchResult(val symbol: String, val displayName: String, val exchange: String)

/** Plain price delta + percent — pure so [parseStockQuote] doesn't need a real quote object to unit-test the arithmetic. */
fun computeStockChange(price: Double, previousClose: Double): Pair<Double, Double> {
    val change = price - previousClose
    val changePercent = if (previousClose != 0.0) (change / previousClose) * 100.0 else 0.0
    return change to changePercent
}

/**
 * The NIFTY 50 for an NSE/BSE symbol, else the S&P 500 — the index a LARGE
 * stock tile's back face tracks (verified live: both tickers resolve on the
 * same chart endpoint stock quotes use, so no separate index-specific fetch
 * path is needed).
 */
fun indexTickerFor(symbol: String): String = if (symbol.endsWith(".NS") || symbol.endsWith(".BO")) "^NSEI" else "^GSPC"

fun indexDisplayNameFor(symbol: String): String =
    if (symbol.endsWith(".NS") || symbol.endsWith(".BO")) "NIFTY 50" else "S&P 500"

/** Whether [nowMillis] falls inside the chart response's own declared regular-session window. */
internal fun isMarketOpen(meta: JSONObject, nowMillis: Long): Boolean {
    val regular = meta.optJSONObject("currentTradingPeriod")?.optJSONObject("regular") ?: return false
    val startSec = regular.optLong("start", -1)
    val endSec = regular.optLong("end", -1)
    if (startSec < 0 || endSec < 0) return false
    val nowSec = nowMillis / 1000
    return nowSec in startSec..endSec
}

internal fun parseStockQuote(symbol: String, body: String, nowMillis: Long): StockQuote? = runCatching {
    val meta = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
    val price = meta.optDouble("regularMarketPrice", Double.NaN)
    val prevClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", Double.NaN))
    if (price.isNaN() || prevClose.isNaN()) return null
    val (change, changePercent) = computeStockChange(price, prevClose)
    StockQuote(
        symbol = meta.optString("symbol", symbol),
        displayName = meta.optString("longName", meta.optString("shortName", symbol)),
        currency = meta.optString("currency", ""),
        price = price,
        previousClose = prevClose,
        change = change,
        changePercent = changePercent,
        dayHigh = meta.optDouble("regularMarketDayHigh", price),
        dayLow = meta.optDouble("regularMarketDayLow", price),
        marketOpen = isMarketOpen(meta, nowMillis),
    )
}.getOrNull()

/** The day's intraday close prices, oldest first — feeds the tile's sparkline. */
internal fun parseSparklinePoints(body: String): List<Double> = runCatching {
    val result = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
    val closes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).optJSONArray("close")
        ?: return emptyList()
    (0 until closes.length()).mapNotNull { i -> if (closes.isNull(i)) null else closes.getDouble(i) }
}.getOrDefault(emptyList())

internal fun parseStockSearchResults(body: String): List<StockSearchResult> = runCatching {
    val quotes = JSONObject(body).optJSONArray("quotes") ?: return emptyList()
    (0 until quotes.length()).mapNotNull { i ->
        val q = quotes.getJSONObject(i)
        if (q.optString("quoteType") != "EQUITY") return@mapNotNull null
        val symbol = q.optString("symbol").ifEmpty { return@mapNotNull null }
        StockSearchResult(
            symbol = symbol,
            displayName = q.optString("shortname").ifEmpty { q.optString("longname") }.ifEmpty { symbol },
            exchange = q.optString("exchange"),
        )
    }
}.getOrDefault(emptyList())

suspend fun fetchStockQuote(symbol: String): StockQuote? {
    val encoded = URLEncoder.encode(symbol, "UTF-8")
    val body = httpGetText("$YAHOO_CHART_BASE/$encoded?interval=1d&range=1d", YAHOO_HEADERS) ?: return null
    return parseStockQuote(symbol, body, System.currentTimeMillis())
}

suspend fun fetchStockSparkline(symbol: String): List<Double> {
    val encoded = URLEncoder.encode(symbol, "UTF-8")
    val body = httpGetText("$YAHOO_CHART_BASE/$encoded?interval=15m&range=1d", YAHOO_HEADERS) ?: return emptyList()
    return parseSparklinePoints(body)
}

suspend fun fetchStockSearch(query: String): List<StockSearchResult> {
    if (query.isBlank()) return emptyList()
    val encoded = URLEncoder.encode(query, "UTF-8")
    val body = httpGetText("$YAHOO_SEARCH_BASE?q=$encoded&quotesCount=8&newsCount=0", YAHOO_HEADERS) ?: return emptyList()
    return parseStockSearchResults(body)
}

/**
 * `1,287.00` style — two decimals, thousands-grouped, with an optional
 * currency prefix. Pure and placed here (not `:feature:livetiles`) so both
 * the tile face and `:feature:personalize`'s picker sheet can format the
 * same way without a new cross-feature dependency — same reasoning as every
 * other shared sports/cricket helper in this file's neighbors.
 */
fun formatStockPrice(value: Double, currency: String): String {
    val prefix = when (currency) {
        "INR" -> "₹"
        "USD" -> "$"
        else -> ""
    }
    // A sub-1 value at two decimals — a currency pair like INR/USD (≈0.0105) —
    // would round to a meaningless "0.01" and hide any real change entirely;
    // four decimals is the everyday convention for quoting a rate that small.
    val pattern = if (kotlin.math.abs(value) < 1.0 && value != 0.0) "%,.4f" else "%,.2f"
    return "$prefix${pattern.format(value)}"
}

/** `+1.63%` / `-0.42%` — the change-percent line shared by the picker preview and the tile faces. */
fun formatStockChangePercent(changePercent: Double): String {
    val sign = if (changePercent >= 0) "+" else ""
    return "$sign${"%.2f".format(changePercent)}%"
}

private const val MARKET_OPEN_HOUR = 9
private const val MARKET_CLOSE_HOUR = 16

/** How often a stock/commodity tile falls back to when [isMarketHoursNow] is false — no real point polling every minute for a price that provably can't move. */
const val CLOSED_MARKET_REFRESH_MS = 15 * 60_000L

/**
 * A coarse "is it plausibly market hours right now" check — 9am-4pm,
 * Monday-Friday, [calendar]'s own time zone (device local time at the call
 * site). Deliberately a single fixed window, not a per-exchange one: a real
 * NSE/NYSE/LSE session each keeps its own local hours (and its own holiday
 * calendar), which would need a timezone/exchange lookup per symbol this
 * tile has no way to resolve reliably. This exists only to decide how often
 * to poll — never to gate correctness, so an imprecise few minutes at either
 * edge of the window costs nothing.
 */
fun isMarketHoursNow(calendar: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
    val day = calendar.get(java.util.Calendar.DAY_OF_WEEK)
    if (day == java.util.Calendar.SATURDAY || day == java.util.Calendar.SUNDAY) return false
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    return hour in MARKET_OPEN_HOUR until MARKET_CLOSE_HOUR
}

/**
 * The refresh interval a stock/commodity tile should actually use right
 * now: [configuredMs] (whatever [LiveRefreshRate] the user picked) during
 * market hours, else the slower of [configuredMs] and
 * [CLOSED_MARKET_REFRESH_MS] — this only ever slows polling down outside
 * market hours, never speeds it up past what the user chose.
 */
fun effectiveMarketRefreshMs(configuredMs: Long, calendar: java.util.Calendar = java.util.Calendar.getInstance()): Long =
    if (isMarketHoursNow(calendar)) configuredMs else maxOf(configuredMs, CLOSED_MARKET_REFRESH_MS)
