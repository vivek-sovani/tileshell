package com.tileshell.feature.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * True while edit mode was opened from a tile's quick actions: the tile's own corner buttons (unpin, colour, size)
 * stay out of the way, because those are in the cluster, and the tile is only there to be moved.
 */
val LocalTileOnlyEdit = staticCompositionLocalOf { false }

/**
 * Called when a long press on a tile (which opened its quick actions) turns into a drag: starts move mode for this
 * tile and lets the edit-mode grid gesture carry on with the same finger. It is given where the finger first pressed,
 * relative to the tile's top-left, so the tile can be held by that same point however far the finger has moved by the
 * time the handover is done. Returns false (nothing happens, the cluster stays) when the layout is locked. Null where
 * a tile has no such handover.
 */
val LocalTileDragStart = staticCompositionLocalOf<((Offset) -> Boolean)?> { null }

/**
 * Tells the user the tile can be moved now: when [active] turns on, the tile lifts and pulses three times while a
 * four-way arrow glyph breathes over it, then it settles (the usual selected look stays). Draw-only.
 */
fun Modifier.moveHint(active: Boolean): Modifier = composed {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) {
            pulse.snapTo(0f)
            return@LaunchedEffect
        }
        repeat(3) {
            pulse.animateTo(1f, tween(320))
            pulse.animateTo(0.25f, tween(260))
        }
        pulse.animateTo(0f, tween(300))
    }
    this
        .graphicsLayer {
            val lift = 1f + 0.07f * pulse.value
            scaleX = lift
            scaleY = lift
            shadowElevation = 14.dp.toPx() * pulse.value
        }
        .drawWithContent {
            drawContent()
            val p = pulse.value
            if (p <= 0.01f) return@drawWithContent
            val c = Offset(size.width / 2f, size.height / 2f)
            val reach = min(size.width, size.height) * 0.22f
            val head = reach * 0.35f
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            val color = Color.White.copy(alpha = 0.9f * p)
            // Four arrows pointing outward from the centre.
            for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
                val tip = Offset(c.x + dx * reach, c.y + dy * reach)
                val tail = Offset(c.x + dx * reach * 0.35f, c.y + dy * reach * 0.35f)
                drawLine(color, tail, tip, stroke.width, StrokeCap.Round)
                // Arrow head: two short strokes back from the tip.
                val px = -dy
                val py = dx
                drawLine(color, tip, Offset(tip.x - dx * head + px * head, tip.y - dy * head + py * head), stroke.width, StrokeCap.Round)
                drawLine(color, tip, Offset(tip.x - dx * head - px * head, tip.y - dy * head - py * head), stroke.width, StrokeCap.Round)
            }
        }
}
