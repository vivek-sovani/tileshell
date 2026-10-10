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

/** Where the cluster's pieces go: the mini-tiles' top-left corners and, when a notification is shown, its card. */
internal data class QuickMenuPlan(val slots: List<Offset>, val card: Rect?)

/**
 * [quickMenuSlots] plus a notification card of [cardHeight] (0 = none) right next to [tile]: under it when the tile
 * is in the upper half of the screen (above it otherwise, or wherever it fits), as wide as the screen. The
 * mini-tiles then go in rows on the other side of the tile; when there is no room there they go beyond the card.
 * Pure, so it is unit-tested.
 */
internal fun quickMenuPlan(
    tile: Rect,
    count: Int,
    cardHeight: Float,
    screenW: Float,
    screenH: Float,
    item: Float,
    gap: Float,
    margin: Float,
    topInset: Float = 0f,
    bottomInset: Float = 0f,
): QuickMenuPlan {
    if (cardHeight <= 0f) {
        return QuickMenuPlan(quickMenuSlots(tile, count, screenW, screenH, item, gap, margin, topInset, bottomInset), null)
    }
    val step = item + gap
    val minY = margin + topInset
    val maxY = screenH - margin - bottomInset
    val fitsBelow = tile.bottom + gap + cardHeight <= maxY
    val fitsAbove = tile.top - gap - cardHeight >= minY
    val preferBelow = tile.center.y < screenH / 2f
    val cardBelow = when {
        preferBelow && fitsBelow -> true
        !preferBelow && fitsAbove -> false
        fitsBelow -> true
        fitsAbove -> false
        else -> preferBelow
    }
    val rawTop = if (cardBelow) tile.bottom + gap else tile.top - gap - cardHeight
    val cardTop = rawTop.coerceIn(minY, max(minY, maxY - cardHeight))
    val card = Rect(margin, cardTop, screenW - margin, cardTop + cardHeight)
    if (count <= 0) return QuickMenuPlan(emptyList(), card)

    val perRow = floor((screenW - 2 * margin + gap) / step).toInt().coerceIn(1, 5)
    val rows = ceil(count / perRow.toFloat()).toInt()
    val rowsHeight = rows * step - gap
    // The side of the tile away from the card, if the rows fit there; else beyond the card.
    val oppositeTop = if (cardBelow) tile.top - gap - rowsHeight else tile.bottom + gap
    val oppositeFits = if (cardBelow) oppositeTop >= minY else oppositeTop + rowsHeight <= maxY
    val startY = when {
        oppositeFits -> oppositeTop
        cardBelow -> (card.bottom + gap).coerceAtMost(max(minY, maxY - rowsHeight))
        else -> (card.top - gap - rowsHeight).coerceAtLeast(minY)
    }
    val slots = ArrayList<Offset>(count)
    for (k in 0 until count) {
        val row = k / perRow
        val col = k % perRow
        val inRow = min(perRow, count - row * perRow)
        val rowWidth = inRow * step - gap
        val startX = tile.left.coerceIn(margin, max(margin, screenW - margin - rowWidth))
        slots += Offset(startX + col * step, startY + row * step)
    }
    return QuickMenuPlan(slots, card)
}

/**
 * Where the size panel goes so it sits next to the tile being resized (root px for its top edge): right under the
 * tile's current (previewed) size, else right above it; null when neither side has room, so the caller keeps it
 * at the bottom of the screen. Pure, so it is unit-tested.
 */
internal fun sizePanelTop(
    tileTop: Float,
    tileBottom: Float,
    panelHeight: Float,
    screenH: Float,
    topInset: Float,
    bottomInset: Float,
    gap: Float,
): Float? {
    if (tileTop.isNaN() || tileBottom.isNaN() || panelHeight <= 0f) return null
    val below = tileBottom + gap
    if (below + panelHeight <= screenH - bottomInset) return below
    val above = tileTop - gap - panelHeight
    if (above >= topInset) return above
    return null
}
