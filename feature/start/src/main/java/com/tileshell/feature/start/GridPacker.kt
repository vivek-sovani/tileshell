package com.tileshell.feature.start

import com.tileshell.core.data.TileSize

/** A tile to be placed, in display order. */
data class TileSpec(val id: String, val size: TileSize)

/** Result of packing: a tile pinned to a grid cell (top-left = col 0, row 0). */
data class TilePlacement(val id: String, val size: TileSize, val col: Int, val row: Int) {
    val cols: Int get() = size.cols
    val rows: Int get() = size.rows
}

/**
 * Dense-packing grid layout (FR-1.1), mirroring CSS `grid-auto-flow: dense` on
 * a [columns]-wide grid (4 by default, user-selectable 4/5/6 — tile footprints
 * stay constant; a larger count just fits more small-tile columns per row).
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

    /**
     * Row stride used to encode an absolute grid cell as a single Int
     * (row * [SLOT_ROW_STRIDE] + col), independent of the current column count
     * (4/5/6) so changing it can't corrupt a persisted cell. Must exceed the
     * max column count with headroom.
     */
    private const val SLOT_ROW_STRIDE = 1000

    fun encodeSlot(col: Int, row: Int): Int = row * SLOT_ROW_STRIDE + col
    fun decodeSlotCol(slot: Int): Int = slot % SLOT_ROW_STRIDE
    fun decodeSlotRow(slot: Int): Int = slot / SLOT_ROW_STRIDE

    /**
     * The sticky (gap-preserving) arrangement allows a gap *within* a row (some
     * columns empty, others occupied) but never a fully empty row — a row no
     * tile's vertical span touches in any column collapses, and every tile below
     * shifts up to close it. [placements] are the tiles' current absolute cells
     * (e.g. every anchored tile, plus whichever tile just moved/resized/left, at
     * its already-resolved new cell); returns only the tiles whose row must
     * change, so a caller can persist just those.
     */
    fun collapseEmptyRows(placements: List<TilePlacement>): Map<String, Int> {
        if (placements.isEmpty()) return emptyMap()
        val maxRow = placements.maxOf { it.row + it.rows }
        val touched = BooleanArray(maxRow)
        for (p in placements) {
            for (r in p.row until p.row + p.rows) touched[r] = true
        }
        // shiftUpTo[r] = how many fully empty rows sit strictly before row r.
        val shiftUpTo = IntArray(maxRow + 1)
        for (r in 0 until maxRow) shiftUpTo[r + 1] = shiftUpTo[r] + if (touched[r]) 0 else 1
        val moved = HashMap<String, Int>()
        for (p in placements) {
            val newRow = p.row - shiftUpTo[p.row]
            if (newRow != p.row) moved[p.id] = encodeSlot(p.col, newRow)
        }
        return moved
    }

    /**
     * Windows-Phone-style gap-preserving layout: a tile with a known cell
     * ([slotOf] non-null) renders exactly there — removing a neighbor never
     * moves it. A tile with no cell yet (never anchored, or its anchored cell
     * no longer fits — e.g. the column count shrank) is placed after every
     * anchored tile's bottom-most row, scanned the same way [pack] scans (top
     * of that region down, left to right) — so a brand-new tile always appends
     * below the existing layout and never backfills an earlier gap.
     */
    fun packSticky(tiles: List<TileSpec>, slotOf: (String) -> Int?, columns: Int = COLUMNS): List<TilePlacement> {
        require(columns > 0) { "columns must be positive" }

        val grid = ArrayList<BooleanArray>()
        fun ensureRows(count: Int) {
            while (grid.size < count) grid.add(BooleanArray(columns))
        }
        fun fits(row: Int, col: Int, w: Int, h: Int): Boolean {
            if (col < 0 || col + w > columns || row < 0) return false
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
        val pending = ArrayList<TileSpec>()
        var frontierRow = 0

        // First pass: place every tile with an anchored cell that still fits.
        for (tile in tiles) {
            val w = tile.size.cols.coerceAtMost(columns)
            val h = tile.size.rows
            val slot = slotOf(tile.id)
            val col = slot?.let { decodeSlotCol(it) }
            val row = slot?.let { decodeSlotRow(it) }
            if (col != null && row != null && fits(row, col, w, h)) {
                occupy(row, col, w, h)
                result.add(TilePlacement(tile.id, tile.size, col, row))
                frontierRow = maxOf(frontierRow, row + h)
            } else {
                pending.add(tile)
            }
        }

        // Second pass: unanchored tiles append after the frontier — new tiles
        // are always pinned at the bottom, never backfilling an earlier gap.
        for (tile in pending) {
            val w = tile.size.cols.coerceAtMost(columns)
            val h = tile.size.rows
            var row = frontierRow
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
}
