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
 * Whether [point] falls inside the merge zone of a tile's [rect] (FR-3.3).
 *
 * For an **app** target ([isFolder] = false) the zone is the normative inner
 * 22–78% (both axes) — the outer ring stays a reorder. For a **folder** target
 * ([isFolder] = true) the whole tile is a merge zone: dropping onto a folder
 * almost always means "add to this folder", so any point inside it merges (a
 * quick pass-through still reorders because the drag loop only settles a merge
 * after a dwell). Edit-mode drag reorders only when *outside* this zone.
 */
fun inMergeZone(rect: Rect, point: Offset, isFolder: Boolean = false): Boolean {
    if (rect.width <= 0f || rect.height <= 0f) return false
    if (isFolder) return rect.contains(point)
    val cx = (point.x - rect.left) / rect.width
    val cy = (point.y - rect.top) / rect.height
    return cx in 0.22f..0.78f && cy in 0.22f..0.78f
}

/**
 * Whether the hovered tile [rect] should be (or stay) the folder-merge target as
 * the finger sits at [point]. Entering a merge still needs the normative inner
 * 22–78% centre ([inMergeZone]); but once a tile *is* the target
 * ([alreadyTarget]), anywhere inside it keeps the merge — a sticky zone so small
 * wobbles out of the exact centre don't drop a folder-merge mid-drag (FR-3.3).
 * [point] is assumed already inside [rect] (the caller hit-tests first).
 */
fun heldAsMergeTarget(
    rect: Rect,
    point: Offset,
    alreadyTarget: Boolean,
    isFolder: Boolean = false,
): Boolean = alreadyTarget || inMergeZone(rect, point, isFolder)

/**
 * Directional reorder hysteresis (FR-3.2). Returns whether the dragged tile,
 * sitting at [fingerPos], should take over [target]'s slot — committing only
 * once the finger has crossed past the target's *midpoint along the dominant
 * drag axis*. This stops a tile reshuffling the moment the finger grazes a
 * neighbour: the gap opens only when the finger has clearly moved onto the far
 * half of the target in the direction it's travelling.
 *
 * [target] is already the tile under the finger (the caller hit-tests). It is
 * never the dragged tile itself. A horizontal-dominant move tests the x
 * midpoint, a vertical-dominant move the y midpoint.
 */
fun shouldReorder(target: Rect, fingerPos: Offset, dragVector: Offset): Boolean {
    if (target.width <= 0f || target.height <= 0f) return false
    val horizontal = kotlin.math.abs(dragVector.x) >= kotlin.math.abs(dragVector.y)
    return if (horizontal) {
        val mid = (target.left + target.right) / 2f
        if (dragVector.x >= 0f) fingerPos.x >= mid else fingerPos.x <= mid
    } else {
        val mid = (target.top + target.bottom) / 2f
        if (dragVector.y >= 0f) fingerPos.y >= mid else fingerPos.y <= mid
    }
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
