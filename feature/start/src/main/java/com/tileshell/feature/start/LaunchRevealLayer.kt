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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.palette.graphics.Palette
import com.tileshell.core.data.LaunchOrigin
import com.tileshell.feature.livetiles.rememberAppIconBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A launched app starts over a full-screen plate with its icon in the middle,
 * which appears the moment you tap, so only the system's own window animation
 * is seen moving; the plate stays while the app is in front and, when
 * TileShell is resumed, shrinks back into the tile, taking the tile's colour
 * as it goes. The plate is the wallpaper's most prominent colour when a
 * wallpaper is set ([wallpaperColor], or for a photo [wallpaperPhoto]'s own
 * most prominent colour), else the tile's own colour.
 * The system's launch animation can't be replaced by a launcher that isn't
 * the phone maker's (scale-up, clip-reveal and custom animations were all
 * ignored), so TileShell draws this itself. [LaunchOrigin.onLaunched] starts
 * it; it never takes touches.
 */
@Composable
internal fun LaunchRevealLayer(wallpaperFallback: Color, wallpaperPhoto: ImageBitmap?) {
    val wallpaperColor = rememberPlateColor(wallpaperPhoto, wallpaperFallback)
    var launched by remember { mutableStateOf<LaunchOrigin.Launched?>(null) }
    // How far the plate is from the tile (1 = the whole screen) and how visible it is.
    val progress = remember { Animatable(1f) }
    val fade = remember { Animatable(0f) }
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
        progress.snapTo(1f)
        fade.snapTo(0f)
        fade.animateTo(1f, tween(FADE_IN_MS))
        // The app takes over (we pause) within a moment; if it never does, go back to the tile.
        withTimeoutOrNull(HOLD_MS) { snapshotFlow { resumed }.first { !it } }
        // Stay full while the app is in front, then shrink into the tile once we are resumed again.
        snapshotFlow { resumed }.first { it }
        progress.animateTo(0f, tween(SHRINK_MS, easing = EASE))
        fade.snapTo(0f)
        launched = null
    }

    val current = launched ?: return
    val icon = rememberAppIconBitmap(current.packageName, 192)
    val origin = current.origin
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val shown = fade.value
        if (shown <= 0f) return@Canvas
        val tile = origin.bounds
        val left = tile.left * (1f - p)
        val top = tile.top * (1f - p)
        val right = tile.right + (size.width - tile.right) * p
        val bottom = tile.bottom + (size.height - tile.bottom) * p
        val corner = origin.cornerPx * (1f - p)
        // The wallpaper's colour (else the tile's) on the full screen, the tile's own colour once back in the tile.
        val tileColor = if (origin.color != 0) Color(origin.color) else wallpaperColor
        val full = if (wallpaperColor.isSpecified) wallpaperColor else tileColor
        val plate = lerp(tileColor, full, p)
        drawRoundRect(plate, Offset(left, top), Size(right - left, bottom - top), CornerRadius(corner), alpha = shown)
        icon?.let {
            val side = (tile.width.coerceAtMost(tile.height) * 0.5f) * (1f - p) + 72.dp.toPx() * p
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            drawImage(
                image = it,
                alpha = shown,
                dstOffset = IntOffset((cx - side / 2f).toInt(), (cy - side / 2f).toInt()),
                dstSize = IntSize(side.toInt(), side.toInt()),
            )
        }
    }
}

/**
 * The wallpaper colour for the plate: for a photo its most prominent colour (see [prominentColor]), computed off
 * the main thread; otherwise [fallback] (a gradient's colour, or [Color.Unspecified] without a wallpaper).
 */
@Composable
private fun rememberPlateColor(photo: ImageBitmap?, fallback: Color): Color {
    var color by remember(photo, fallback) { mutableStateOf(fallback) }
    LaunchedEffect(photo, fallback) {
        if (photo != null && fallback.isSpecified) {
            color = withContext(Dispatchers.Default) { prominentColor(photo) } ?: fallback
        }
    }
    return color
}

/**
 * The colour a photo is most made of that is still a colour: every Palette swatch scored by how much of the photo
 * it covers, weighted by its saturation so a large grey or black area doesn't win over the colour you notice.
 */
internal fun prominentColor(photo: ImageBitmap): Color? {
    val palette = runCatching { Palette.from(photo.asAndroidBitmap()).maximumColorCount(24).generate() }.getOrNull()
        ?: return null
    val best = palette.swatches.maxByOrNull { it.population * (SATURATION_FLOOR + it.hsl[1]) } ?: return null
    return Color(best.rgb)
}

private const val SATURATION_FLOOR = 0.25f
private const val FADE_IN_MS = 90
private const val SHRINK_MS = 260
private const val HOLD_MS = 1500L
private val EASE = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
