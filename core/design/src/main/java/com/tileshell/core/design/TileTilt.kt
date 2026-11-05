package com.tileshell.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The live state of one tile's press: how far it leans (degrees about each axis) and how far it has dipped. Owned by
 * [tiltOnPress]; shared with [pressParallax] so a tile's face can float against the lean while the plate turns.
 */
@Stable
class TilePressState {
    internal val rotationX = Animatable(0f)
    internal val rotationY = Animatable(0f)
    internal val scale = Animatable(1f)

    /** The lean as −1..1 on each axis (x: toward the right edge, y: toward the bottom edge) for a tilt of [maxDegrees]. */
    internal fun lean(maxDegrees: Float): Offset =
        Offset(rotationY.value / maxDegrees, -rotationX.value / maxDegrees)

    /** 0 at rest, 1 fully pressed. */
    internal fun pressed(pressedScale: Float): Float = pressedness(scale.value, pressedScale)
}

/** 0 (resting, scale 1) .. 1 (fully dipped to [pressedScale]); pure so it can be tested. */
internal fun pressedness(scale: Float, pressedScale: Float): Float {
    val span = 1f - pressedScale
    if (span <= 0f) return 0f
    return ((1f - scale) / span).coerceIn(0f, 1f)
}

/**
 * Where the highlight sits on a tile leaning by ([nx], [ny]) (each −1..1): it slides away from the finger, as a light
 * fixed above the screen catches the face that is tipping up. Returned as fractions of the tile's width and height.
 */
internal fun highlightCentre(nx: Float, ny: Float): Offset =
    Offset(0.5f - nx.coerceIn(-1f, 1f) * 0.4f, 0.5f - ny.coerceIn(-1f, 1f) * 0.4f)

/** How far, in dp, the face floats against a lean of ([nx], [ny]); at most [maxShift] on an axis. */
internal fun parallaxShift(nx: Float, ny: Float, maxShift: Float): Offset =
    Offset(-nx.coerceIn(-1f, 1f) * maxShift, -ny.coerceIn(-1f, 1f) * maxShift)

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
 * @param lighting also draws a highlight that slides as the tile leans, a light edge on one side and shade on the
 *   other (all fading in with the press). Draw-only, over whatever is below this modifier.
 * @param cornerRadius the tile's corner radius, so the lighting follows its shape
 * @param state the press state, when something else (a [pressParallax]) needs to follow it
 */
fun Modifier.tiltOnPress(
    maxTiltDegrees: Float = 12f,
    pressedScale: Float = 0.95f,
    lighting: Boolean = false,
    cornerRadius: Dp = 0.dp,
    state: TilePressState? = null,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val press = state ?: remember { TilePressState() }
    val rotationX = press.rotationX
    val rotationY = press.rotationY
    val scale = press.scale
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
        .then(
            if (!lighting) {
                Modifier
            } else {
                Modifier.drawWithContent {
                    drawContent()
                    val strength = press.pressed(pressedScale)
                    if (strength <= 0.01f) return@drawWithContent
                    val lean = press.lean(maxTiltDegrees)
                    val centre = highlightCentre(lean.x, lean.y)
                    val radius = CornerRadius(cornerRadius.toPx())
                    // The plate: a soft pool of light where the tile tips up, over a gentle light-to-shade slope.
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.10f * strength),
                            1f to Color.Black.copy(alpha = 0.18f * strength),
                            start = Offset.Zero,
                            end = Offset(this.size.width, this.size.height),
                        ),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.22f * strength),
                            1f to Color.Transparent,
                            center = Offset(this.size.width * centre.x, this.size.height * centre.y),
                            radius = maxOf(this.size.width, this.size.height) * 0.62f,
                        ),
                        cornerRadius = radius,
                    )
                    // The rim: a light edge on the side leaning up, shade on the side leaning down.
                    val toward = Offset(lean.x, lean.y)
                    val from = Offset(
                        this.size.width * (0.5f - toward.x * 0.5f),
                        this.size.height * (0.5f - toward.y * 0.5f),
                    )
                    val to = Offset(
                        this.size.width * (0.5f + toward.x * 0.5f),
                        this.size.height * (0.5f + toward.y * 0.5f),
                    )
                    val line = 1.dp.toPx()
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.35f * strength),
                            1f to Color.Black.copy(alpha = 0.28f * strength),
                            start = from,
                            end = if (to == from) Offset(from.x + 1f, from.y + 1f) else to,
                        ),
                        topLeft = Offset(line / 2f, line / 2f),
                        size = androidx.compose.ui.geometry.Size(this.size.width - line, this.size.height - line),
                        cornerRadius = CornerRadius(maxOf(0f, radius.x - line / 2f)),
                        style = Stroke(width = line),
                    )
                }
            },
        )
}

/**
 * Floats whatever this modifier wraps (the tile's face, when placed after the plate's background and border) a few dp
 * against the lean of [state], so it reads as sitting above the plate. Draw-only: nothing about layout or hit-testing
 * moves, and it shifts nothing at rest.
 */
fun Modifier.pressParallax(
    state: TilePressState,
    maxTiltDegrees: Float = 12f,
    maxShift: Dp = 6.dp,
): Modifier = drawWithContent {
    val lean = state.lean(maxTiltDegrees)
    if (lean == Offset.Zero) {
        drawContent()
    } else {
        val shift = parallaxShift(lean.x, lean.y, maxShift.toPx())
        translate(shift.x, shift.y) { this@drawWithContent.drawContent() }
    }
}
