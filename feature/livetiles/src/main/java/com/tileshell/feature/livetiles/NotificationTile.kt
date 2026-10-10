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
    val itemIndex = remember(packageName) { mutableIntStateOf(0) }
    var flipped by remember(packageName) { mutableStateOf(false) }

    // Self-managed flip: hold count face → cycle through each notification → back to count.
    LaunchedEffect(active, itemCount) {
        itemIndex.intValue = 0
        flipped = false
        if (!active) return@LaunchedEffect
        while (true) {
            delay(COUNT_HOLD_MS)
            repeat(itemCount) { i ->
                itemIndex.intValue = i
                flipped = true
                delay(NOTIF_CYCLE_MS)
            }
            flipped = false
        }
    }
    val current = preview.items.getOrElse(itemIndex.intValue) {
        ConversationItem(sender = preview.sender, snippet = preview.snippet)
    }
    // Report which notification is actually on screen so a tap opens *that* one
    // (see NotificationCenter.openAndClear) — only while the back face (this
    // specific notification) is showing; the front/count face reverts it to null
    // so a tap there still opens the newest, as before.
    SideEffect {
        NotificationCenter.reportDisplayedKey(
            packageName,
            if (flipped) current.notificationKey.ifEmpty { null } else null,
        )
    }
    val imgs = itemImages[current.notificationKey] ?: fallbackImages[packageName]

    Box(modifier = modifier.fillMaxSize()) {
        FlipTile(
            flipped = flipped,
            modifier = Modifier.fillMaxSize(),
            // The tile's own face (app glyph + name), as on a Windows Phone flip tile; the
            // pending count is the badge, and the back shows the notification.
            front = { fallback() },
            back = {
                NotificationFaceContent(
                    item = current,
                    avatar = imgs?.avatar?.asImageBitmap(),
                    picture = imgs?.picture?.asImageBitmap(),
                    size = size,
                    packageName = packageName,
                    homeStyle = homeStyle,
                    iconShape = iconShape,
                    themedIcons = themedIcons,
                )
            },
        )
    }
}
