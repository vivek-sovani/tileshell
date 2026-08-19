package com.tileshell.feature.start

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure logic for gesture-based tile resize: `snapResizeTarget` (GridGeometry.kt).
 * The single-finger corner-drag gesture always moves both axes at once, so
 * there's no per-axis behaviour left to test here — every case below feeds a
 * plain (dxPx, dyPx) delta.
 */
class ResizeSnapTest {

    private val geom = GridGeometry.of(totalWidthPx = 393f, columns = 4)

    @Test
    fun `no movement keeps the current footprint`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 2, dxPx = 0f, dyPx = 0f, columns = 4,
        )
        assertEquals(TileSize.MEDIUM, target)
    }

    @Test
    fun `dragging the corner right and down by one cell grows both axes`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = geom.step, dyPx = geom.step, columns = 4,
        )
        assertEquals(TileSize.MEDIUM, target)
    }

    @Test
    fun `movement on only one axis leaves the other footprint dimension unchanged`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 1, dxPx = 0f, dyPx = geom.step, columns = 4,
        )
        // cols must stay at 2 since dxPx is 0 — this is what a finger moving
        // purely vertically (holding the other still, or a straight-down
        // corner drag) produces.
        assertEquals(2, target.cols)
        assertEquals(2, target.rows)
        assertEquals(TileSize.MEDIUM, target)
    }

    @Test
    fun `dragging past the grid edge clamps to the widest available preset`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = geom.step * 20, dyPx = 0f, columns = 4,
        )
        // Clamped to columns=4, rows stays 1: (4,1) is an exact match for
        // BANNER, added alongside COLUMN(1,4) for a full-width/full-height
        // drag-only preset pair.
        assertEquals(TileSize.BANNER, target)
    }

    @Test
    fun `growing to a 4x1 footprint lands on banner`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = geom.step * 3, dyPx = 0f, columns = 4,
        )
        assertEquals(TileSize.BANNER, target)
    }

    @Test
    fun `growing to a 1x4 footprint lands on column`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = 0f, dyPx = geom.step * 3, columns = 4,
        )
        assertEquals(TileSize.COLUMN, target)
    }

    @Test
    fun `a large negative drag never produces a footprint smaller than 1x1`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 2, dxPx = -geom.step * 50, dyPx = -geom.step * 50, columns = 4,
        )
        assertEquals(TileSize.SMALL, target)
    }

    @Test
    fun `growing toward wide-small lands exactly on the new 2x1 preset`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = geom.step, dyPx = -geom.step * 0.4f, columns = 4,
        )
        assertEquals(TileSize.WIDE_SMALL, target)
    }

    @Test
    fun `growing toward tall lands exactly on the new 1x2 preset`() {
        val target = snapResizeTarget(
            geom, currentCols = 1, currentRows = 1, dxPx = -geom.step * 0.4f, dyPx = geom.step, columns = 4,
        )
        assertEquals(TileSize.TALL, target)
    }

    @Test
    fun `growing to a 4x4 footprint lands on xlarge`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 2, dxPx = geom.step * 2, dyPx = geom.step * 2, columns = 4,
        )
        assertEquals(TileSize.XLARGE, target)
    }

    @Test
    fun `column clamp never exceeds the current column count`() {
        val target = snapResizeTarget(
            geom, currentCols = 2, currentRows = 2, dxPx = geom.step * 10, dyPx = 0f, columns = 4,
        )
        assertEquals(4, target.cols)
    }
}
