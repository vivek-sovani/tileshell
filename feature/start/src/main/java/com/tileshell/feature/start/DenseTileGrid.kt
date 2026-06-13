package com.tileshell.feature.start

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Renders [tiles] with the [GridPacker] dense layout in a custom Compose
 * [Layout]. The cell unit, gaps and side padding are derived proportionally
 * from the available width against the 393px prototype reference
 * (unit 90 / gap 3 / side 9 / top 10), so the 4-column grid fills the screen
 * at any size while preserving the prototype's spacing ratios.
 *
 * The layout reports its full packed height (independent of incoming height
 * constraints), so it can be placed inside a vertically scrolling container.
 */
@Composable
fun DenseTileGrid(
    tiles: List<TileSpec>,
    modifier: Modifier = Modifier,
    columns: Int = GridPacker.COLUMNS,
    tileContent: @Composable (TileSpec) -> Unit,
) {
    val placements = remember(tiles, columns) { GridPacker.pack(tiles, columns) }

    Layout(
        modifier = modifier,
        content = {
            placements.forEach { p -> Box { tileContent(TileSpec(p.id, p.size)) } }
        },
    ) { measurables, constraints ->
        val totalW = constraints.maxWidth
        val side = totalW * (9f / 393f)
        val gap = totalW * (3f / 393f)
        val topPad = totalW * (10f / 393f)
        val unit = (totalW - 2 * side - (columns - 1) * gap) / columns
        val step = unit + gap

        val placed = measurables.mapIndexed { i, measurable ->
            val p = placements[i]
            val w = (p.cols * unit + (p.cols - 1) * gap).roundToInt().coerceAtLeast(0)
            val h = (p.rows * unit + (p.rows - 1) * gap).roundToInt().coerceAtLeast(0)
            measurable.measure(Constraints.fixed(w, h)) to p
        }

        val rowCount = GridPacker.rowCount(placements)
        val height =
            if (rowCount == 0) 0
            else (topPad + rowCount * unit + (rowCount - 1) * gap).roundToInt()

        layout(totalW, height) {
            placed.forEach { (placeable, p) ->
                val x = (side + p.col * step).roundToInt()
                val y = (topPad + p.row * step).roundToInt()
                placeable.place(x, y)
            }
        }
    }
}
