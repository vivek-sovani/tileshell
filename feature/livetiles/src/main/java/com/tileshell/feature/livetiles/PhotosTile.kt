package com.tileshell.feature.livetiles

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val SLIDE_MS = 3_000L

/**
 * The live photos tile (FR-2): a cross-fade slideshow of the user's picked photos
 * (prototype `liveFace('photos')`, `slideshowStep`). It never flips — the photos
 * face is the prototype's `data-noflip` — so [flipped] is irrelevant here. While
 * [active], the visible photo advances every ~3.0 s with an ~0.8 s cross-fade. A
 * shadowed "photos" label sits bottom-left. With no photos picked it renders
 * [fallback] (the static glyph); an individual unreadable URI just shows the
 * tile's accent fill for that step.
 */
@Composable
fun PhotosTileFace(
    active: Boolean,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { PhotosStore.create(context) }
    val uris = store.data.collectAsState(initial = PhotosData()).value.uris
    if (uris.isEmpty()) return fallback()

    var index by remember(uris) { mutableIntStateOf(0) }
    LaunchedEffect(active, uris) {
        if (!active || uris.size < 2) return@LaunchedEffect
        while (true) {
            delay(SLIDE_MS)
            index = (index + 1) % uris.size
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Cross-fade between photos (prototype .photoslab opacity .8s).
        Crossfade(
            targetState = index.coerceIn(0, uris.lastIndex),
            animationSpec = tween(800),
            label = "slide",
            modifier = Modifier.fillMaxSize(),
        ) { i ->
            val bitmap = rememberTileBitmap(uris[i])
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = "photos",
            color = Color.White,
            fontSize = 13.sp,
            style = TextStyle(
                shadow = Shadow(Color(0x99000000), Offset(0f, 1f), blurRadius = 4f),
            ),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 11.dp, bottom = 9.dp),
        )
    }
}
