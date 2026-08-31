package com.tileshell.core.data

/** One trackable commodity/currency pair a [CommodityTile] can follow. */
data class CommodityItem(val symbol: String, val displayName: String, val category: String)

/**
 * Curated, live-verified list of metals, energy futures, and major currency
 * pairs — same reasoning as [STOCK_CATEGORIES]/[CRICKET_TEAMS]: no free
 * "list every tradable commodity" API exists, so this is hand-picked and
 * checked directly against Yahoo Finance's chart endpoint. Futures ([symbol]
 * ending `=F`) and currency pairs (`=X`) both resolve on the same endpoint
 * [fetchStockQuote] already uses for stocks — see [fetchCommoditySparkline]
 * for the one real difference (futures need a wider intraday range).
 */
val COMMODITY_ITEMS: List<CommodityItem> = listOf(
    CommodityItem("GC=F", "Gold", category = "metals"),
    CommodityItem("SI=F", "Silver", category = "metals"),
    CommodityItem("PL=F", "Platinum", category = "metals"),
    CommodityItem("HG=F", "Copper", category = "metals"),
    CommodityItem("CL=F", "Crude Oil (WTI)", category = "energy"),
    CommodityItem("BZ=F", "Brent Crude", category = "energy"),
    CommodityItem("NG=F", "Natural Gas", category = "energy"),
    CommodityItem("USDINR=X", "USD / INR", category = "currencies"),
    CommodityItem("EURUSD=X", "EUR / USD", category = "currencies"),
    CommodityItem("GBPUSD=X", "GBP / USD", category = "currencies"),
    CommodityItem("USDJPY=X", "USD / JPY", category = "currencies"),
    CommodityItem("USDCNY=X", "USD / CNY", category = "currencies"),
    CommodityItem("AUDUSD=X", "AUD / USD", category = "currencies"),
)

/** Display order for [CommodityItem.category] sections in the picker. */
val COMMODITY_CATEGORY_ORDER: List<String> = listOf("metals", "energy", "currencies")

fun commodityItemFor(symbol: String): CommodityItem? = COMMODITY_ITEMS.find { it.symbol == symbol }

/** A futures contract (`=F`) has no `range=1d` intraday data on Yahoo's endpoint (verified live) — [fetchCommoditySparkline] widens the window for it. */
fun isFuturesSymbol(symbol: String): Boolean = symbol.endsWith("=F")

/** One ISO 4217 currency the picker can offer as a base or target. */
data class CurrencyCode(val code: String, val displayName: String)

/**
 * Major currencies the picker's "choose your own pair" flow offers as base
 * or target — any base/target combination resolves on Yahoo's endpoint via
 * [currencyPairTicker] (verified live for several combinations beyond the
 * fixed [COMMODITY_ITEMS] presets, e.g. `INRUSD=X`, `EURINR=X`, `GBPJPY=X`),
 * so unlike [STOCK_CATEGORIES] this genuinely is "any pair from this list of
 * currencies," not a curated list of pairs.
 */
val CURRENCY_CODES: List<CurrencyCode> = listOf(
    CurrencyCode("USD", "US Dollar"),
    CurrencyCode("EUR", "Euro"),
    CurrencyCode("GBP", "British Pound"),
    CurrencyCode("JPY", "Japanese Yen"),
    CurrencyCode("INR", "Indian Rupee"),
    CurrencyCode("CNY", "Chinese Yuan"),
    CurrencyCode("AUD", "Australian Dollar"),
    CurrencyCode("CAD", "Canadian Dollar"),
    CurrencyCode("CHF", "Swiss Franc"),
    CurrencyCode("SGD", "Singapore Dollar"),
    CurrencyCode("AED", "UAE Dirham"),
    CurrencyCode("HKD", "Hong Kong Dollar"),
)

/** `"USDINR=X"` — 1 unit of [base] priced in [target], Yahoo's own pair-ticker convention (verified live for every combination the picker can produce). */
fun currencyPairTicker(base: String, target: String): String = "$base$target=X"

/** `"USD / INR"` — matches the wording [COMMODITY_ITEMS]' own preset currency pairs already use. */
fun currencyPairLabel(base: String, target: String): String = "$base / $target"
