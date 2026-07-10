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
}
