package com.tileshell.feature.start

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.core.design.wallpaperBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * The full-screen wallpaper layer behind Start (FR-7). Renders either the
 * selected bundled [gradient] or a user-picked [customWallpaperUri] photo, and
 * applies the blur-wallpaper effect from the prototype CSS
 * (`#screen.blur #wall { filter: blur(18px) saturate(1.1); transform: scale(1.12) }`)
 * when [blur] is on. Blur is a no-op below API 31 (matching the folder overlay).
 * [alignX]/[alignY] (0..1) control which part of a custom photo is visible when the
 * image is cropped to fill the screen (0 = left/top edge, 0.5 = centred, 1 = right/bottom).
 * [zoom] (1 = none) additionally magnifies the cropped photo around the screen
 * centre, matching the pinch-zoom set in `WallpaperCropOverlay`.
 */
@Composable
fun WallpaperBackground(
    gradient: WallpaperGradient,
    customWallpaperUri: String?,
    blur: Boolean,
    alignX: Float = 0.5f,
    alignY: Float = 0.5f,
    zoom: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val layer = modifier
        .fillMaxSize()
        .graphicsLayer {
            if (blur) {
                scaleX = 1.12f
                scaleY = 1.12f
            }
        }
        .then(if (blur) Modifier.blur(18.dp) else Modifier)

    if (customWallpaperUri != null) {
        val image = rememberWallpaperBitmap(customWallpaperUri)
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(alignX * 2f - 1f, alignY * 2f - 1f),
                colorFilter = if (blur) ColorFilter.colorMatrix(saturation(1.1f)) else null,
                modifier = layer.graphicsLayer { scaleX = zoom; scaleY = zoom },
            )
            return
        }
        // URI unreadable (revoked/deleted) — fall through to the gradient.
    }
    Box(modifier = layer.wallpaperBackground(gradient))
}

/** Saturation [ColorMatrix] (1 = unchanged). */
private fun saturation(value: Float): ColorMatrix =
    ColorMatrix().apply { setToSaturation(value) }

/**
 * Paints [image] as a *window* onto a screen-anchored canvas for "wallpaper behind
 * tiles" mode (FR-7 follow-up): the photo is cover-scaled to fill a virtual
 * [fullWidth]×[fullHeight] rectangle (the screen) and this tile shows the slice at
 * its current screen [origin]. [origin] is read in the draw phase, so the photo stays
 * fixed to the screen while the tiles scroll over it (WP parallax). [darkBase] fills
 * first, so any tile beyond the photo stays dark rather than empty.
 * [alignX]/[alignY] shift the photo's position within the screen rectangle (same
 * semantics as [WallpaperBackground]): 0 = left/top edge, 0.5 = centred, 1 = right/bottom.
 * [zoom] (1 = none) additionally magnifies the photo around the *screen's* centre
 * (not this tile's own centre), same semantics as [WallpaperBackground] so every
 * tile's window magnifies consistently.
 */
fun Modifier.photoWindow(
    image: ImageBitmap,
    fullWidth: Float,
    fullHeight: Float,
    darkBase: Color,
    origin: () -> Offset,
    alignX: Float = 0.5f,
    alignY: Float = 0.5f,
    zoom: Float = 1f,
): Modifier = drawBehind {
    drawRect(darkBase)
    val imgW = image.width.toFloat()
    val imgH = image.height.toFloat()
    if (imgW <= 0f || imgH <= 0f) return@drawBehind
    val o = origin()
    val scale = max(fullWidth / imgW, fullHeight / imgH)
    val dstW = imgW * scale
    val dstH = imgH * scale
    // alignX/Y in [0,1] map into the photo's overflow space: 0 = left/top edge
    // visible, 0.5 = centred (the original behaviour), 1 = right/bottom edge.
    val left = alignX * (fullWidth - dstW) - o.x
    val top  = alignY * (fullHeight - dstH) - o.y
    // The screen's centre, expressed in this tile's local draw coordinates.
    val zoomPivot = Offset(fullWidth / 2f - o.x, fullHeight / 2f - o.y)
    clipRect {
        scale(zoom, pivot = zoomPivot) {
            translate(left = left, top = top) {
                scale(scale, pivot = Offset.Zero) {
                    drawImage(image)
                }
            }
        }
    }
}

/**
 * Decodes the content URI off the main thread, down-sampled so a large camera
 * photo (12–50 MP) doesn't load as a full-resolution bitmap and cause GC
 * pressure / flickering. Targets the shorter decoded side at ≥ 1 080 px (enough
 * to fill any current phone screen without visible quality loss). Returns null
 * while loading or if the URI can't be read. Reloads only when the URI changes.
 */
@Composable
fun rememberWallpaperBitmap(uri: String): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decodeWallpaper(context, uri) }
    }
    return image
}

private fun decodeWallpaper(context: Context, uri: String): ImageBitmap? = runCatching {
    val parsed = Uri.parse(uri)
    // First pass: read dimensions without allocating pixels.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = wallpaperSampleSize(bounds.outWidth, bounds.outHeight)
    }
    // Second pass: decode at the computed sample size.
    val bitmap = context.contentResolver.openInputStream(parsed)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)
    } ?: return@runCatching null

    // Third pass: read EXIF orientation so landscape photos stored rotated display correctly.
    val orientation = context.contentResolver.openInputStream(parsed)?.use { stream ->
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90  -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val rotated = if (degrees != 0f) {
        val matrix = Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    } else {
        bitmap
    }
    rotated.asImageBitmap()
}.getOrNull()

/**
 * Largest power-of-two sample size that keeps the shorter decoded side ≥ 1 080 px,
 * matching a 1080p phone's shorter portrait dimension. A 12 MP photo (4 032 × 3 024)
 * decodes at 2×  →  2 016 × 1 512 (~12 MB); a 50 MP photo (8 064 × 6 048) decodes
 * at 4×  →  2 016 × 1 512 as well — a massive reduction from the raw bitmap sizes.
 */
internal fun wallpaperSampleSize(width: Int, height: Int): Int {
    val shorter = minOf(width, height)
    if (shorter <= 0) return 1
    var sample = 1
    while (shorter / (sample * 2) >= 1080) sample *= 2
    return sample
}
