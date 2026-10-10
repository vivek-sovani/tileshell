package com.tileshell.feature.start

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where the mini-tiles of a tile's quick-action cluster go (top-left corners, in px), around [tile] on a screen of
 * [screenW]×[screenH]: first a column down the tile's right edge (its left edge when there is no room on the right),
 * as many as fit the tile's height (at most 3); the rest in rows under the tile (above it when there is no room
 * below). Everything stays [margin] inside the screen. Pure, so it is unit-tested.
 */
internal fun quickMenuSlots(
    tile: Rect,
    count: Int,
    screenW: Float,
    screenH: Float,
    item: Float,
    gap: Float,
    margin: Float,
    topInset: Float = 0f,
    bottomInset: Float = 0f,
): List<Offset> {
    if (count <= 0) return emptyList()
    val step = item + gap
    val minY = margin + topInset
    val maxY = screenH - margin - bottomInset
    val rightFits = tile.right + gap + item <= screenW - margin
    val leftFits = tile.left - gap - item >= margin
    val sideCap = if (rightFits || leftFits) floor((tile.height + gap) / step).toInt().coerceIn(1, 3) else 0
    val side = min(count, sideCap)
    val out = ArrayList<Offset>(count)
    val sideX = if (rightFits) tile.right + gap else tile.left - gap - item
    // The column moves as one: when the tile is near the bottom edge it shifts up whole, never squashing its items together.
    val columnTop = tile.top.coerceAtMost(maxY - (side * step - gap)).coerceAtLeast(minY)
    for (i in 0 until side) {
        out += Offset(sideX, columnTop + i * step)
    }
    val rest = count - side
    if (rest > 0) {
        fun perRowFor(left: Float, right: Float) = floor((right - left + gap) / step).toInt().coerceIn(1, 4)
        var rowLeft = margin
        var rowRight = screenW - margin
        var perRow = perRowFor(rowLeft, rowRight)
        var blockHeight = ceil(rest / perRow.toFloat()).toInt() * step - gap
        val below = tile.bottom + gap
        fun startYFor(height: Float) = if (below + height <= maxY) below else (tile.top - gap - height).coerceAtLeast(minY)
        var startY = startYFor(blockHeight)
        // Rows above the tile (no room below) can run into a side column that was shifted up: then they stop short of it.
        val columnBottom = columnTop + side * step - gap
        if (side > 0 && startY + blockHeight > columnTop && startY < columnBottom) {
            if (rightFits) rowRight = sideX - gap else rowLeft = sideX + item + gap
            perRow = perRowFor(rowLeft, rowRight)
            blockHeight = ceil(rest / perRow.toFloat()).toInt() * step - gap
            startY = startYFor(blockHeight)
        }
        for (k in 0 until rest) {
            val row = k / perRow
            val col = k % perRow
            val inRow = min(perRow, rest - row * perRow)
            val rowWidth = inRow * step - gap
            val startX = tile.left.coerceIn(rowLeft, max(rowLeft, rowRight - rowWidth))
            out += Offset(startX + col * step, startY + row * step)
        }
    }
    return out
}
