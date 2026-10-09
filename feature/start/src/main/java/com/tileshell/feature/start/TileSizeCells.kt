package com.tileshell.feature.start

import com.tileshell.core.data.TileSize

/** How many cells across and down the size picker's grid is: every size is a rectangle from its top-left cell. */
internal const val SIZE_PICKER_CELLS = 4

/**
 * The size a tap on cell ([col], [row]) of the picker's grid means: a tile whose bottom-right cell that is
 * (column 2, row 1 → 3 wide, 2 tall), or null when that rectangle isn't a size a tile can take (or isn't offered
 * for this tile). Pure.
 */
internal fun sizeForCell(col: Int, row: Int, allowed: Collection<TileSize>): TileSize? =
    allowed.firstOrNull { it.cols == col + 1 && it.rows == row + 1 }

/** The cell under a touch at ([x], [y]) in a grid of [cell]-px squares, clamped to the grid (a drag may leave it). Pure. */
internal fun cellAtPoint(x: Float, y: Float, cell: Float, cells: Int = SIZE_PICKER_CELLS): Pair<Int, Int> =
    (x / cell).toInt().coerceIn(0, cells - 1) to (y / cell).toInt().coerceIn(0, cells - 1)

/** What a size is called in the picker ("2×2 medium"), the name only when it has one. Pure. */
internal fun sizeLabel(size: TileSize): String {
    val dims = "${size.cols}×${size.rows}"
    val name = when (size) {
        TileSize.SMALL -> "small"
        TileSize.MEDIUM -> "medium"
        TileSize.WIDE -> "wide"
        TileSize.LARGE -> "large"
        TileSize.XLARGE -> "extra large"
        TileSize.BANNER -> "banner"
        TileSize.COLUMN -> "column"
        else -> ""
    }
    return if (name.isEmpty()) dims else "$dims $name"
}
