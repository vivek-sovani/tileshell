package com.tileshell.core.data

/**
 * Encodes a stock tile's selection into a blank-package tile's `activityName`
 * column — same no-schema-migration trick as [CountdownTile]/[SportsTile]. A
 * tile follows one stock ([Selection.Single]), a curated sector basket
 * ([Selection.Category], see [STOCK_CATEGORIES]), or a user-built custom list
 * ([Selection.MultiStock]) — the tile face branches on which at render time.
 * A category or multi-stock tile's front face tracks a sector/broad-market
 * index (there's no single member that represents the whole group), showing
 * one member's own price+sparkline at [com.tileshell.core.data.TileSize.LARGE]
 * — one member everywhere smaller); a single-stock tile is just that stock's
 * own price+sparkline throughout.
 */
object StockTile {
    const val ICON_KEY = "stock"
    private const val PREFIX = "stock:"
    private const val KIND_SINGLE = "single"
    private const val KIND_CATEGORY = "category"
    private const val KIND_MULTI = "multi"

    sealed class Selection {
        data class Single(val symbol: String, val displayName: String) : Selection()
        data class Category(val categoryId: String, val displayName: String) : Selection()
        data class MultiStock(val symbols: List<Pair<String, String>>, val displayName: String) : Selection()
    }

    fun encodeSingle(symbol: String, displayName: String): String = "$PREFIX$KIND_SINGLE|$symbol|$displayName"

    fun encodeCategory(categoryId: String, displayName: String): String = "$PREFIX$KIND_CATEGORY|$categoryId|$displayName"

    fun encodeMultiStock(symbols: List<Pair<String, String>>, displayName: String): String {
        val packed = symbols.joinToString(";") { (symbol, name) -> "$symbol:$name" }
        return "$PREFIX$KIND_MULTI|$packed|$displayName"
    }

    fun decode(activityName: String): Selection? {
        if (!activityName.startsWith(PREFIX)) return null
        val parts = activityName.removePrefix(PREFIX).split("|", limit = 3)
        if (parts.size < 3) return null
        val (kind, id, label) = parts
        if (id.isEmpty()) return null
        return when (kind) {
            KIND_SINGLE -> Selection.Single(id, label)
            KIND_CATEGORY -> Selection.Category(id, label)
            KIND_MULTI -> decodeMultiStock(id, label)
            else -> null
        }
    }

    private fun decodeMultiStock(packed: String, label: String): Selection.MultiStock? {
        val symbols = packed.split(";").mapNotNull { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
        }
        return if (symbols.isEmpty()) null else Selection.MultiStock(symbols, label)
    }
}

/**
 * `"Apple"` / `"Apple & TCS"` / `"Apple, TCS +2 more"` — a short label for a
 * user-built custom list, generated from the picked names rather than asking
 * for a separate title (one fewer step in the picker). Pure so the exact
 * wording at each count is unit-testable.
 */
fun multiStockLabel(names: List<String>): String = when (names.size) {
    0 -> "custom list"
    1 -> names[0]
    2 -> "${names[0]} & ${names[1]}"
    else -> "${names[0]}, ${names[1]} +${names.size - 2} more"
}
