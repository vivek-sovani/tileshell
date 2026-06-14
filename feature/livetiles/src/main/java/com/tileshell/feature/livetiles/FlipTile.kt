package com.tileshell.feature.livetiles

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Prototype flip easing: `cubic-bezier(.5,.05,.2,1)` over 500 ms (styles.css). */
private val FlipEasing = CubicBezierEasing(0.5f, 0.05f, 0.2f, 1f)
private const val FLIP_DURATION_MS = 500

/**
 * A live tile that turns between a [front] and a [back] face on an X-axis 3D
 * flip (FR-2). [flipped] drives the half-turn: the container rotates 0°→180°,
 * the front shows for the first quarter-turn and the back — counter-rotated so
 * it reads upright — for the second. `cameraDistance` keeps the perspective
 * shallow so the turn reads like the WP tile rather than a steep page-fold.
 *
 * (The HTML prototype fakes this with a vertical slide because CSS 3D backface
 * was unreliable; Compose handles the real rotation, so we use it — closer to
 * the actual Windows Phone flip. See docs/DECISIONS.md S20.)
 */
@Composable
fun FlipTile(
    flipped: Boolean,
    front: @Composable BoxScope.() -> Unit,
    back: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(FLIP_DURATION_MS, easing = FlipEasing),
        label = "flip",
    )
    Box(
        modifier = modifier.graphicsLayer {
            rotationX = rotation
            cameraDistance = 16f * density
        },
    ) {
        if (rotation <= 90f) {
            Box(modifier = Modifier.fillMaxSize(), content = front)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationX = 180f },
                content = back,
            )
        }
    }
}

/**
 * Which live tiles are currently turned to their back face. Mutated only by
 * [rememberFlipState]'s scheduler; tiles read it by id during composition.
 */
@Stable
class FlipState internal constructor() {
    internal val flipped = mutableStateMapOf<String, Boolean>()

    fun isFlipped(id: String): Boolean = flipped[id] == true
}

/**
 * The random-tile flip scheduler (FR-2): every ~2.6 s while [active], one of the
 * visible flippable [liveIds] is toggled, exactly as the prototype's
 * `setInterval(flipOne, 2600)`. The loop is suspended whenever [active] is false
 * (edit mode, off-screen, screen off, battery saver, animations off — gated by
 * [rememberLiveTilesActive]); the currently-shown faces freeze in place and
 * resume turning when it comes back, so nothing snaps on return.
 *
 * Ids that scroll out of [liveIds] are pruned so their flip state does not leak
 * back if the same tile reappears.
 */
@Composable
fun rememberFlipState(liveIds: List<String>, active: Boolean): FlipState {
    val state = remember { FlipState() }

    LaunchedEffect(liveIds) {
        state.flipped.keys.retainAll(liveIds.toHashSet())
    }

    LaunchedEffect(active, liveIds) {
        if (!active || liveIds.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(2600)
            val target = pickFlipTarget(liveIds) ?: continue
            state.flipped[target] = !(state.flipped[target] ?: false)
        }
    }

    return state
}
