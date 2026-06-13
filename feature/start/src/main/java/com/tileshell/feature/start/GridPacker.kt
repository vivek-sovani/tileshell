package com.tileshell.feature.start

/**
 * Tile footprints on the 4-column grid (CLAUDE.md normative values):
 * small 1×1, medium 2×2, wide 4×2, large 4×4.
 */
enum class TileSize(val cols: Int, val rows: Int) {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    LARGE(4, 4),
}

/** A tile to be placed, in display order. */
data class TileSpec(val id: String, val size: TileSize)

/** Result of packing: a tile pinned to a grid cell (top-left = col 0, row 0). */
data class TilePlacement(val id: String, val size: TileSize, val col: Int, val row: Int) {
    val cols: Int get() = size.cols
    val rows: Int get() = size.rows
}

/**
 * Dense-packing grid layout (FR-1.1), mirroring CSS `grid-auto-flow: dense` on
 * a fixed 4-column grid.
 *
 * Tiles are placed in order; for each tile the cursor scans the grid from the
 * top-left, row by row then column by column, and drops the tile in the first
 * cell where its whole footprint fits. Because the scan always restarts at the
 * top, smaller tiles later in the order back-fill holes left by earlier larger
 * tiles. The function is pure and deterministic: identical input always yields
 * identical output, which is what keeps reorders stable.
 */
object GridPacker {

    const val COLUMNS = 4

    fun pack(tiles: List<TileSpec>, columns: Int = COLUMNS): List<TilePlacement> {
        require(columns > 0) { "columns must be positive" }

        // Row-major occupancy, grown on demand.
        val grid = ArrayList<BooleanArray>()
        fun ensureRows(count: Int) {
            while (grid.size < count) grid.add(BooleanArray(columns))
        }
        fun fits(row: Int, col: Int, w: Int, h: Int): Boolean {
            if (col + w > columns) return false
            ensureRows(row + h)
            for (r in row until row + h) {
                for (c in col until col + w) {
                    if (grid[r][c]) return false
                }
            }
            return true
        }
        fun occupy(row: Int, col: Int, w: Int, h: Int) {
            ensureRows(row + h)
            for (r in row until row + h) {
                for (c in col until col + w) grid[r][c] = true
            }
        }

        val result = ArrayList<TilePlacement>(tiles.size)
        for (tile in tiles) {
            // A tile wider than the grid is clamped so it can always be placed.
            val w = tile.size.cols.coerceAtMost(columns)
            val h = tile.size.rows
            var row = 0
            while (true) {
                var col = 0
                var placed = false
                while (col <= columns - w) {
                    if (fits(row, col, w, h)) {
                        occupy(row, col, w, h)
                        result.add(TilePlacement(tile.id, tile.size, col, row))
                        placed = true
                        break
                    }
                    col++
                }
                if (placed) break
                row++
            }
        }
        return result
    }

    /** Total rows the packed layout spans (0 when empty). */
    fun rowCount(placements: List<TilePlacement>): Int =
        placements.maxOfOrNull { it.row + it.rows } ?: 0
}
