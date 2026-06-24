package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

/**
 * The generic notification live tile (FR-2.3 — "live tiles for all other apps").
 * Any pinned app tile without a dedicated live face becomes live the moment its
 * package has an active notification: the front shows the newest notification as
 * an Android-notification-style row (sender avatar + title + snippet, with a
 * shared-picture thumbnail when one is attached). When the app has nothing
 * pending — or notification access is off — it renders [fallback] (the static
 * glyph), so an app with no notifications looks exactly as before.
 *
 * Unlike the mail/messages [ConversationTileFace] this face does not flip: the
 * per-app badge already carries the count, and a generic notification tile is not
 * registered with the flip scheduler (its icon key maps to no [LiveFace]). It
 * shows the latest notification only — content, never gated by `liveActive`, so
 * notifications surface even while the flip scheduler is paused.
 */
@Composable
fun NotificationTileFace(
    packageName: String,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by NotificationCenter.snapshot.collectAsState()
    val preview = snapshot.conversationFor(packageName) ?: return fallback()
    // The notification's sender avatar + shared picture, shown alongside the text.
    val images by NotificationCenter.images.collectAsState()
    val imgs = images[packageName]

    Box(modifier = modifier.fillMaxSize()) {
        NotificationFaceContent(
            preview = preview,
            avatar = imgs?.avatar?.asImageBitmap(),
            picture = imgs?.picture?.asImageBitmap(),
        )
        // The app's own icon in the top-left corner (the count badge sits top-right).
        AppIconCorner(
            packageName = packageName,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}
