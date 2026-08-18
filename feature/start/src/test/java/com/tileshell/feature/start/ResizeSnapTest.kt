package com.tileshell.feature.start

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure logic for the drag-to-resize handles: `snapResizeTarget` (GridGeometry.kt). */
class ResizeSnapTest {

    private val geom = GridGeometry.of(totalWidthPx = 393f, columns = 4)

    @Test
    fun `no movement keeps the current footprint`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 2, dxPx = 0f, dyPx = 0f, axis = ResizeAxis.BOTH, columns = 4,
        )
        assertEquals(TileSize.MEDIUM, target)
    }

    @Test
    fun `dragging the corner right and down by one cell grows both axes`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = geom.step, dyPx = geom.step, axis = ResizeAxis.BOTH, columns = 4,
        )
        assertEquals(TileSize.MEDIUM, target)
    }

    @Test
    fun `height-only handle ignores horizontal movement`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 1, dxPx = geom.step * 5, dyPx = geom.step, axis = ResizeAxis.HEIGHT, columns = 4,
        )
        // cols must stay at 2 regardless of the (ignored) horizontal delta.
        assertEquals(2, target.cols)
        assertEquals(2, target.rows)
        assertEquals(TileSize.MEDIUM, target)
    }

    @Test
    fun `width-only handle ignores vertical movement`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 2, dxPx = geom.step, dyPx = geom.step * 5, axis = ResizeAxis.WIDTH, columns = 4,
        )
        assertEquals(2, target.cols)
        assertEquals(2, target.rows)
    }

    @Test
    fun `dragging past the grid edge clamps to the widest available preset`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = geom.step * 20, dyPx = 0f, axis = ResizeAxis.WIDTH, columns = 4,
        )
        // Clamped to columns=4, rows stays 1 -> nearest preset is WIDE_SMALL (2x1)... 
        // actually target cols clamps to 4, rows clamps to 1: nearest by squared
        // distance among all nine presets to (4,1) is WIDE_SMALL(2,1) at distance 4,
        // vs WIDE(4,2) at distance 1 -> WIDE wins.
        assertEquals(TileSize.WIDE, target)
    }

    @Test
    fun `a large negative drag never produces a footprint smaller than 1x1`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 2, dxPx = -geom.step * 50, dyPx = -geom.step * 50, axis = ResizeAxis.BOTH, columns = 4,
        )
        assertEquals(TileSize.SMALL, target)
    }

    @Test
    fun `growing toward wide-small lands exactly on the new 2x1 preset`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = geom.step, dyPx = -geom.step * 0.4f, axis = ResizeAxis.BOTH, columns = 4,
        )
        assertEquals(TileSize.WIDE_SMALL, target)
    }

    @Test
    fun `growing toward tall lands exactly on the new 1x2 preset`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = -geom.step * 0.4f, dyPx = geom.step, axis = ResizeAxis.BOTH, columns = 4,
        )
        assertEquals(TileSize.TALL, target)
    }

    @Test
    fun `growing to a 4x4 footprint lands on xlarge`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 2, dxPx = geom.step * 2, dyPx = geom.step * 2, axis = ResizeAxis.BOTH, columns = 4,
        )
        assertEquals(TileSize.XLARGE, target)
    }

    @Test
    fun `column clamp never exceeds the current column count`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 2, dxPx = geom.step * 10, dyPx = 0f, axis = ResizeAxis.WIDTH, columns = 4,
        )
        assertEquals(4, target.cols)
    }
}
