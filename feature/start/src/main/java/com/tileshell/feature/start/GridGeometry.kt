package com.tileshell.feature.start

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tileshell.core.data.TileSize
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
        /**
         * [gapPx], when given, overrides the proportional tile gap (the
         * personalize "tile spacing" setting in px); null keeps the prototype's
         * tight 3/393 ratio. Side padding and top stay proportional either way.
         */
        fun of(
            totalWidthPx: Float,
            columns: Int = GridPacker.COLUMNS,
            gapPx: Float? = null,
        ): GridGeometry {
            val side = totalWidthPx * (9f / 393f)
            val gap = gapPx ?: (totalWidthPx * (3f / 393f))
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
 * Nearest grid cell (col, row) for a tile's top-left corner at [topLeftPx] —
 * the inverse of [GridGeometry.topLeft]. Used by the sticky (gap-preserving)
 * arrangement to find which cell a drag-drop lands in; [w] clamps the column so
 * a wider tile's footprint never overflows the grid, and row has no upper bound
 * (the grid simply grows).
 */
fun GridGeometry.cellAt(topLeftPx: Offset, columns: Int, w: Int): IntOffset {
    val col = ((topLeftPx.x - side) / step).roundToInt().coerceIn(0, (columns - w).coerceAtLeast(0))
    val row = ((topLeftPx.y - topPad) / step).roundToInt().coerceAtLeast(0)
    return IntOffset(col, row)
}

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
 * Whether the hovered tile [rect] should be (or stay) the folder-merge target as
 * the finger sits at [point]. Entering a merge still needs the normative inner
 * 22–78% centre ([inMergeZone]); but once a tile *is* the target
 * ([alreadyTarget]), anywhere inside it keeps the merge — a sticky zone so small
 * wobbles out of the exact centre don't drop a folder-merge mid-drag (FR-3.3).
 * [point] is assumed already inside [rect] (the caller hit-tests first).
 */
fun heldAsMergeTarget(rect: Rect, point: Offset, alreadyTarget: Boolean): Boolean =
    alreadyTarget || inMergeZone(rect, point)

/**
 * Whether [point] still falls inside a folder's own inline-expanded block —
 * its own placed cell, or any of its currently-rendered children's cells
 * (see [GridPacker.expandFolderInline]) — used by the folder-child drag-out
 * gesture (`StartScreen.editDragGesture`) to tell an intra-folder sibling
 * reorder (still inside) apart from pulling the app out onto the top-level
 * grid (moved outside). Pure and decoupled from the synthetic child-id
 * encoding: the caller tells us which of [placements] belongs to [folderId]
 * via [isChild] (true for one of its rendered children).
 */
fun isInsideFolderBlock(
    placements: List<TilePlacement>,
    geom: GridGeometry,
    folderId: String,
    point: Offset,
    isChild: (id: String) -> Boolean,
): Boolean = placements.any { (it.id == folderId || isChild(it.id)) && geom.rect(it).contains(point) }

/**
 * Where a folder child pulled out onto the top-level grid (dense/free mode)
 * should land: spliced into [order] at [beforeId]'s current index — so it
 * inserts right where it was dropped, mirroring [reorderTiles]'s own
 * splice-before-target convention — or appended at the end when [beforeId]
 * is null or no longer present (an empty-area drop). Pure; [order] is
 * untouched, a new list is returned.
 */
fun insertBeforeTarget(order: List<String>, newId: String, beforeId: String?): List<String> {
    val out = order.toMutableList()
    val idx = beforeId?.let { out.indexOf(it) }?.takeIf { it >= 0 } ?: out.size
    out.add(idx, newId)
    return out
}

/**
 * Reconciles the live working order with the freshly persisted layout
 * ([fresh], DB-ordered): keeps every still-present id in [current]'s own
 * existing relative order (so a just-applied client-side reorder isn't
 * clobbered by the async DB round-trip catching up — no flicker), drops ids
 * that no longer exist, and — unlike a plain "keep the survivors, append
 * everything new at the end" merge — inserts a brand-new id immediately
 * before whichever already-known id sits right after it in [fresh], not
 * always at the very end. A genuinely-appended new pin still lands at the end
 * here too (nothing already-known follows it in [fresh]), but a tile written
 * to a specific mid-grid position — e.g. dragged out of a folder to a chosen
 * spot ([insertBeforeTarget]'s own result, once persisted) — lands there
 * instead of always reappearing at the bottom regardless of where the DB
 * actually placed it. Pure; both inputs are untouched.
 */
fun mergeOrder(current: List<String>, fresh: List<String>): List<String> {
    if (current.isEmpty()) return fresh
    val present = fresh.toHashSet()
    val kept = current.filter { it in present }
    val keptSet = kept.toHashSet()
    val newIds = fresh.filter { it !in keptSet }
    if (newIds.isEmpty()) return kept
    val result = kept.toMutableList()
    for (newId in newIds) {
        val newIdIndex = fresh.indexOf(newId)
        val nextKeptId = fresh.drop(newIdIndex + 1).firstOrNull { it in keptSet }
        val insertAt = if (nextKeptId != null) result.indexOf(nextKeptId) else result.size
        result.add(insertAt, newId)
    }
    return result
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

/** The largest row count any [TileSize] preset uses — a resize drag never needs more. */
private val MAX_PRESET_ROWS = TileSize.entries.maxOf { it.rows }

/**
 * The nearest [TileSize] preset for a live drag-resize gesture (single-finger
 * corner drag, which always moves both axes at once, so there is no
 * per-axis gating here): starting from a
 * [currentCols]×[currentRows] footprint, the gesture has moved ([dxPx],
 * [dyPx]) since it began, measured in the same px [geom] uses for one grid
 * cell step (unit + gap) on each axis. The result is clamped to the grid's
 * [columns] and to the tallest preset's row count, then matched to the
 * preset with the smallest squared cols/rows distance — ties fall to
 * whichever [TileSize] entry comes first, which is deterministic
 * (declaration order) but otherwise arbitrary, since a true tie is
 * indistinguishable to the user. Pure and stateless: the caller
 * (StartScreen's hoisted resize-preview state) re-derives the candidate on
 * every drag tick from the *total* delta since the gesture started, not
 * incrementally — so it can never drift from what a single call with the
 * same inputs would produce.
 *
 * [minRows] excludes every preset shorter than it from the candidate set —
 * for a tile whose content needs 2+ rows (see [AppCategories.requiresTallTile]),
 * passing 2 here keeps a drag from ever landing on SMALL/WIDE_SMALL/BANNER.
 */
fun snapResizeTarget(
    geom: GridGeometry,
    currentCols: Int,
    currentRows: Int,
    dxPx: Float,
    dyPx: Float,
    columns: Int,
    minRows: Int = 1,
): TileSize {
    val dCols = (dxPx / geom.step).roundToInt()
    val dRows = (dyPx / geom.step).roundToInt()
    val targetCols = (currentCols + dCols).coerceIn(1, columns.coerceAtLeast(1))
    val targetRows = (currentRows + dRows).coerceIn(1, MAX_PRESET_ROWS)
    return TileSize.entries
        .filter { it.rows >= minRows }
        .minByOrNull { candidate ->
            val colsDiff = candidate.cols - targetCols
            val rowsDiff = candidate.rows - targetRows
            colsDiff * colsDiff + rowsDiff * rowsDiff
        } ?: TileSize.MEDIUM
}
