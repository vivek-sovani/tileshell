package com.tileshell.core.data

/**
 * Encodes a commodity/currency tile's picked symbol into a blank-package
 * tile's `activityName` column — same no-schema-migration trick as
 * [CountdownTile]. Unlike [StockTile] there's no basket/category/multi-select
 * concept here — gold, silver, a currency pair, etc. are each already a
 * single trackable price, so one tile is always exactly one symbol from
 * [COMMODITY_ITEMS].
 */
object CommodityTile {
    const val ICON_KEY = "commodity"
    private const val PREFIX = "commodity:"

    fun encode(symbol: String, displayName: String): String = "$PREFIX$symbol|$displayName"

    /** Returns (symbol, displayName), or null when not yet picked / malformed. */
    fun decode(activityName: String): Pair<String, String>? {
        if (!activityName.startsWith(PREFIX)) return null
        val parts = activityName.removePrefix(PREFIX).split("|", limit = 2)
        if (parts.size < 2 || parts[0].isEmpty()) return null
        return parts[0] to parts[1]
    }
}
