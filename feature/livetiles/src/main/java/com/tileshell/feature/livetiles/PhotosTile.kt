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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import kotlinx.coroutines.delay

private const val SLIDE_MS = 3_000L

// How long the photo slideshow shows before turning to a pending notification, and
// how long the notification stays up before turning back. Only used while the tile's
// package has an active notification (see [PhotosTileFace]).
private const val PHOTO_HOLD_MS = 4_000L
private const val NOTIF_HOLD_MS = 3_000L

/**
 * The live photos tile (FR-2): a cross-fade slideshow of the user's picked photos
 * (prototype `liveFace('photos')`, `slideshowStep`). Normally it never flips — the
 * photos face is the prototype's `data-noflip`, so it is excluded from the random
 * flip scheduler (`LiveFace.PHOTOS.flips == false`).
 *
 * **Notification back-face:** when the tile's gallery app ([packageName]) has an
 * active notification, the tile self-flips between the photo slideshow (front) and
 * an Android-notification-style row (back), so a gallery notification surfaces
 * without losing the slideshow. This flip is self-managed (not driven by the shared
 * scheduler) and happens *only* while a notification is pending — with nothing
 * pending the tile stays on the slideshow and never turns. When no photos are
 * picked it renders [fallback] (the static glyph), unchanged.
 *
 * Standalone mode ([forcedIndex] = null, default): while [active] the photo advances
 * every ~3.0 s with an ~0.8 s cross-fade, and the notification flip (above) applies.
 *
 * Stack mode ([forcedIndex] != null): the internal slideshow timer is suppressed and
 * the notification flip is disabled (a stack member is already a rotating carousel
 * cell). The displayed photo is determined entirely by the caller-supplied
 * [forcedIndex], hoisted into [StackTileContent] so it survives AnimatedContent
 * composition recycling, taken modulo the URI count.
 */
@Composable
fun PhotosTileFace(
    active: Boolean,
    fallback: @Composable () -> Unit,
    packageName: String = "",
    size: TileSize = TileSize.WIDE,
    forcedIndex: Int? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { PhotosStore.create(context) }
    val uris = store.data.collectAsState(initial = PhotosData()).value.uris
    if (uris.isEmpty()) return fallback()

    // In stack mode the index is driven by forcedIndex (keyed in remember so the
    // correct photo shows immediately on composition, with no first-frame flash).
    // In standalone mode the index starts at 0 and advances via the timer below.
    var index by remember(uris, forcedIndex) {
        mutableIntStateOf(
            if (forcedIndex != null && uris.isNotEmpty()) forcedIndex % uris.size else 0
        )
    }
    LaunchedEffect(active, uris) {
        if (!active || uris.size < 2 || forcedIndex != null) return@LaunchedEffect
        // Standalone: continuous 3 s slideshow.
        while (true) {
            delay(SLIDE_MS)
            index = (index + 1) % uris.size
        }
    }

    // The gallery app's newest notification, if any. Only consulted for standalone
    // tiles (a stack member never flips to a notification).
    val snapshot by NotificationCenter.snapshot.collectAsState()
    val preview = if (forcedIndex == null && packageName.isNotBlank()) {
        snapshot.conversationFor(packageName)
    } else {
        null
    }

    val photoFront: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
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

    // Nothing pending → just the slideshow, exactly as before (no flipping).
    if (preview == null) {
        Box(modifier = modifier.fillMaxSize()) { photoFront() }
        return
    }

    // A notification is pending: self-flip between photos and the notification while
    // active. When the flip pauses (active low) we settle back on the photos.
    var flipped by remember(packageName) { mutableStateOf(false) }
    LaunchedEffect(active, preview.sender, preview.snippet, preview.count) {
        if (!active) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            flipped = false
            delay(PHOTO_HOLD_MS)
            flipped = true
            delay(NOTIF_HOLD_MS)
        }
    }

    val images by NotificationCenter.images.collectAsState()
    val imgs = images[packageName]
    Box(modifier = modifier.fillMaxSize()) {
        FlipTile(
            flipped = flipped,
            modifier = Modifier.fillMaxSize(),
            front = { photoFront() },
            back = {
                NotificationFaceContent(
                    preview = preview,
                    avatar = imgs?.avatar?.asImageBitmap(),
                    picture = imgs?.picture?.asImageBitmap(),
                    size = size,
                )
                // The gallery app's own icon in the top-left corner.
                AppIconCorner(
                    packageName = packageName,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            },
        )
    }
}
