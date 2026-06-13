package com.tileshell.feature.start

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Pixel geometry of the dense 4-column grid, derived proportionally from the
 * available width against the 393 px prototype reference (unit 90 / gap 3 /
 * side 9 / top 10). Shared by [DenseTileGrid] (tile placement) and the
 * edit-mode drag hit-testing so both agree on exactly where each tile sits.
 */
class GridGeometry private constructor(
    val side: Float,
    val gap: Float,
    val unit: Float,
    val topPad: Float,
) {
    val step: Float get() = unit + gap

    /** Top-left of a placed tile, in grid-local px. */
    fun topLeft(p: TilePlacement): IntOffset = IntOffset(
        (side + p.col * step).roundToInt(),
        (topPad + p.row * step).roundToInt(),
    )

    /** Pixel size of a placed tile (spans across its cols/rows incl. inner gaps). */
    fun sizePx(p: TilePlacement): IntSize = IntSize(
        (p.cols * unit + (p.cols - 1) * gap).roundToInt().coerceAtLeast(0),
        (p.rows * unit + (p.rows - 1) * gap).roundToInt().coerceAtLeast(0),
    )

    /** Grid-local bounding rect of a placed tile, for hit-testing. */
    fun rect(p: TilePlacement): Rect {
        val tl = topLeft(p)
        val sz = sizePx(p)
        return Rect(
            tl.x.toFloat(),
            tl.y.toFloat(),
            (tl.x + sz.width).toFloat(),
            (tl.y + sz.height).toFloat(),
        )
    }

    /** Total packed height in px (0 when empty). */
    fun totalHeight(rowCount: Int): Int =
        if (rowCount == 0) 0 else (topPad + rowCount * unit + (rowCount - 1) * gap).roundToInt()

    companion object {
        fun of(totalWidthPx: Float, columns: Int = GridPacker.COLUMNS): GridGeometry {
            val side = totalWidthPx * (9f / 393f)
            val gap = totalWidthPx * (3f / 393f)
            val topPad = totalWidthPx * (10f / 393f)
            val unit = (totalWidthPx - 2 * side - (columns - 1) * gap) / columns
            return GridGeometry(side, gap, unit, topPad)
        }
    }
}

/** The id of the placed tile containing [point] (grid-local px), or null. */
fun tileAt(placements: List<TilePlacement>, geom: GridGeometry, point: Offset): String? =
    placements.firstOrNull { geom.rect(it).contains(point) }?.id

/**
 * Whether [point] falls inside the inner 22–78% (both axes) of a tile's [rect] —
 * the merge zone (FR-3.3). Edit-mode drag reorders only when *outside* it; the
 * centre is reserved for the folder-merge gesture landing in S14.
 */
fun inMergeZone(rect: Rect, point: Offset): Boolean {
    if (rect.width <= 0f || rect.height <= 0f) return false
    val cx = (point.x - rect.left) / rect.width
    val cy = (point.y - rect.top) / rect.height
    return cx in 0.22f..0.78f && cy in 0.22f..0.78f
}

/**
 * Move [dragId] to sit where [targetId] currently is (FR-3.2). Mirrors the
 * prototype reorder (`reorder()` in launcher.js): splice the dragged id out,
 * then re-insert it at the target's *original* index — so a forward drag lands
 * the tile after the target and a backward drag lands it before, matching the
 * finger direction. Returns a new list; the input is untouched. No-op when
 * either id is absent or the two are equal.
 */
fun reorderTiles(order: List<String>, dragId: String, targetId: String): List<String> {
    if (dragId == targetId) return order
    val di = order.indexOf(dragId)
    val ti = order.indexOf(targetId)
    if (di < 0 || ti < 0) return order
    val out = order.toMutableList()
    out.removeAt(di)
    out.add(ti.coerceAtMost(out.size), dragId)
    return out
}
