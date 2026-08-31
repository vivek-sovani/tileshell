package com.tileshell.core.data

import java.net.URLEncoder

private const val YAHOO_CHART_BASE_COMMODITY = "https://query1.finance.yahoo.com/v8/finance/chart"
private val COMMODITY_HEADERS = mapOf(
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
)

/**
 * The intraday close series for a commodity/currency tile's sparkline. Reuses
 * [parseSparklinePoints] (the same parser [fetchStockSparkline] uses — the
 * chart JSON shape is identical). The one real difference from stocks: a
 * futures contract has no `range=1d` intraday data on Yahoo's endpoint
 * (verified live), so it widens to a 5-day window at the same 15-minute
 * granularity; a currency pair trades enough hours to keep the 1-day window.
 */
suspend fun fetchCommoditySparkline(symbol: String): List<Double> {
    val range = if (isFuturesSymbol(symbol)) "5d" else "1d"
    val encoded = URLEncoder.encode(symbol, "UTF-8")
    val body = httpGetText("$YAHOO_CHART_BASE_COMMODITY/$encoded?interval=15m&range=$range", COMMODITY_HEADERS) ?: return emptyList()
    return parseSparklinePoints(body)
}

/** A commodity/currency quote — same shape and fetch path as [fetchStockQuote], just a semantic alias so call sites read clearly. */
suspend fun fetchCommodityQuote(symbol: String): StockQuote? = fetchStockQuote(symbol)
