package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.settings.HomeStyle
import com.tileshell.core.data.settings.IconShape
import kotlinx.coroutines.delay

// How long the count face stays visible before flipping to show individual notifications.
private const val COUNT_HOLD_MS = 3_000L
// How long each individual notification is shown while cycling on the back face.
private const val NOTIF_CYCLE_MS = 2_600L

/**
 * The sequence of a notification tile, as on Windows Phone: the whole tile flips from its front face (the count) to
 * its back face and shows the first message; further messages then slide up inside the tile, one after another
 * ([MessageSlide], the old one leaving through the top as the next rises from the bottom, the colour plate staying
 * put); after the last the tile flips back to the front. [flipped] says which face is showing, [index] which message.
 */
internal class NotificationFlipper {
    var flipped by mutableStateOf(false)
    var index by mutableIntStateOf(0)

    fun reset() {
        flipped = false
        index = 0
    }
}

/** Runs [NotificationFlipper]'s sequence for [itemCount] messages while [active]; restarts when the count changes. */
@Composable
internal fun rememberNotificationFlipper(packageName: String, active: Boolean, itemCount: Int): NotificationFlipper {
    val flipper = remember(packageName) { NotificationFlipper() }
    LaunchedEffect(active, itemCount) {
        flipper.reset()
        if (!active || itemCount == 0) return@LaunchedEffect
        while (true) {
            delay(COUNT_HOLD_MS)
            flipper.index = 0
            flipper.flipped = true
            delay(NOTIF_CYCLE_MS)
            for (i in 1 until itemCount) {
                flipper.index = i
                delay(NOTIF_CYCLE_MS)
            }
            flipper.flipped = false
        }
    }
    return flipper
}

/**
 * Shows message [index] with [content]; when the index changes the new message rises from the bottom while the old
 * one leaves through the top (about 0.45 s), clipped to the tile — the way a Windows Phone tile steps through its
 * messages without turning over.
 */
@Composable
internal fun MessageSlide(index: Int, content: @Composable (Int) -> Unit) {
    androidx.compose.animation.AnimatedContent(
        targetState = index,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(
                450,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            )
            androidx.compose.animation.ContentTransform(
                targetContentEnter = androidx.compose.animation.slideInVertically(spec) { it },
                initialContentExit = androidx.compose.animation.slideOutVertically(spec) { -it },
                sizeTransform = androidx.compose.animation.SizeTransform(clip = true),
            )
        },
        label = "notificationSlide",
    ) { i -> content(i) }
}

/**
 * The generic notification live tile (FR-2.3 — "live tiles for all other apps").
 * Any pinned app tile without a dedicated live face becomes live the moment its
 * package has an active notification.
 *
 * Front face: the tile's own static face (app glyph and name) — the count is the badge.
 * Back face: each pending notification in turn (newest first, one every 2.6 s, sliding up
 * from one to the next) so every message gets its moment. With a single notification the back
 * face shows it without cycling.
 *
 * The flip is self-managed: count shows for 3 s, then each notification is shown for
 * 2.6 s each, then it returns to the count face. Paused when [active] is false.
 * When nothing is pending it renders [fallback] (the static glyph).
 */
@Composable
fun NotificationTileFace(
    packageName: String,
    active: Boolean,
    fallback: @Composable () -> Unit,
    size: TileSize = TileSize.MEDIUM,
    homeStyle: HomeStyle = HomeStyle.TILES,
    iconShape: IconShape = IconShape.ORIGINAL,
    themedIcons: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val snapshot by NotificationCenter.snapshot.collectAsState()
    val muted = LocalNotificationMuted.current
    val preview = snapshot.conversationFor(packageName).takeUnless { muted } ?: return fallback()
    val itemImages by NotificationCenter.itemImages.collectAsState()
    val fallbackImages by NotificationCenter.images.collectAsState()

    val itemCount = preview.items.size
    val flipper = rememberNotificationFlipper(packageName, active, itemCount)
    val current = preview.items.getOrElse(flipper.index) {
        ConversationItem(sender = preview.sender, snippet = preview.snippet)
    }
    // Report which notification is actually on screen so a tap opens *that* one
    // (see NotificationCenter.openAndClear) — only while the back face (a message) is showing; the
    // front face reverts it to null so a tap there still opens the newest, as before.
    SideEffect {
        NotificationCenter.reportDisplayedKey(
            packageName,
            if (flipper.flipped) current.notificationKey.ifEmpty { null } else null,
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        FlipTile(
            flipped = flipper.flipped,
            modifier = Modifier.fillMaxSize(),
            // The tile's own face (app glyph + name), as on a Windows Phone flip tile; the
            // pending count is the badge, and the back shows the notifications.
            front = { fallback() },
            back = {
                MessageSlide(flipper.index) { slot ->
                    val item = preview.items.getOrElse(slot) { current }
                    val imgs = itemImages[item.notificationKey] ?: fallbackImages[packageName]
                    NotificationFaceContent(
                        item = item,
                        avatar = imgs?.avatar?.asImageBitmap(),
                        picture = imgs?.picture?.asImageBitmap(),
                        size = size,
                        packageName = packageName,
                        homeStyle = homeStyle,
                        iconShape = iconShape,
                        themedIcons = themedIcons,
                    )
                }
            },
        )
    }
}
