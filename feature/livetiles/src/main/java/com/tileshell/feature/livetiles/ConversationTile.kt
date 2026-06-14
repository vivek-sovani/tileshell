package com.tileshell.feature.livetiles

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
import androidx.compose.foundation.background
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
 * The live mail / messages tile (FR-2). Bound to the tile's own [packageName]:
 * the front shows the newest conversation (sender + snippet), the back the unread
 * count. Reads [NotificationCenter] — when notification access is off, the
 * listener is disconnected, or that app has nothing pending, there is no preview
 * and it renders [fallback] (the static glyph), so the tile degrades gracefully.
 *
 * [kind] selects only the back-face wording ("unread" for mail, "new" for
 * messages), matching the prototype `liveFace('mail'|'messages')`.
 */
@Composable
fun ConversationTileFace(
    kind: LiveFace,
    packageName: String,
    flipped: Boolean,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by NotificationCenter.snapshot.collectAsState()
    val preview = snapshot.conversationFor(packageName) ?: return fallback()

    val countWord = if (kind == LiveFace.MESSAGES) "new" else "unread"
    Box(modifier = modifier.fillMaxSize()) {
        FlipTile(
            flipped = flipped,
            modifier = Modifier.fillMaxSize(),
            front = { ConversationFront(preview) },
            back = { ConversationBack(preview.count, countWord) },
        )
        // The mail/messages app's own icon in the top-left corner.
        AppIconCorner(
            packageName = packageName,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}

@Composable
private fun ConversationFront(preview: ConversationPreview) {
    Column(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(preview.sender)
            Spacer(Modifier.width(8.dp))
            Text(
                text = preview.sender.ifBlank { "someone" },
                color = FaceText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
private fun ConversationBack(count: Int, word: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = count.toString(),
            color = FaceText,
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
        )
        Text(text = word, color = FaceText.copy(alpha = 0.82f), fontSize = 13.sp, maxLines = 1)
    }
}

/** A small initials avatar (prototype `.av`), tinted from the sender's name. */
@Composable
private fun Avatar(name: String) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(Color(0x33FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initials(name), color = FaceText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
