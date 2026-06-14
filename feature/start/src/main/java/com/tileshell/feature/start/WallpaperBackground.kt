package com.tileshell.feature.start

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.core.design.wallpaperBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The full-screen wallpaper layer behind Start (FR-7). Renders either the
 * selected bundled [gradient] or a user-picked [customWallpaperUri] photo, and
 * applies the blur-wallpaper effect from the prototype CSS
 * (`#screen.blur #wall { filter: blur(18px) saturate(1.1); transform: scale(1.12) }`)
 * when [blur] is on. Blur is a no-op below API 31 (matching the folder overlay).
 */
@Composable
fun WallpaperBackground(
    gradient: WallpaperGradient,
    customWallpaperUri: String?,
    blur: Boolean,
    modifier: Modifier = Modifier,
) {
    // Blur/scale/saturate are tied together in the prototype's `.blur #wall` rule.
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
                // saturate(1.1) from the blur filter — only visible on photos.
                colorFilter = if (blur) ColorFilter.colorMatrix(saturation(1.1f)) else null,
                modifier = layer,
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
 * Decodes the content URI off the main thread, re-loading only when the URI
 * changes. Returns null while loading or if the URI can't be read.
 */
@Composable
private fun rememberWallpaperBitmap(uri: String): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return image
}
