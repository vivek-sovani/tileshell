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
     * Windows-Phone-style inline folder expansion: a purely render-time
     * transform applied *after* the normal pack/packSticky computation, so it
     * works identically in either arrangement mode without touching how either
     * is computed. [expandedId]'s own placement is left exactly where it is
     * (the caller renders it as a collapse affordance instead of its usual
     * face); [children] are laid out as their own dense block starting
     * immediately below it (row = its bottom edge), and every placement at or
     * below that row shifts down by the block's height to make room. Nothing
     * here is persisted — collapsing is just calling this with a null/absent
     * [expandedId] and getting [placements] back unchanged.
     */
    fun expandFolderInline(
        placements: List<TilePlacement>,
        expandedId: String,
        children: List<TileSpec>,
        columns: Int = COLUMNS,
    ): List<TilePlacement> {
        val expanded = placements.firstOrNull { it.id == expandedId } ?: return placements
        if (children.isEmpty()) return placements
        val insertAtRow = expanded.row + expanded.rows
        val childPlacements = pack(children, columns)
        val childRows = rowCount(childPlacements)
        val shifted = placements.map { p ->
            if (p.id != expandedId && p.row >= insertAtRow) p.copy(row = p.row + childRows) else p
        }
        val offsetChildren = childPlacements.map { it.copy(row = it.row + insertAtRow) }
        return shifted + offsetChildren
    }

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

    /**
     * Sticky mode's shared "where would this land" computation: [movedId]
     * (sized [size]) targets absolute cell ([targetCol], [targetRow]);
     * returns its own resolved cell plus every other anchored tile in
     * [anchored] that has to move to make room, plus any fully-empty row
     * that leaves behind, collapsed ([collapseEmptyRows]). A blocked tile is
     * nudged sideways within its own row-band first, if the row has a free
     * gap of its width somewhere else — so dropping onto a tile in a row
     * that isn't full just slides that tile over, instead of always bumping
     * it down into a whole new row. Only when no such gap exists does it
     * fall back to a straight push straight down (same column), cascading
     * until nothing overlaps. Pure — the caller supplies every other tile's
     * current cell via [anchored] (never including [movedId] itself) — so
     * the exact same logic drives both the actual write (a resize or a
     * drag-drop's commit) and a live drag-preview recomputed on every
     * pointer move with no DB write.
     */
    fun stickyPlacement(
        anchored: List<TilePlacement>,
        movedId: String,
        size: TileSize,
        targetCol: Int,
        targetRow: Int,
        columns: Int = COLUMNS,
    ): Map<String, Int> {
        val w = size.cols.coerceAtMost(columns)
        val h = size.rows
        val effectiveCol = targetCol.coerceIn(0, (columns - w).coerceAtLeast(0))
        val effectiveSlot = encodeSlot(effectiveCol, targetRow)

        data class Box(val id: String, var col: Int, var row: Int, val w: Int, val h: Int)
        fun overlaps(a: Box, b: Box) =
            a.col < b.col + b.w && b.col < a.col + a.w && a.row < b.row + b.h && b.row < a.row + a.h

        val moving = Box(movedId, effectiveCol, targetRow, w, h)
        val boxes = anchored.map { Box(it.id, it.col, it.row, it.cols.coerceAtMost(columns), it.rows) }
        val fixed = listOf(moving) + boxes

        // The nearest free column for [box] at its own row/height, checked
        // against every other box's *current* (possibly already-shifted)
        // position — nearest first so a small nudge is preferred over a
        // far one, ties broken toward the lower column for determinism.
        fun freeColumnNear(box: Box, others: List<Box>): Int? {
            val candidates = (0..(columns - box.w)).sortedBy { kotlin.math.abs(it - box.col) }
            for (c in candidates) {
                if (c == box.col) continue
                val trial = Box(box.id, c, box.row, box.w, box.h)
                if (others.none { it !== box && overlaps(it, trial) }) return c
            }
            return null
        }

        val displaced = mutableMapOf<String, Int>()
        var settled = false
        var guard = 0
        while (!settled && guard++ <= boxes.size * 4 + 4) {
            settled = true
            for (box in boxes) {
                val others = fixed.filter { it !== box }
                val blockers = others.filter { overlaps(it, box) }
                if (blockers.isEmpty()) continue
                val newCol = freeColumnNear(box, others)
                if (newCol != null) {
                    box.col = newCol
                    displaced[box.id] = encodeSlot(box.col, box.row)
                    settled = false
                } else {
                    val newRow = blockers.maxOf { it.row + it.h }
                    if (newRow > box.row) {
                        box.row = newRow
                        displaced[box.id] = encodeSlot(box.col, box.row)
                        settled = false
                    }
                }
            }
        }

        val projected = anchored.map { p ->
            val slot = displaced[p.id]
            if (slot != null) p.copy(col = decodeSlotCol(slot), row = decodeSlotRow(slot)) else p
        } + TilePlacement(movedId, size, effectiveCol, targetRow)
        val collapse = collapseEmptyRows(projected)
        val ownFinal = collapse[movedId] ?: effectiveSlot
        return displaced + collapse + (movedId to ownFinal)
    }
}
