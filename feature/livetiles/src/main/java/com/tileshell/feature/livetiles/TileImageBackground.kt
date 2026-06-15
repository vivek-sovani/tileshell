package com.tileshell.feature.livetiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Draws [image] cropped to fill the tile behind [content], under a dark vertical
 * scrim so the white text/controls stay legible over it. Used to surface a
 * notification's picture / contact photo (FR-2) and a track's album art (FR-2.3)
 * on their live faces. When [image] is null it simply renders [content] over the
 * tile's existing accent fill — so a notification/track with no artwork looks
 * exactly as before.
 */
@Composable
fun TileImageBackground(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Scrim: light at the top (so the app icon reads), heavier at the
            // bottom where the sender/snippet or title/artist sit.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x22000000),
                        0.5f to Color(0x66000000),
                        1f to Color(0xCC000000),
                    ),
                ),
            )
        }
        content()
    }
}
