package com.tileshell.feature.livetiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FaceText = Color.White

/**
 * The generic notification live tile (FR-2.3 — "live tiles for all other apps").
 * Any pinned app tile without a dedicated live face becomes live the moment its
 * package has an active notification: the front shows the newest title + snippet
 * (the same data the mail/messages faces use, generalised to every app). When the
 * app has nothing pending — or notification access is off — it renders [fallback]
 * (the static glyph), so an app with no notifications looks exactly as before.
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

    Column(
        modifier = modifier.fillMaxSize().padding(11.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NotificationAvatar(preview.sender)
            if (preview.sender.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = preview.sender,
                    color = FaceText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (preview.snippet.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = preview.snippet,
                color = FaceText.copy(alpha = 0.82f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NotificationAvatar(name: String) {
    Box(
        modifier = Modifier.size(24.dp).background(Color(0x33FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials(name), color = FaceText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
