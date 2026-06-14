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
}
