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
 */
@Composable
fun DenseTileGrid(
    tiles: List<TileSpec>,
    modifier: Modifier = Modifier,
    columns: Int = GridPacker.COLUMNS,
    tileContent: @Composable (spec: TileSpec, slot: IntOffset, sizePx: IntSize) -> Unit,
) {
    val placements = remember(tiles, columns) { GridPacker.pack(tiles, columns) }

    BoxWithConstraints(modifier = modifier) {
        val totalW = constraints.maxWidth.toFloat()
        val geom = remember(totalW, columns) { GridGeometry.of(totalW, columns) }
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
