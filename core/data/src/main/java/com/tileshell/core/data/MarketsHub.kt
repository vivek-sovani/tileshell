package com.tileshell.core.data

/** One symbol on the markets hub's watchlist, as Yahoo Finance names it. */
data class WatchSymbol(val symbol: String, val displayName: String)

/** What a fresh install's watchlist holds: a few big names, gold and the dollar. */
val DEFAULT_WATCHLIST: List<WatchSymbol> = listOf(
    WatchSymbol("RELIANCE.NS", "Reliance Industries"),
    WatchSymbol("TCS.NS", "Tata Consultancy Services"),
    WatchSymbol("HDFCBANK.NS", "HDFC Bank"),
    WatchSymbol("INFY.NS", "Infosys"),
    WatchSymbol("GC=F", "Gold"),
    WatchSymbol("USDINR=X", "USD / INR"),
)

/** Quick adds under the hub's search box, for things the stock search doesn't return. */
val POPULAR_WATCH_SYMBOLS: List<WatchSymbol> = listOf(
    WatchSymbol("GC=F", "Gold"),
    WatchSymbol("SI=F", "Silver"),
    WatchSymbol("CL=F", "Crude oil"),
    WatchSymbol("USDINR=X", "USD / INR"),
    WatchSymbol("BTC-USD", "Bitcoin"),
)

/** An index on the hub's "indices" page. */
data class MarketIndex(val symbol: String, val displayName: String)

val MARKET_INDICES: List<MarketIndex> = listOf(
    MarketIndex("^NSEI", "NIFTY 50"),
    MarketIndex("^BSESN", "SENSEX"),
    MarketIndex("^NSEBANK", "NIFTY Bank"),
    MarketIndex("^CNXIT", "NIFTY IT"),
    MarketIndex("^GSPC", "S&P 500"),
    MarketIndex("^IXIC", "Nasdaq"),
    MarketIndex("^DJI", "Dow Jones"),
)

/** At most this many symbols on the watchlist — each is a request on every refresh. */
const val MAX_WATCHLIST = 30

/**
 * The symbols the "movers" page ranks. Yahoo's own screener needs a login
 * crumb, so the page ranks the curated sector baskets ([STOCK_CATEGORIES])
 * instead — about thirty names a region, the large ones people follow.
 */
fun moverSymbols(region: String): List<StockSymbolRef> =
    STOCK_CATEGORIES.filter { it.region == region }.flatMap { it.symbols }.distinctBy { it.symbol }

/** The biggest risers (or fallers) by percent change; a flat or opposite-signed quote never makes the list. */
fun topMovers(quotes: List<StockQuote>, gainers: Boolean, limit: Int = 6): List<StockQuote> =
    quotes
        .filter { if (gainers) it.changePercent > 0.0 else it.changePercent < 0.0 }
        .sortedBy { if (gainers) -it.changePercent else it.changePercent }
        .take(limit)

/** `symbol<TAB>name` per line; tabs and newlines inside a name are dropped so the format can't be broken. */
fun encodeWatchlist(list: List<WatchSymbol>): String =
    list.joinToString("\n") { "${clean(it.symbol)}\t${clean(it.displayName)}" }

/**
 * The saved watchlist, or null when nothing was ever saved (the caller then
 * shows [DEFAULT_WATCHLIST]). An explicitly emptied list is saved as a single
 * blank line so it can be told apart from "never saved".
 */
fun decodeWatchlist(raw: String?): List<WatchSymbol>? {
    if (raw == null) return null
    return raw.split("\n").mapNotNull { line ->
        val parts = line.split("\t", limit = 2)
        val symbol = parts[0].trim()
        if (symbol.isEmpty()) null else WatchSymbol(symbol, parts.getOrNull(1)?.trim().orEmpty().ifEmpty { symbol })
    }.distinctBy { it.symbol }.take(MAX_WATCHLIST)
}

/** Adds [item] to the end of [list] unless it's already there or the list is full. */
fun watchlistWith(list: List<WatchSymbol>, item: WatchSymbol): List<WatchSymbol> =
    if (list.any { it.symbol == item.symbol } || list.size >= MAX_WATCHLIST) list else list + item

fun watchlistWithout(list: List<WatchSymbol>, symbol: String): List<WatchSymbol> = list.filterNot { it.symbol == symbol }

private val INDIA_INDEX_SYMBOLS = setOf("^NSEI", "^BSESN", "^NSEBANK", "^CNXIT")

/**
 * Whether [symbol]'s market is in session. Yahoo's Indian index tickers carry
 * no `.NS` suffix, which is how [marketSessionFor] finds the NSE's hours, so
 * they'd be judged by New York's; they're checked against an NSE symbol instead.
 */
fun isMarketInSession(symbol: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
    isMarketOpenFor(sessionSymbolFor(symbol), nowMillis)

private fun sessionSymbolFor(symbol: String) = if (symbol in INDIA_INDEX_SYMBOLS) "NIFTY.NS" else symbol

/**
 * How long the markets hub (or tile) waits before refreshing [symbols]: the
 * user's "live data refresh" rate for stocks while any of them is trading,
 * otherwise through to the soonest opening bell — the stock tile's own rule
 * ([nextMarketRefreshDelayMs]), applied to the soonest of the group. Pure.
 */
fun marketsRefreshDelayMs(symbols: List<String>, configuredMs: Long, nowMillis: Long = System.currentTimeMillis()): Long =
    symbols.minOfOrNull { nextMarketRefreshDelayMs(sessionSymbolFor(it), configuredMs, nowMillis) } ?: configuredMs

/** "stocks", "commodities" or "currencies": the filter a watchlist row falls under. */
fun watchKind(symbol: String): String = when {
    symbol.endsWith("=F") -> "commodities"
    symbol.endsWith("=X") -> "currencies"
    symbol.contains('-') && !symbol.contains('.') -> "crypto"
    else -> "stocks"
}

private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim()
