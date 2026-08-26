package com.tileshell.feature.start

/** Persisted Quick Panel tile order + size, keyed by a tile's stable id (see `QuickPanelTileSpec.id`). */
internal data class QuickPanelTileLayout(
    val order: List<String>,
    val sizes: Map<String, QuickPanelTileSize>,
)

/**
 * Reorders [liveIds] (already in their natural, device-state-derived order) so
 * ids present in the persisted [order] sort first, in that order; any live id
 * absent from [order] (e.g. a tile that only recently became available, like
 * "allow access" disappearing once WRITE_SETTINGS is granted) appends at the
 * end, in its natural order. A persisted id with no matching live tile (e.g. one
 * that's conditionally absent right now, or dropped in a future release) is
 * silently ignored — same "dead entry, never actively pruned" idiom as
 * `HiddenApps`/`edgeStripApps` tolerating a since-uninstalled package.
 */
internal fun applyQuickPanelOrder(liveIds: List<String>, order: List<String>): List<String> {
    val index = order.withIndex().associate { (i, id) -> id to i }
    return liveIds.sortedBy { index[it] ?: Int.MAX_VALUE }
}

/**
 * Moves [dragId] to sit where [targetId] currently is — same splice-and-reinsert
 * shape as `feed/WidgetSlot.kt`'s `reorderWidgets`, keyed by tile id string
 * instead of widget int id (no stacking concept here, so it's a plain
 * remove+reinsert). No-op when either id is absent or they're equal.
 */
internal fun reorderQuickPanelTiles(order: List<String>, dragId: String, targetId: String): List<String> {
    if (dragId == targetId) return order
    val di = order.indexOf(dragId)
    val ti = order.indexOf(targetId)
    if (di < 0 || ti < 0) return order
    val out = order.toMutableList()
    out.removeAt(di)
    out.add(ti.coerceAtMost(out.size), dragId)
    return out
}

/** Snaps a live drag width (in column units) to the nearer of the two sizes. */
internal fun settleQuickPanelTileSize(liveCols: Float): QuickPanelTileSize =
    if (liveCols >= 1.5f) QuickPanelTileSize.WIDE else QuickPanelTileSize.SQUARE

/**
 * Decodes the persisted `"id:cols"` token list (see `LauncherSettings.quickPanelTileSizes`)
 * into a lookup, keyed by tile id — a malformed token (bad int, unknown cols value)
 * is silently dropped rather than crashing, same tolerant posture as `WidgetCodec`.
 * An id absent from the map means the default [QuickPanelTileSize.SQUARE].
 */
internal fun decodeQuickPanelSizes(tokens: List<String>): Map<String, QuickPanelTileSize> =
    tokens.mapNotNull { token ->
        val sep = token.indexOf(':')
        if (sep <= 0) return@mapNotNull null
        val id = token.substring(0, sep)
        val cols = token.substring(sep + 1).toIntOrNull() ?: return@mapNotNull null
        val size = QuickPanelTileSize.entries.find { it.cols == cols } ?: return@mapNotNull null
        id to size
    }.toMap()

/**
 * Encodes a size lookup back to `"id:cols"` tokens — an id mapped to the default
 * [QuickPanelTileSize.SQUARE] is omitted (matches [decodeQuickPanelSizes]'s "absent
 * means square" contract, keeping the persisted list only as long as it needs to be).
 */
internal fun encodeQuickPanelSizes(sizes: Map<String, QuickPanelTileSize>): List<String> =
    sizes.filterValues { it != QuickPanelTileSize.SQUARE }.map { (id, size) -> "$id:${size.cols}" }

/**
 * Greedy wrap-pack of [items] into rows of at most [columns] wide, never
 * splitting an item across rows — each item's column span comes from [colsOf].
 * Generic over the item type so the same logic is testable against plain ids
 * without depending on `QuickPanelTileSpec` (which carries a Compose lambda).
 */
internal fun <T> packQuickPanelRows(items: List<T>, columns: Int, colsOf: (T) -> Int): List<List<T>> {
    val rows = mutableListOf<MutableList<T>>()
    var rowCols = 0
    for (item in items) {
        val c = colsOf(item).coerceIn(1, columns)
        if (rows.isEmpty() || rowCols + c > columns) {
            rows.add(mutableListOf())
            rowCols = 0
        }
        rows.last().add(item)
        rowCols += c
    }
    return rows
}
