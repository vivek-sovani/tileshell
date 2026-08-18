package com.tileshell.feature.start

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPackerTest {

    private fun specs(vararg sizes: TileSize) =
        sizes.mapIndexed { i, s -> TileSpec("t$i", s) }

    /** Deterministic mixed-size set: smalls broken up by mediums, the odd wide. */
    private fun demoTiles(count: Int): List<TileSpec> = List(count) { i ->
        val size = when {
            i % 7 == 3 -> TileSize.WIDE
            i % 3 == 0 -> TileSize.MEDIUM
            else -> TileSize.SMALL
        }
        TileSpec(id = "t$i", size = size)
    }

    private fun TilePlacement.at() = Triple(id, col, row)

    // ---- basic packing + gap back-fill ----------------------------------

    @Test
    fun `two mediums sit side by side filling the top two rows`() {
        val p = GridPacker.pack(specs(TileSize.MEDIUM, TileSize.MEDIUM))
        assertEquals(0 to 0, p[0].col to p[0].row)
        assertEquals(2 to 0, p[1].col to p[1].row)
        assertEquals(2, GridPacker.rowCount(p))
    }

    @Test
    fun `later small back-fills a hole left by a wide tile`() {
        // M, M fill rows 0-1. S takes (0,2). WIDE cannot fit until row 3.
        // The final S back-fills the (1,2) hole the wide tile skipped over.
        val p = GridPacker.pack(
            specs(TileSize.MEDIUM, TileSize.MEDIUM, TileSize.SMALL, TileSize.WIDE, TileSize.SMALL),
        )
        assertEquals(listOf(
            "t0" to (0 to 0),   // medium
            "t1" to (2 to 0),   // medium
            "t2" to (0 to 2),   // small
            "t3" to (0 to 3),   // wide, pushed below the small
            "t4" to (1 to 2),   // small back-fills the hole at row 2
        ), p.map { it.id to (it.col to it.row) })
        assertEquals(5, GridPacker.rowCount(p))
    }

    @Test
    fun `small after wide back-fills the wide's own top row`() {
        // S at (0,0); WIDE needs a clear 4-wide band so lands at row 1;
        // the next small back-fills (1,0).
        val p = GridPacker.pack(specs(TileSize.SMALL, TileSize.WIDE, TileSize.SMALL))
        assertEquals("t0" to (0 to 0), p[0].id to (p[0].col to p[0].row))
        assertEquals("t1" to (0 to 1), p[1].id to (p[1].col to p[1].row))
        assertEquals("t2" to (1 to 0), p[2].id to (p[2].col to p[2].row))
    }

    // ---- wide row spans -------------------------------------------------

    @Test
    fun `wide spans all four columns and two rows`() {
        val p = GridPacker.pack(specs(TileSize.WIDE)).single()
        assertEquals(0, p.col)
        assertEquals(0, p.row)
        assertEquals(4, p.cols)
        assertEquals(2, p.rows)
        assertEquals(2, GridPacker.rowCount(p.let(::listOf)))
    }

    @Test
    fun `wide drops below a small then a later small back-fills its band`() {
        val p = GridPacker.pack(specs(TileSize.WIDE, TileSize.SMALL))
        assertEquals(0 to 0, p[0].col to p[0].row) // wide takes the top band
        assertEquals(0 to 2, p[1].col to p[1].row) // small drops below the wide
        assertEquals(3, GridPacker.rowCount(p))
    }

    @Test
    fun `wide cannot share a row that is partially occupied`() {
        // SMALL at (0,0) blocks col 0 of row 0, so the wide must go to row 1.
        val p = GridPacker.pack(specs(TileSize.SMALL, TileSize.WIDE))
        assertEquals(0 to 1, p[1].col to p[1].row)
    }

    // ---- new size presets (gesture drag resize) -------------------------

    @Test
    fun `wide_small and tall pack side by side and stacked respectively`() {
        val p = GridPacker.pack(specs(TileSize.WIDE_SMALL, TileSize.TALL))
        val wideSmall = p.first { it.id == "t0" }
        assertEquals(2, wideSmall.cols)
        assertEquals(1, wideSmall.rows)
        assertEquals(0 to 0, wideSmall.col to wideSmall.row)
        val tall = p.first { it.id == "t1" }
        assertEquals(1, tall.cols)
        assertEquals(2, tall.rows)
        // Fits into the remaining (2,0) column, back-filling beside wide_small
        // rather than dropping below it.
        assertEquals(2, tall.col)
        assertEquals(0, tall.row)
    }

    @Test
    fun `wide_medium and tall_medium never overlap anything in a mixed set`() {
        val tiles = listOf(
            TileSpec("a", TileSize.WIDE_MEDIUM),
            TileSpec("b", TileSize.TALL_MEDIUM),
            TileSpec("c", TileSize.SMALL),
            TileSpec("d", TileSize.WIDE_SMALL),
            TileSpec("e", TileSize.TALL),
        )
        val p = GridPacker.pack(tiles)
        val rows = GridPacker.rowCount(p)
        val occupied = Array(rows) { BooleanArray(GridPacker.COLUMNS) }
        for (placement in p) {
            for (r in placement.row until placement.row + placement.rows) {
                for (c in placement.col until placement.col + placement.cols) {
                    assertFalse("tiles overlap at ($c,$r)", occupied[r][c])
                    occupied[r][c] = true
                }
            }
        }
        assertEquals(tiles.size, p.size)
    }

    @Test
    fun `xlarge consumes a full 4x4 row-band on a 4-column grid`() {
        val p = GridPacker.pack(specs(TileSize.XLARGE)).single()
        assertEquals(0, p.col)
        assertEquals(0, p.row)
        assertEquals(4, p.cols)
        assertEquals(4, p.rows)
        assertEquals(4, GridPacker.rowCount(p.let(::listOf)))
    }

    @Test
    fun `xlarge is clamped to the column count, never overflowing a 4-column grid`() {
        // XLARGE.cols == 4 == COLUMNS, so no clamping is actually needed here —
        // this guards the coerceAtMost(columns) path regardless.
        val p = GridPacker.pack(specs(TileSize.XLARGE), columns = 4).single()
        assertTrue(p.col + p.cols <= 4)
    }

    @Test
    fun `new presets anchor at their stored cell in sticky mode too`() {
        val tiles = specs(TileSize.XLARGE, TileSize.WIDE_MEDIUM)
        val slots = mapOf(
            "t0" to GridPacker.encodeSlot(0, 0),
            "t1" to GridPacker.encodeSlot(0, 5),
        )
        val p = GridPacker.packSticky(tiles, slots::get)
        val xlarge = p.first { it.id == "t0" }
        assertEquals(0 to 0, xlarge.col to xlarge.row)
        assertEquals(4, xlarge.cols)
        assertEquals(4, xlarge.rows)
        val wideMedium = p.first { it.id == "t1" }
        assertEquals(0 to 5, wideMedium.col to wideMedium.row)
        assertEquals(3, wideMedium.cols)
        assertEquals(2, wideMedium.rows)
    }

    // ---- determinism / reorder stability --------------------------------

    @Test
    fun `packing is deterministic for identical input`() {
        val input = specs(
            TileSize.MEDIUM, TileSize.SMALL, TileSize.WIDE,
            TileSize.WIDE, TileSize.SMALL, TileSize.MEDIUM,
        )
        assertEquals(GridPacker.pack(input), GridPacker.pack(input))
    }

    @Test
    fun `swapping two tiles changes the resulting placements`() {
        val a = specs(TileSize.WIDE, TileSize.SMALL, TileSize.MEDIUM)
        val b = listOf(a[1], a[0], a[2]) // swap first two
        assertFalse(
            "reordering should produce a different packing",
            GridPacker.pack(a).map { it.col to it.row } ==
                GridPacker.pack(b).map { it.col to it.row },
        )
    }

    // ---- structural invariants over a large mixed set -------------------

    @Test
    fun `no overlaps and everything stays within four columns for 60 tiles`() {
        val placements = GridPacker.pack(demoTiles(60))
        val rows = GridPacker.rowCount(placements)
        val occupied = Array(rows) { BooleanArray(GridPacker.COLUMNS) }
        for (p in placements) {
            assertTrue("tile ${p.id} exceeds 4 columns", p.col + p.cols <= GridPacker.COLUMNS)
            assertTrue("tile ${p.id} has negative origin", p.col >= 0 && p.row >= 0)
            for (r in p.row until p.row + p.rows) {
                for (c in p.col until p.col + p.cols) {
                    assertFalse("tiles overlap at ($c,$r)", occupied[r][c])
                    occupied[r][c] = true
                }
            }
        }
        assertEquals(60, placements.size)
    }

    // ---- slot encode/decode ----------------------------------------------

    @Test
    fun `slot encode-decode round-trips and is columns-invariant`() {
        val slot = GridPacker.encodeSlot(col = 3, row = 7)
        assertEquals(3, GridPacker.decodeSlotCol(slot))
        assertEquals(7, GridPacker.decodeSlotRow(slot))
    }

    // ---- sticky (gap-preserving) packing ----------------------------------

    @Test
    fun `anchored tile renders exactly at its stored cell regardless of order`() {
        val tiles = specs(TileSize.SMALL, TileSize.SMALL)
        val slots = mapOf("t0" to GridPacker.encodeSlot(3, 5))
        val p = GridPacker.packSticky(tiles, slots::get)
        val anchored = p.first { it.id == "t0" }
        assertEquals(3, anchored.col)
        assertEquals(5, anchored.row)
    }

    @Test
    fun `removing an anchored tile leaves a gap the others do not fill`() {
        // Two anchored tiles with a gap between them at (1,0); a third,
        // never-anchored tile must NOT back-fill that gap — it appends after
        // the frontier row instead (real-WP: gaps only close via an explicit drag).
        val tiles = specs(TileSize.SMALL, TileSize.SMALL, TileSize.SMALL)
        val slots = mapOf("t0" to GridPacker.encodeSlot(0, 0), "t1" to GridPacker.encodeSlot(2, 0))
        val p = GridPacker.packSticky(tiles, slots::get)
        val unanchored = p.first { it.id == "t2" }
        assertFalse("must not backfill the (1,0) gap", unanchored.col == 1 && unanchored.row == 0)
        assertEquals(1, unanchored.row) // appended after the anchored tiles' row
    }

    @Test
    fun `unanchored tiles append after the frontier row, never above it`() {
        val tiles = specs(TileSize.WIDE, TileSize.SMALL, TileSize.SMALL)
        val slots = mapOf("t0" to GridPacker.encodeSlot(0, 4)) // anchored far down the grid
        val p = GridPacker.packSticky(tiles, slots::get)
        val others = p.filter { it.id != "t0" }
        assertTrue("new tiles must land at/after the anchored tile's bottom row", others.all { it.row >= 6 })
    }

    @Test
    fun `an anchored tile that no longer fits the column count re-flows instead of overlapping`() {
        // Anchored at col 3 with a MEDIUM (2-wide) footprint: fits at columns=5
        // but overflows at columns=4 — must fall back to auto-placement, not overlap.
        val tiles = specs(TileSize.MEDIUM, TileSize.SMALL)
        val slots = mapOf("t0" to GridPacker.encodeSlot(3, 0))
        val p = GridPacker.packSticky(tiles, slots::get, columns = 4)
        val t0 = p.first { it.id == "t0" }
        assertTrue("must fit within 4 columns", t0.col + t0.cols <= 4)
    }

    // ---- sticky live drag-preview (push-down) computation ------------------

    @Test
    fun `dropping onto a free cell moves only the dropped tile`() {
        val anchored = listOf(TilePlacement("a", TileSize.SMALL, 0, 0))
        val moved = GridPacker.stickyPlacement(
            anchored = anchored, movedId = "b", size = TileSize.SMALL, targetCol = 2, targetRow = 0, columns = 4,
        )
        assertEquals(setOf("b"), moved.keys)
        assertEquals(GridPacker.encodeSlot(2, 0), moved.getValue("b"))
    }

    @Test
    fun `dropping onto an occupied cell nudges the occupant sideways when its row has room`() {
        // 4 columns wide, only column 0 taken — "a" has three free columns to
        // slide into, so it must NOT grow the grid downward at all.
        val anchored = listOf(TilePlacement("a", TileSize.SMALL, 0, 0))
        val moved = GridPacker.stickyPlacement(
            anchored = anchored, movedId = "b", size = TileSize.SMALL, targetCol = 0, targetRow = 0, columns = 4,
        )
        assertEquals(GridPacker.encodeSlot(0, 0), moved.getValue("b"))
        assertEquals("must stay in the same row", 0, GridPacker.decodeSlotRow(moved.getValue("a")))
        assertEquals("nearest free column", 1, GridPacker.decodeSlotCol(moved.getValue("a")))
    }

    @Test
    fun `dropping onto an occupied cell pushes straight down when its row is already full`() {
        val anchored = listOf(
            TilePlacement("a", TileSize.SMALL, 0, 0),
            TilePlacement("x", TileSize.SMALL, 1, 0),
            TilePlacement("y", TileSize.SMALL, 2, 0),
            TilePlacement("z", TileSize.SMALL, 3, 0),
        )
        val moved = GridPacker.stickyPlacement(
            anchored = anchored, movedId = "b", size = TileSize.SMALL, targetCol = 0, targetRow = 0, columns = 4,
        )
        assertEquals(GridPacker.encodeSlot(0, 0), moved.getValue("b"))
        assertEquals(GridPacker.encodeSlot(0, 1), moved.getValue("a"))
        assertFalse("x/y/z were never touched by the drop", moved.containsKey("x"))
    }

    @Test
    fun `push-down cascades through a stack of occupants when there is no room to go sideways`() {
        // A single-column grid rules out any sideways nudge, isolating the
        // vertical-cascade behaviour.
        val anchored = listOf(
            TilePlacement("a", TileSize.SMALL, 0, 0),
            TilePlacement("b", TileSize.SMALL, 0, 1),
        )
        val moved = GridPacker.stickyPlacement(
            anchored = anchored, movedId = "c", size = TileSize.SMALL, targetCol = 0, targetRow = 0, columns = 1,
        )
        assertEquals(GridPacker.encodeSlot(0, 0), moved.getValue("c"))
        assertEquals(GridPacker.encodeSlot(0, 1), moved.getValue("a"))
        assertEquals(GridPacker.encodeSlot(0, 2), moved.getValue("b"))
    }

    @Test
    fun `a tile pushed off leaving a fully empty row collapses back up`() {
        // Single-column grid (no sideways room) so dropping onto "a" pushes
        // it to row 1, which was already fully empty — collapse must NOT
        // leave a gap at row 0.
        val anchored = listOf(TilePlacement("a", TileSize.SMALL, 0, 0))
        val moved = GridPacker.stickyPlacement(
            anchored = anchored, movedId = "b", size = TileSize.SMALL, targetCol = 0, targetRow = 0, columns = 1,
        )
        // Both tiles land in the top two rows with nothing collapsible below.
        assertEquals(GridPacker.encodeSlot(0, 0), moved.getValue("b"))
        assertEquals(GridPacker.encodeSlot(0, 1), moved.getValue("a"))
    }

    @Test
    fun `an untouched tile elsewhere on the grid is not part of the result`() {
        // "far" shares row 0 with "a" so no fully-empty row ever exists between
        // them — isolating the assertion to "does an unrelated, non-displaced
        // tile get left out of the result" rather than incidentally exercising
        // empty-row collapse too.
        val anchored = listOf(
            TilePlacement("a", TileSize.SMALL, 0, 0),
            TilePlacement("far", TileSize.SMALL, 3, 0),
        )
        val moved = GridPacker.stickyPlacement(
            anchored = anchored, movedId = "b", size = TileSize.SMALL, targetCol = 1, targetRow = 0, columns = 4,
        )
        assertFalse("an unrelated tile should never move", moved.containsKey("far"))
    }

    // ---- full-row-gap collapse ---------------------------------------------

    @Test
    fun `no fully empty row means nothing moves`() {
        val placements = listOf(
            TilePlacement("a", TileSize.SMALL, 0, 0),
            TilePlacement("b", TileSize.SMALL, 1, 0),
        )
        assertTrue(GridPacker.collapseEmptyRows(placements).isEmpty())
    }

    @Test
    fun `a row only partially occupied is not collapsed`() {
        // Row 1 has only column 0 occupied (a gap at columns 1-3) — allowed.
        val placements = listOf(
            TilePlacement("a", TileSize.SMALL, 0, 0),
            TilePlacement("b", TileSize.SMALL, 0, 1),
        )
        assertTrue(GridPacker.collapseEmptyRows(placements).isEmpty())
    }

    @Test
    fun `a fully empty row collapses and everything below shifts up`() {
        // Row 1 is fully empty (no tile touches any column there).
        val placements = listOf(
            TilePlacement("a", TileSize.SMALL, 0, 0),
            TilePlacement("b", TileSize.SMALL, 1, 2),
        )
        val moved = GridPacker.collapseEmptyRows(placements)
        assertEquals(setOf("b"), moved.keys)
        assertEquals(GridPacker.encodeSlot(1, 1), moved.getValue("b"))
    }

    @Test
    fun `consecutive fully empty rows collapse together`() {
        val placements = listOf(
            TilePlacement("a", TileSize.SMALL, 0, 0),
            TilePlacement("b", TileSize.SMALL, 0, 4), // rows 1-3 fully empty
        )
        val moved = GridPacker.collapseEmptyRows(placements)
        assertEquals(GridPacker.encodeSlot(0, 1), moved.getValue("b"))
    }

    @Test
    fun `a tile spanning multiple rows keeps every row it touches from collapsing`() {
        val placements = listOf(
            TilePlacement("wide", TileSize.WIDE, 0, 0), // touches rows 0-1
            TilePlacement("b", TileSize.SMALL, 0, 2),
        )
        assertTrue(GridPacker.collapseEmptyRows(placements).isEmpty())
    }

    @Test
    fun `sticky packing never overlaps across a large mixed set`() {
        val tiles = demoTiles(40)
        // Anchor every third tile at a distinct, deliberately sparse cell so
        // real gaps exist between them.
        val slots = tiles.filterIndexed { i, _ -> i % 3 == 0 }
            .mapIndexed { i, t -> t.id to GridPacker.encodeSlot(0, i * 3) }
            .toMap()
        val p = GridPacker.packSticky(tiles, slots::get)
        val rows = GridPacker.rowCount(p)
        val occupied = Array(rows) { BooleanArray(GridPacker.COLUMNS) }
        for (placement in p) {
            assertTrue(placement.col + placement.cols <= GridPacker.COLUMNS)
            for (r in placement.row until placement.row + placement.rows) {
                for (c in placement.col until placement.col + placement.cols) {
                    assertFalse("tiles overlap at ($c,$r)", occupied[r][c])
                    occupied[r][c] = true
                }
            }
        }
        assertEquals(40, p.size)
    }

    // ---- inline folder expansion -------------------------------------------

    @Test
    fun `expanding leaves the folder's own placement untouched`() {
        val placements = GridPacker.pack(specs(TileSize.MEDIUM, TileSize.MEDIUM))
        val folder = placements[0]
        val expanded = GridPacker.expandFolderInline(
            placements, folder.id, listOf(TileSpec("child0", TileSize.SMALL)),
        )
        assertEquals(folder, expanded.first { it.id == folder.id })
    }

    @Test
    fun `expanding inserts children right below the folder and pushes what is strictly below down`() {
        // t0 (medium, rows 0-1, col 0), t1 (medium, rows 0-1, col 2 — beside
        // t0, same rows), t2 (medium, rows 2-3, col 0 — strictly below t0).
        // Expanding t0 with a 1-row child block must push t2 down by 1 row but
        // leave t1 (which never overlapped the inserted rows) untouched.
        val placements = GridPacker.pack(specs(TileSize.MEDIUM, TileSize.MEDIUM, TileSize.MEDIUM))
        val expanded = GridPacker.expandFolderInline(
            placements, "t0",
            listOf(TileSpec("child0", TileSize.SMALL), TileSpec("child1", TileSize.SMALL)),
        )
        val t1 = expanded.first { it.id == "t1" }
        assertEquals(0, t1.row) // beside the folder, never touched the inserted rows
        val t2 = expanded.first { it.id == "t2" }
        assertEquals(3, t2.row) // was row 2, pushed down by the 1-row child block
        val child0 = expanded.first { it.id == "child0" }
        assertEquals(2, child0.row) // right below the medium folder (rows 0-1)
        assertEquals(0, child0.col)
    }

    @Test
    fun `nothing above the folder moves when it expands`() {
        val placements = GridPacker.pack(specs(TileSize.SMALL, TileSize.MEDIUM))
        val expanded = GridPacker.expandFolderInline(
            placements, "t1", listOf(TileSpec("child0", TileSize.SMALL)),
        )
        val t0 = expanded.first { it.id == "t0" }
        assertEquals(0, t0.row)
        assertEquals(0, t0.col)
    }

    @Test
    fun `collapsing (no children) is a no-op`() {
        val placements = GridPacker.pack(specs(TileSize.MEDIUM, TileSize.MEDIUM))
        val expanded = GridPacker.expandFolderInline(placements, "t0", emptyList())
        assertEquals(placements, expanded)
    }

    @Test
    fun `expanding an unknown id is a no-op`() {
        val placements = GridPacker.pack(specs(TileSize.MEDIUM))
        val expanded = GridPacker.expandFolderInline(
            placements, "does-not-exist", listOf(TileSpec("child0", TileSize.SMALL)),
        )
        assertEquals(placements, expanded)
    }

    @Test
    fun `expanded layout never overlaps`() {
        val placements = GridPacker.pack(demoTiles(20))
        val children = List(9) { TileSpec("child$it", TileSize.SMALL) }
        val expanded = GridPacker.expandFolderInline(placements, "t5", children)
        val rows = GridPacker.rowCount(expanded)
        val occupied = Array(rows) { BooleanArray(GridPacker.COLUMNS) }
        for (p in expanded) {
            for (r in p.row until p.row + p.rows) {
                for (c in p.col until p.col + p.cols) {
                    assertFalse("tiles overlap at ($c,$r)", occupied[r][c])
                    occupied[r][c] = true
                }
            }
        }
        assertEquals(placements.size + children.size, expanded.size)
    }


    // ---- swapPlacement (FREE mode) --------------------------------------

    @Test
    fun `dropping onto an empty cell just relocates the tile`() {
        val anchored = listOf(TilePlacement("a", TileSize.SMALL, 0, 0))
        val moved = GridPacker.swapPlacement(
            anchored, "moving", movedFromCol = 3, movedFromRow = 3, TileSize.SMALL, targetCol = 3, targetRow = 2,
        )
        assertEquals(setOf("moving"), moved.keys)
        assertEquals(GridPacker.encodeSlot(3, 2), moved.getValue("moving"))
    }

    @Test
    fun `dropping onto an occupied equal-size cell swaps the two tiles`() {
        // "moving" starts at (0,0) and is dropped onto "target" at (2,0);
        // afterwards "moving" must be at (2,0) and "target" at (0,0) — a
        // genuine trade, not both landing on the same cell.
        val anchored = listOf(
            TilePlacement("target", TileSize.SMALL, 2, 0),
            TilePlacement("far", TileSize.SMALL, 3, 3),
        )
        val moved = GridPacker.swapPlacement(
            anchored, "moving", movedFromCol = 0, movedFromRow = 0, TileSize.SMALL, targetCol = 2, targetRow = 0,
        )
        assertEquals(setOf("moving", "target"), moved.keys)
        assertEquals(GridPacker.encodeSlot(2, 0), moved.getValue("moving"))
        assertEquals(GridPacker.encodeSlot(0, 0), moved.getValue("target"))
    }

    @Test
    fun `swap leaves every other tile untouched`() {
        val anchored = listOf(
            TilePlacement("target", TileSize.SMALL, 1, 0),
            TilePlacement("far1", TileSize.SMALL, 3, 0),
            TilePlacement("far2", TileSize.MEDIUM, 0, 2),
        )
        val moved = GridPacker.swapPlacement(
            anchored, "moving", movedFromCol = 3, movedFromRow = 2, TileSize.SMALL, targetCol = 1, targetRow = 0,
        )
        assertEquals(setOf("moving", "target"), moved.keys)
        assertFalse(moved.containsKey("far1"))
        assertFalse(moved.containsKey("far2"))
    }

    @Test
    fun `swap with no known origin cell falls back rather than colliding`() {
        // A tile that was never anchored (movedFromCol/Row unknown, e.g. it
        // just entered FREE mode) has nowhere to send the occupant, so this
        // must fall back to the push-down solver instead of guessing.
        val anchored = listOf(TilePlacement("target", TileSize.SMALL, 1, 0))
        val moved = GridPacker.swapPlacement(
            anchored, "moving", movedFromCol = null, movedFromRow = null, TileSize.SMALL, targetCol = 1, targetRow = 0,
        )
        assertTrue("both tiles must still resolve to some non-overlapping cell", moved.containsKey("moving"))
    }

    @Test
    fun `swapping mismatched footprints that would overlap falls back to the push-down solver`() {
        // "target" is a 3x3 LARGE tile at (0,0) [cols 0-2, rows 0-2]. "moving"
        // (currently at the empty cell (3,0)) is dropped at (1,1), inside
        // target's footprint. A plain swap would put target at moving's old
        // (3,0) [cols 3-5, rows 0-2] — but "blocker" at (3,1) sits squarely
        // inside that new footprint, forcing a real collision a plain swap
        // can't resolve on its own.
        val anchored = listOf(
            TilePlacement("target", TileSize.LARGE, 0, 0),
            TilePlacement("blocker", TileSize.SMALL, 3, 1),
        )
        val moved = GridPacker.swapPlacement(
            anchored, "moving", movedFromCol = 3, movedFromRow = 0, TileSize.SMALL, targetCol = 1, targetRow = 1,
        )
        // Whatever the fallback solver decided, nothing may overlap.
        val final = anchored.filter { it.id != "moving" }.map { p ->
            val slot = moved[p.id]
            if (slot != null) p.copy(col = GridPacker.decodeSlotCol(slot), row = GridPacker.decodeSlotRow(slot)) else p
        } + TilePlacement(
            "moving", TileSize.SMALL,
            GridPacker.decodeSlotCol(moved.getValue("moving")), GridPacker.decodeSlotRow(moved.getValue("moving")),
        )
        for (i in final.indices) {
            for (j in i + 1 until final.size) {
                val a = final[i]
                val b = final[j]
                val overlap = a.col < b.col + b.cols && b.col < a.col + a.cols &&
                    a.row < b.row + b.rows && b.row < a.row + a.rows
                assertFalse("${a.id} and ${b.id} must not overlap", overlap)
            }
        }
    }

    @Test
    fun `swap onto more than one occupant falls back without overlap`() {
        // A WIDE (4x2) tile dropped where two SMALL tiles sit is ambiguous for
        // a plain swap, so it must fall back to the push-down solver rather
        // than pick one occupant arbitrarily.
        val anchored = listOf(
            TilePlacement("s1", TileSize.SMALL, 0, 0),
            TilePlacement("s2", TileSize.SMALL, 1, 0),
        )
        val moved = GridPacker.swapPlacement(
            anchored, "moving", movedFromCol = 0, movedFromRow = 3, TileSize.WIDE, targetCol = 0, targetRow = 0,
        )
        assertTrue("wide tile must have a resolved slot", moved.containsKey("moving"))
    }

    @Test
    fun `swap never collapses a blank row the user deliberately left open`() {
        // Row 1 is empty on purpose (FREE mode's whole point). Swapping two
        // tiles in row 0 must not touch row 1, and the blank row must not be
        // collapsed away by the plain (non-fallback) swap path.
        val anchored = listOf(
            TilePlacement("target", TileSize.SMALL, 1, 0),
            TilePlacement("keep-away", TileSize.SMALL, 0, 2),
        )
        val moved = GridPacker.swapPlacement(
            anchored, "moving", movedFromCol = 3, movedFromRow = 0, TileSize.SMALL, targetCol = 1, targetRow = 0,
        )
        assertEquals(setOf("moving", "target"), moved.keys)
        assertFalse("a plain equal-size swap must never touch an unrelated tile", moved.containsKey("keep-away"))
    }
}
