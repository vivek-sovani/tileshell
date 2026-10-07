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

/** The watchlist's kinds, in the order the hub's filters and tiles use them. */
val WATCH_KINDS: List<String> = listOf("stocks", "commodities", "currencies", "crypto")

/** The kinds the watchlist actually has something in. */
fun watchKinds(watch: List<WatchSymbol>): List<String> = WATCH_KINDS.filter { kind -> watch.any { watchKind(it.symbol) == kind } }

/** How many symbols a markets tile can show at most. */
const val MAX_TILE_SYMBOLS = 8

/** How many a tile shows when none of its kind is marked: enough that a freshly pinned tile isn't empty. */
const val DEFAULT_TILE_SYMBOLS = 3

/**
 * What a stocks / commodities / currencies / crypto tile shows: the watchlist
 * entries of [kind] the user marked "on tile", in watchlist order; with none
 * marked, the first few of that kind. Marks for symbols since removed from the
 * watchlist are ignored. Pure.
 */
fun tileSymbols(kind: String, watch: List<WatchSymbol>, marked: Collection<String>): List<WatchSymbol> {
    val ofKind = watch.filter { watchKind(it.symbol) == kind }
    val picked = ofKind.filter { it.symbol in marked }
    return (picked.ifEmpty { ofKind.take(DEFAULT_TILE_SYMBOLS) }).take(MAX_TILE_SYMBOLS)
}

/** What the markets (indices) tile shows: the marked indices, or the NIFTY 50 when none (or nothing was ever saved, [marked] null). */
fun tileIndices(marked: Collection<String>?): List<MarketIndex> =
    MARKET_INDICES.filter { marked != null && it.symbol in marked }.ifEmpty { MARKET_INDICES.take(1) }.take(MAX_TILE_SYMBOLS)

/** Marks [symbol] if it isn't, unmarks it if it is. */
fun toggleMarked(marked: List<String>, symbol: String): List<String> = if (symbol in marked) marked - symbol else marked + symbol

/** A short name for a tile row: the company name when it fits, else the ticker without its exchange suffix. */
fun tileLabel(item: WatchSymbol, maxChars: Int = 12): String =
    if (item.displayName.length <= maxChars) item.displayName
    else item.symbol.removeSuffix(".NS").removeSuffix(".BO").removeSuffix("=X").removeSuffix("=F").take(maxChars)

/**
 * A markets tile's kind in its `activityName`: "indices" (the hub's own tile,
 * also what a blank name means), "stocks", "commodities", "currencies" or
 * "crypto". Blank-package tile, no schema change, like the other hub tiles.
 */
object MarketsTile {
    const val ICON_KEY = "markets"
    const val INDICES = "indices"
    private const val PREFIX = "markets:"

    fun encode(kind: String): String = PREFIX + kind

    fun decode(activityName: String): String {
        val kind = activityName.removePrefix(PREFIX).takeIf { activityName.startsWith(PREFIX) } ?: return INDICES
        return if (kind in WATCH_KINDS) kind else INDICES
    }
}

