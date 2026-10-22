package com.tileshell.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Where a hub opens from: the tile that was tapped (window coordinates), its colour and corner radius. */
data class HubOriginData(val rect: Rect, val color: Color, val cornerPx: Float)

/**
 * The tile a hub is about to open from. Start records it on a tile tap
 * ([set]); the hub that appears in response takes it ([take]) when it first
 * composes, and keeps it until it has fully closed, so closing runs the same
 * motion backwards into the tile. A hub opened any other way (quick panel,
 * another hub, an intent) finds nothing here and slides up as before.
 */
object HubOrigin {
    private const val VALID_MS = 1500L

    private var pending: HubOriginData? = null
    private var at = 0L

    /**
     * The colour of the plate a hub grows out of while a wallpaper is set (the wallpaper's most prominent colour,
     * the same as the launch plate's), or [Color.Unspecified] to use the tapped tile's own colour. Kept current
     * by Start.
     */
    @Volatile
    var wallpaperPlate: Color = Color.Unspecified

    fun set(rect: Rect, color: Color, cornerPx: Float) {
        pending = HubOriginData(rect, color, cornerPx)
        at = System.currentTimeMillis()
    }

    fun take(): HubOriginData? {
        val origin = pending
        val fresh = origin != null && System.currentTimeMillis() - at <= VALID_MS
        pending = null
        return if (fresh) origin?.let { if (wallpaperPlate.isSpecified) it.copy(color = wallpaperPlate) else it } else null
    }
}

/** Takes the tile a hub opens from once per open; call it after the hub's "fully closed" early return. */
@Composable
fun rememberHubOrigin(): HubOriginData? = remember { HubOrigin.take() }

/** The screen-space rectangle, as pieces, that a reveal at [p] shows. */
internal data class RevealFrame(
    val scale: Float,
    val translation: Offset,
    val clip: Rect,
    val corner: Float,
)

/**
 * The reveal at progress [p] (0 = the tile, 1 = the full panel of [width] x
 * [height]): the panel is scaled uniformly about its top-left corner so that
 * its centre travels from the tile's centre to the screen's, and clipped to
 * a rounded rectangle that grows from the tile's own rectangle to the whole
 * panel. Pure, so it is unit-tested.
 */
internal fun revealFrame(origin: HubOriginData, width: Float, height: Float, p: Float): RevealFrame {
    val t = p.coerceIn(0f, 1f)
    val tile = origin.rect
    val s0 = (tile.width / width).coerceIn(0.05f, 1f)
    val scale = s0 + (1f - s0) * t
    val centre = Offset(
        tile.center.x + (width / 2f - tile.center.x) * t,
        tile.center.y + (height / 2f - tile.center.y) * t,
    )
    val translation = Offset(centre.x - scale * width / 2f, centre.y - scale * height / 2f)
    // The clip in the panel's own (unscaled) coordinates, centred on the panel.
    val screenW = tile.width + (width - tile.width) * t
    val screenH = tile.height + (height - tile.height) * t
    val localW = (screenW / scale).coerceAtMost(width)
    val localH = (screenH / scale).coerceAtMost(height)
    val clip = Rect(
        left = (width - localW) / 2f,
        top = (height - localH) / 2f,
        right = (width + localW) / 2f,
        bottom = (height + localH) / 2f,
    )
    val corner = origin.cornerPx * (1f - t) / scale
    return RevealFrame(scale, translation, clip, corner)
}

private class RevealShape(private val frame: RevealFrame) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(frame.clip, CornerRadius(frame.corner)))
}

/**
 * A hub panel's open / close motion. With an [origin] the panel grows out of
 * that tile and shrinks back into it, the tile's colour fading away over the
 * first part of the grow; without one it slides up from the bottom as every
 * hub always did. [progress] is the hub's own animated 0..1.
 */
fun Modifier.hubReveal(origin: HubOriginData?, progress: () -> Float): Modifier {
    if (origin == null) {
        return graphicsLayer { translationY = size.height * (1f - progress()) }
    }
    return this
        .graphicsLayer {
            val p = progress()
            if (p >= 1f) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = 1f
                scaleY = 1f
                translationX = 0f
                translationY = 0f
                clip = false
                return@graphicsLayer
            }
            val frame = revealFrame(origin, size.width, size.height, p)
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = frame.scale
            scaleY = frame.scale
            translationX = frame.translation.x
            translationY = frame.translation.y
            shape = RevealShape(frame)
            clip = true
        }
        .drawWithContent {
            drawContent()
            val plate = (1f - progress() * 3f).coerceIn(0f, 1f)
            if (plate > 0f) drawRect(origin.color, alpha = plate)
        }
}
