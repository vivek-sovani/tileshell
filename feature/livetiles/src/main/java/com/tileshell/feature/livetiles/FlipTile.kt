package com.tileshell.feature.livetiles

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** Flip easing: quick off the mark, settling at the end — about 0.4 s end to end, as timed from a real Lumia. */
private val FlipEasing = CubicBezierEasing(0.42f, 0f, 0.3f, 1f)
private const val FLIP_DURATION_MS = 400

/**
 * A live tile that turns between a [front] and a [back] face (FR-2), the way a
 * Windows Phone tile does: the *whole tile* (plate, outline and face) turns
 * about its horizontal centre line, seen by one camera at the middle of the
 * screen — a global perspective, not one per tile (see [tileFlip]). [flipped]
 * drives the half-turn: the front shows for the first quarter-turn and the back
 * — upright — for the second.
 *
 * Inside a `TileView` (which provides a [TileFlipHost] around the whole tile)
 * this only drives the host's rotation and swaps the faces; with no host (a
 * glance card, a preview) it turns its own box instead.
 */
@Composable
fun FlipTile(
    flipped: Boolean,
    front: @Composable BoxScope.() -> Unit,
    back: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val provided = LocalTileFlipHost.current
    val host = provided ?: remember { TileFlipHost() }
    // The first composition snaps to its face (a tile that appears already
    // flipped must not spin); every later change turns.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(flipped) {
        val target = if (flipped) 180f else 0f
        if (!settled) {
            settled = true
            host.rotation.snapTo(target)
        } else {
            host.rotation.animateTo(target, tween(FLIP_DURATION_MS, easing = FlipEasing))
        }
    }
    val showBack by remember(host) { derivedStateOf { host.rotation.value > 90f } }
    Box(modifier = if (provided == null) modifier.tileFlip(host) else modifier) {
        if (!showBack) {
            Box(modifier = Modifier.fillMaxSize(), content = front)
        } else {
            Box(modifier = Modifier.fillMaxSize(), content = back)
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
