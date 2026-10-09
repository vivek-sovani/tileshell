package com.tileshell.feature.start

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileSizeCellsTest {
    private val all = TileSize.entries

    @Test
    fun `a cell means the size ending there`() {
        assertEquals(TileSize.SMALL, sizeForCell(0, 0, all))
        assertEquals(TileSize.MEDIUM, sizeForCell(1, 1, all))
        assertEquals(TileSize.WIDE, sizeForCell(3, 1, all))
        assertEquals(TileSize.LARGE, sizeForCell(2, 2, all))
        assertEquals(TileSize.XLARGE, sizeForCell(3, 3, all))
        assertEquals(TileSize.BANNER, sizeForCell(3, 0, all))
        assertEquals(TileSize.COLUMN, sizeForCell(0, 3, all))
    }

    @Test
    fun `rectangles that are not a size, or are not offered, have no cell`() {
        // 1 wide, 3 tall isn't a tile size.
        assertNull(sizeForCell(0, 2, all))
        // A tile that needs two rows is not offered a one-row size.
        assertNull(sizeForCell(0, 0, all.filter { it.rows >= 2 }))
        assertEquals(TileSize.MEDIUM, sizeForCell(1, 1, all.filter { it.rows >= 2 }))
    }

    @Test
    fun `a touch finds its cell and stays inside the grid`() {
        assertEquals(0 to 0, cellAtPoint(3f, 3f, 44f))
        assertEquals(2 to 1, cellAtPoint(100f, 60f, 44f))
        assertEquals(3 to 3, cellAtPoint(900f, 900f, 44f))
        assertEquals(0 to 0, cellAtPoint(-20f, -5f, 44f))
    }

    @Test
    fun `labels give the dimensions and a name where there is one`() {
        assertEquals("2×2 medium", sizeLabel(TileSize.MEDIUM))
        assertEquals("3×2", sizeLabel(TileSize.WIDE_MEDIUM))
        assertEquals("4×4 extra large", sizeLabel(TileSize.XLARGE))
    }
}
