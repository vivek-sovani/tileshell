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
 * The count → message → message … → count sequence of a notification tile, as whole-tile flips (a Windows Phone
 * tile turns over for every message, not just to its back face once). A [FlipTile] has two faces, so each step writes
 * what comes next into the face that is turned away and then flips to it: [frontSlot] / [backSlot] hold the content
 * of each face (-1 = the count face, otherwise the index of a message) and [flipped] says which is showing.
 */
internal class NotificationFlipper {
    var frontSlot by mutableIntStateOf(COUNT_SLOT)
    var backSlot by mutableIntStateOf(0)
    var flipped by mutableStateOf(false)

    /** The slot now on screen: -1 for the count face, else the message index. */
    val shown: Int get() = if (flipped) backSlot else frontSlot

    fun reset() {
        frontSlot = COUNT_SLOT
        backSlot = 0
        flipped = false
    }

    /** Turns to [slot]: written into the face that is turned away, then a flip to it. */
    fun turnTo(slot: Int) {
        if (flipped) frontSlot = slot else backSlot = slot
        flipped = !flipped
    }

    companion object {
        const val COUNT_SLOT = -1
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
            repeat(itemCount) { i ->
                flipper.turnTo(i)
                delay(NOTIF_CYCLE_MS)
            }
            flipper.turnTo(NotificationFlipper.COUNT_SLOT)
        }
    }
    return flipper
}

/**
 * The generic notification live tile (FR-2.3 — "live tiles for all other apps").
 * Any pinned app tile without a dedicated live face becomes live the moment its
 * package has an active notification.
 *
 * Front face: the tile's own static face (app glyph and name) — the count is the badge.
 * Back face: cycles through each pending notification in turn (newest first, one
 * every 2.6 s) so every message gets its moment. With a single notification the back
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
    val shown = flipper.shown
    val current = preview.items.getOrElse(shown.coerceAtLeast(0)) {
        ConversationItem(sender = preview.sender, snippet = preview.snippet)
    }
    // Report which notification is actually on screen so a tap opens *that* one
    // (see NotificationCenter.openAndClear) — only while a message is showing; the
    // count face reverts it to null so a tap there still opens the newest, as before.
    SideEffect {
        NotificationCenter.reportDisplayedKey(
            packageName,
            if (shown >= 0) current.notificationKey.ifEmpty { null } else null,
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        // Each face shows its own slot: the count face (the tile's glyph and name, with the pending count as the
        // badge) or one message.
        val face: @Composable (Int) -> Unit = { slot ->
            if (slot < 0) {
                fallback()
            } else {
                val item = preview.items.getOrElse(slot) { current }
                val itemImgs = itemImages[item.notificationKey] ?: fallbackImages[packageName]
                NotificationFaceContent(
                    item = item,
                    avatar = itemImgs?.avatar?.asImageBitmap(),
                    picture = itemImgs?.picture?.asImageBitmap(),
                    size = size,
                    packageName = packageName,
                    homeStyle = homeStyle,
                    iconShape = iconShape,
                    themedIcons = themedIcons,
                )
            }
        }
        FlipTile(
            flipped = flipper.flipped,
            modifier = Modifier.fillMaxSize(),
            front = { face(flipper.frontSlot) },
            back = { face(flipper.backSlot) },
        )
    }
}
