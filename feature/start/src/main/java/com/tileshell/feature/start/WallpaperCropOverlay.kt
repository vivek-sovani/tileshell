package com.tileshell.feature.start

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.TileColorSource
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.feature.livetiles.NotificationSnapshot
import com.tileshell.feature.start.feed.rememberFeedPalette

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 3f

/**
 * Wraps [WallpaperCropOverlay] with a fade-out on close (confirm or cancel)
 * instead of the hard instant-removal a plain `if (uri != null)` gives —
 * "apply" should read as a transition, not a cut. Visible whenever [uri] is
 * non-null. [WallpaperCropOverlay] itself only accepts a non-null `uri`, but
 * by the time the exit animation runs [uri] has already gone back to null
 * (that's *what triggered* the exit) — so this latches the last non-null
 * value and keeps rendering the real overlay (photo + tiles) through the
 * whole fade instead of the content vanishing the instant the animation
 * starts. Confirming already writes the new wallpaper into settings
 * immediately (see the call sites), so what's fading out and what's
 * underneath show the same thing — a plain alpha fade between two
 * visually-identical frames reads as no animation at all, which is exactly
 * why this pairs it with a scale-down: shrinking is a visible transform
 * regardless of whether the pixels underneath already match, so "applying"
 * still reads as a deliberate dismiss motion instead of nothing happening.
 */
@Composable
fun WallpaperCropOverlayHost(
    uri: String?,
    tiles: List<TileModel>,
    settings: LauncherSettings,
    accent: Color,
    wallpaperGradient: WallpaperGradient,
    notifications: NotificationSnapshot,
    darkTheme: Boolean,
    glassLine: Color,
    onConfirm: (alignX: Float, alignY: Float, zoom: Float) -> Unit,
    onCancel: () -> Unit,
    initialAlignX: Float = 0.5f,
    initialAlignY: Float = 0.5f,
    initialZoom: Float = 1f,
    rightHalf: Boolean = false,
) {
    var latchedUri by remember { mutableStateOf<String?>(null) }
    if (uri != null) latchedUri = uri
    AnimatedVisibility(
        visible = uri != null,
        exit = fadeOut(tween(360)) + scaleOut(targetScale = 0.92f, animationSpec = tween(360)),
    ) {
        latchedUri?.let { resolvedUri ->
            WallpaperCropOverlay(
                uri = resolvedUri,
                tiles = tiles,
                settings = settings,
                accent = accent,
                wallpaperGradient = wallpaperGradient,
                notifications = notifications,
                darkTheme = darkTheme,
                glassLine = glassLine,
                onConfirm = onConfirm,
                onCancel = onCancel,
                initialAlignX = initialAlignX,
                initialAlignY = initialAlignY,
                initialZoom = initialZoom,
                rightHalf = rightHalf,
            )
        }
    }
}

/**
 * Full-screen overlay shown immediately after the user picks a custom wallpaper
 * photo. Rather than just showing the bare photo, the whole screen behind the
 * drag/pinch gesture IS the real Start screen — real tiles, real icons — on
 * this photo (see [WallpaperStartPreview]), the way OneUI and most Android
 * launchers preview a wallpaper pick, so positioning it shows exactly how the
 * actual grid will read on it. Drag to reposition and pinch to zoom which part
 * of the image is used. Tapping "use this" calls [onConfirm] with the chosen
 * [alignX]/[alignY] (0..1) and [zoom] (1..3). Tapping "cancel" calls [onCancel]
 * without changing anything. Normally reached through [WallpaperCropOverlayHost],
 * which adds the fade-out-on-close transition around this.
 */
@Composable
fun WallpaperCropOverlay(
    uri: String,
    tiles: List<TileModel>,
    settings: LauncherSettings,
    accent: Color,
    wallpaperGradient: WallpaperGradient,
    notifications: NotificationSnapshot,
    darkTheme: Boolean,
    glassLine: Color,
    onConfirm: (alignX: Float, alignY: Float, zoom: Float) -> Unit,
    onCancel: () -> Unit,
    initialAlignX: Float = 0.5f,
    initialAlignY: Float = 0.5f,
    initialZoom: Float = 1f,
    rightHalf: Boolean = false,
) {
    val image = rememberWallpaperBitmap(uri)
    // Seed from the current focal point so re-adjusting resumes where it left off;
    // the user drags to reposition and pinches to zoom.
    var alignX by remember(uri) { mutableStateOf(initialAlignX) }
    var alignY by remember(uri) { mutableStateOf(initialAlignY) }
    var zoomLevel by remember(uri) { mutableStateOf(initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)) }

    // The wallpaper-derived accent this *candidate* photo would produce — recomputed
    // from it directly (not the already-applied wallpaper's own accent, which is
    // still the old photo/gradient until "use this" is confirmed), same extraction
    // the feed page/Quick Panel use. Only fed into the preview's tile colours when
    // that source is actually selected — see WallpaperStartPreview.
    val candidateWallpaperAccent = if (settings.tileColorSource == TileColorSource.WALLPAPER_ACCENT && image != null) {
        rememberFeedPalette(image, wallpaperGradient, accent).second
    } else {
        null
    }

    // Back gesture cancels the crop (only composed while active, so always on).
    BackHandler(enabled = true) { onCancel() }

    SheetStage(rightHalf = rightHalf) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        if (image != null) {
            WallpaperStartPreview(
                tiles = tiles,
                settings = settings,
                accent = accent,
                wallpaper = wallpaperGradient,
                wallpaperAccent = candidateWallpaperAccent,
                notifications = notifications,
                darkTheme = darkTheme,
                glassLine = glassLine,
                wallpaperImage = image,
                alignX = alignX,
                alignY = alignY,
                zoom = zoomLevel,
                modifier = Modifier
                    .fillMaxSize()
                    // Keyed only on stable inputs (image/screen size) — never on
                    // alignX/alignY/zoomLevel, which change *during* the very
                    // gesture this detector is tracking. Re-keying on those would
                    // cancel and restart the gesture mid-pinch/drag. Every tile
                    // WallpaperStartPreview renders is `readOnly` (see TileView),
                    // so none of them intercept this gesture.
                    .pointerInput(image, screenW, screenH) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            zoomLevel = (zoomLevel * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            // Recomputed fresh every callback from the *current*
                            // zoomLevel, so the pan range grows as soon as the
                            // user starts zooming in — previously this was
                            // computed once from the zoom-1 cover fit only, so
                            // whichever axis had zero overflow there stayed
                            // permanently un-pannable no matter how far zoomed in.
                            val geo = wallpaperCropGeometry(
                                image.width.toFloat(), image.height.toFloat(), screenW, screenH, alignX, alignY, zoomLevel,
                            )
                            val slackX = geo.dstWidth - screenW
                            val slackY = geo.dstHeight - screenH
                            // Dragging right/down moves the photo right/down, showing
                            // the left/top part → alignX/Y decreases. The pan delta is
                            // in raw (unzoomed) screen px, but at higher zoom the same
                            // finger travel reveals less of the underlying image.
                            if (slackX > 0f)
                                alignX = (alignX - (pan.x / zoomLevel) / slackX).coerceIn(0f, 1f)
                            if (slackY > 0f)
                                alignY = (alignY - (pan.y / zoomLevel) / slackY).coerceIn(0f, 1f)
                        }
                    },
            )
        }

        // Bottom control bar.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "cancel",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (image == null) "loading…" else "drag to position · pinch to zoom",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "use this",
                    color = if (image != null) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(
                        enabled = image != null,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onConfirm(alignX, alignY, zoomLevel) },
                    ),
                )
            }
        }
        }
    }
}
