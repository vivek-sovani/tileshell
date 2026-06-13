package com.tileshell.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/**
 * Windows-Phone 3D tile press feedback (FR-1.2): while pressed, the tile tilts
 * in 3D toward the contact point and dips slightly; on release it springs back
 * flat. Pressing a corner pushes that corner away from the viewer; the tilt
 * follows the finger as it moves.
 *
 * The gesture observer does not consume pointer events, so a `clickable` (or
 * long-press detector) on the same node keeps working.
 *
 * @param maxTiltDegrees tilt magnitude at the tile edges
 * @param pressedScale scale applied while pressed (the "dip")
 */
fun Modifier.tiltOnPress(
    maxTiltDegrees: Float = 12f,
    pressedScale: Float = 0.95f,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val rotationX = remember { Animatable(0f) }
    val rotationY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val releaseSpec = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun tiltToward(position: Offset) {
        val w = size.width.coerceAtLeast(1)
        val h = size.height.coerceAtLeast(1)
        // -1..1 from centre.
        val nx = (((position.x / w) - 0.5f) * 2f).coerceIn(-1f, 1f)
        val ny = (((position.y / h) - 0.5f) * 2f).coerceIn(-1f, 1f)
        scope.launch { rotationY.snapTo(nx * maxTiltDegrees) }
        scope.launch { rotationX.snapTo(-ny * maxTiltDegrees) }
    }

    fun release() {
        scope.launch { rotationX.animateTo(0f, releaseSpec) }
        scope.launch { rotationY.animateTo(0f, releaseSpec) }
        scope.launch { scale.animateTo(1f, releaseSpec) }
    }

    this
        .onSizeChanged { size = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scope.launch { scale.animateTo(pressedScale, spring(stiffness = Spring.StiffnessMedium)) }
                tiltToward(down.position)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    tiltToward(change.position)
                }
                release()
            }
        }
        .graphicsLayer {
            this.rotationX = rotationX.value
            this.rotationY = rotationY.value
            scaleX = scale.value
            scaleY = scale.value
        }
}
