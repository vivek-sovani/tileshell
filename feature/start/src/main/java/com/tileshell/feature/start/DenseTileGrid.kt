package com.tileshell.feature.start

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Renders [tiles] with the [GridPacker] dense layout. Geometry comes from the
 * shared [GridGeometry] (cell unit, gaps and side padding derived
 * proportionally from the available width against the 393 px prototype
 * reference: unit 90 / gap 3 / side 9 / top 10), so the 4-column grid fills the
 * screen at any size while preserving the prototype's spacing ratios.
 *
 * Each tile is placed at the grid origin and handed its target [IntOffset] slot
 * and [IntSize]; the caller positions it (via `Modifier.offset`), which lets the
 * edit-mode drag lift one tile off its slot to follow the finger while the rest
 * animate to their new slots as the order re-flows. The container reports its
 * full packed height, so it sits inside a vertically scrolling parent.
 *
 * [slotOf], when non-null, switches to the windows-phone-style sticky
 * (gap-preserving) arrangement ([GridPacker.packSticky]) instead of the default
 * dense repack — a tile with a cell renders exactly there regardless of what
 * else changed. Existing callers that don't pass it are unaffected.
 *
 * [postProcess], when non-null, runs after the pack/packSticky computation —
 * used for inline folder expansion ([GridPacker.expandFolderInline]), which is
 * a pure rendering transform independent of which arrangement mode is active.
 */
@Composable
fun DenseTileGrid(
    tiles: List<TileSpec>,
    modifier: Modifier = Modifier,
    columns: Int = GridPacker.COLUMNS,
    gapPx: Float? = null,
    slotOf: ((String) -> Int?)? = null,
    postProcess: ((List<TilePlacement>) -> List<TilePlacement>)? = null,
    tileContent: @Composable (spec: TileSpec, slot: IntOffset, sizePx: IntSize) -> Unit,
) {
    // Memoized: both packSticky and postProcess (inline folder expansion) do
    // non-trivial grid-packing work, and this composable recomposes often
    // (live-tile flips, notification/media polling) whenever it's on screen.
    // Recomputing unconditionally on every one of those ticks was cheap
    // enough to go unnoticed normally, but slow enough while a folder was
    // expanded to occasionally starve the touch-handling coroutine mid-tap —
    // a plain tap would then read as having crossed the long-press threshold
    // and open edit mode instead of launching. Correct as long as callers
    // pass stable `slotOf`/`postProcess` instances (i.e. `remember`'d at the
    // call site) rather than a fresh lambda every recomposition.
    val basePlacements = if (slotOf != null) {
        remember(tiles, columns, slotOf) { GridPacker.packSticky(tiles, slotOf, columns) }
    } else {
        remember(tiles, columns) { GridPacker.pack(tiles, columns) }
    }
    val placements = remember(basePlacements, postProcess) {
        postProcess?.invoke(basePlacements) ?: basePlacements
    }

    BoxWithConstraints(modifier = modifier) {
        val totalW = constraints.maxWidth.toFloat()
        val geom = remember(totalW, columns, gapPx) { GridGeometry.of(totalW, columns, gapPx) }
        val rowCount = GridPacker.rowCount(placements)
        val heightDp = with(LocalDensity.current) { geom.totalHeight(rowCount).toDp() }

        Box(modifier = Modifier.fillMaxWidth().height(heightDp)) {
            placements.forEach { p ->
                key(p.id) {
                    tileContent(TileSpec(p.id, p.size), geom.topLeft(p), geom.sizePx(p))
                }
            }
        }
    }
}
