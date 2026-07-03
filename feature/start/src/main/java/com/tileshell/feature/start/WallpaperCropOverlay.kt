package com.tileshell.feature.start

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.SheetStage
import kotlin.math.max

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 3f

/**
 * Full-screen overlay shown immediately after the user picks a custom wallpaper
 * photo. Displays the photo cover-scaled to fill the screen and lets the user
 * drag to reposition and pinch to zoom which part of the image is used as the
 * wallpaper. Tapping "use this" calls [onConfirm] with the chosen
 * [alignX]/[alignY] (0..1) and [zoom] (1..3). Tapping "cancel" calls [onCancel]
 * without changing anything.
 */
@Composable
fun WallpaperCropOverlay(
    uri: String,
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

        // Overflow in px for each axis: how many px the cover-scaled photo extends
        // beyond the screen, determining how far the user can pan in that direction.
        val overflowX: Float
        val overflowY: Float
        if (image != null && screenW > 0f && screenH > 0f) {
            val imgW = image.width.toFloat()
            val imgH = image.height.toFloat()
            val scale = if (imgW > 0f && imgH > 0f) max(screenW / imgW, screenH / imgH) else 1f
            overflowX = max(0f, imgW * scale - screenW)
            overflowY = max(0f, imgH * scale - screenH)
        } else {
            overflowX = 0f
            overflowY = 0f
        }

        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(alignX * 2f - 1f, alignY * 2f - 1f),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(overflowX, overflowY) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            zoomLevel = (zoomLevel * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            // Dragging right/down moves the photo right/down, showing
                            // the left/top part → alignX/Y decreases. The pan delta is
                            // in raw (unzoomed) screen px, but at higher zoom the same
                            // finger travel reveals less of the underlying image.
                            if (overflowX > 0f)
                                alignX = (alignX - (pan.x / zoomLevel) / overflowX).coerceIn(0f, 1f)
                            if (overflowY > 0f)
                                alignY = (alignY - (pan.y / zoomLevel) / overflowY).coerceIn(0f, 1f)
                        }
                    }
                    .graphicsLayer { scaleX = zoomLevel; scaleY = zoomLevel },
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
