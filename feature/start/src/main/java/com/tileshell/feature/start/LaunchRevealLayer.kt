package com.tileshell.feature.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tileshell.core.data.LaunchOrigin
import com.tileshell.feature.livetiles.rememberAppIconBitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A launched app grows out of its tile, and shrinks back into it when you come
 * home. The system's own launch animation can't be replaced by a launcher that
 * isn't the phone maker's (scale-up, clip-reveal and custom animations were
 * all ignored), so TileShell draws this itself: the tile's colour plate, with
 * the app's icon, fills the screen while the app starts, stays there while the
 * app is in front, and goes back into the tile when TileShell is resumed.
 * [LaunchOrigin.onLaunched] starts it; it never takes touches.
 */
@Composable
internal fun LaunchRevealLayer() {
    var launched by remember { mutableStateOf<LaunchOrigin.Launched?>(null) }
    val progress = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        LaunchOrigin.onLaunched = { launched = it }
        onDispose {
            lifecycle.removeObserver(observer)
            LaunchOrigin.onLaunched = null
        }
    }

    LaunchedEffect(launched) {
        if (launched == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(EXPAND_MS, easing = EASE))
        // The app takes over (we pause) within a moment; if it never does, go back to the tile.
        withTimeoutOrNull(HOLD_MS) { snapshotFlow { resumed }.first { !it } }
        // Stay full while the app is in front, then shrink into the tile once we are resumed again.
        snapshotFlow { resumed }.first { it }
        progress.animateTo(0f, tween(SHRINK_MS, easing = EASE))
        launched = null
    }

    val current = launched ?: return
    val icon = rememberAppIconBitmap(current.packageName, 192)
    val origin = current.origin
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p <= 0f) return@Canvas
        val tile = origin.bounds
        val left = tile.left * (1f - p)
        val top = tile.top * (1f - p)
        val right = tile.right + (size.width - tile.right) * p
        val bottom = tile.bottom + (size.height - tile.bottom) * p
        val corner = origin.cornerPx * (1f - p)
        val plate = if (origin.color != 0) Color(origin.color) else Color(0xFF2B78E4)
        drawRoundRect(plate, Offset(left, top), Size(right - left, bottom - top), CornerRadius(corner))
        icon?.let {
            val side = (tile.width.coerceAtMost(tile.height) * 0.5f) * (1f - p) + 72.dp.toPx() * p
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            drawImage(
                image = it,
                dstOffset = IntOffset((cx - side / 2f).toInt(), (cy - side / 2f).toInt()),
                dstSize = IntSize(side.toInt(), side.toInt()),
            )
        }
    }
}

private const val EXPAND_MS = 140
private const val SHRINK_MS = 260
private const val HOLD_MS = 1500L
private val EASE = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
