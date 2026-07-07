package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.tileshell.core.data.TileSize
import kotlinx.coroutines.delay

private const val NOTIF_CYCLE_MS = 2_600L

/**
 * The generic notification live tile (FR-2.3 — "live tiles for all other apps").
 * Any pinned app tile without a dedicated live face becomes live the moment its
 * package has an active notification. When there are multiple pending notifications
 * the front cycles through them newest-first, one every 2.6 s, so each message gets
 * its turn instead of only the latest being visible. With a single notification the
 * tile shows it without cycling. When the app has nothing pending — or notification
 * access is off — it renders [fallback] (the static glyph).
 */
@Composable
fun NotificationTileFace(
    packageName: String,
    active: Boolean,
    fallback: @Composable () -> Unit,
    size: TileSize = TileSize.MEDIUM,
    modifier: Modifier = Modifier,
) {
    val snapshot by NotificationCenter.snapshot.collectAsState()
    val preview = snapshot.conversationFor(packageName) ?: return fallback()
    val images by NotificationCenter.images.collectAsState()
    val imgs = images[packageName]

    val itemCount = preview.items.size
    val itemIndex = remember(packageName) { mutableIntStateOf(0) }
    LaunchedEffect(active, itemCount) {
        itemIndex.intValue = 0
        if (!active || itemCount <= 1) return@LaunchedEffect
        while (true) {
            delay(NOTIF_CYCLE_MS)
            itemIndex.intValue = (itemIndex.intValue + 1) % itemCount
        }
    }
    val current = preview.items.getOrElse(itemIndex.intValue) {
        ConversationItem(sender = preview.sender, snippet = preview.snippet)
    }

    Box(modifier = modifier.fillMaxSize()) {
        NotificationFaceContent(
            item = current,
            avatar = imgs?.avatar?.asImageBitmap(),
            picture = imgs?.picture?.asImageBitmap(),
            size = size,
        )
        AppIconCorner(
            packageName = packageName,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}
