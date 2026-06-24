package com.tileshell.feature.livetiles

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FaceText = Color.White

/**
 * The live mail / messages tile (FR-2). Bound to the tile's own [packageName]:
 * the front shows the newest conversation as an Android-notification-style row
 * (sender avatar + name + message, with a shared-picture thumbnail at the end),
 * the back the unread count. Reads [NotificationCenter] — when notification
 * access is off, the listener is disconnected, or that app has nothing pending,
 * there is no preview and it renders [fallback] (the static glyph), so the tile
 * degrades gracefully.
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
    // The notification's sender avatar + shared picture, shown alongside the text.
    val images by NotificationCenter.images.collectAsState()
    val imgs = images[packageName]

    val countWord = if (kind == LiveFace.MESSAGES) "new" else "unread"
    Box(modifier = modifier.fillMaxSize()) {
        FlipTile(
            flipped = flipped,
            modifier = Modifier.fillMaxSize(),
            front = {
                NotificationFaceContent(
                    preview = preview,
                    avatar = imgs?.avatar?.asImageBitmap(),
                    picture = imgs?.picture?.asImageBitmap(),
                )
            },
            back = { ConversationBack(preview.count, countWord) },
        )
        // The mail/messages app's own icon in the top-left corner.
        AppIconCorner(
            packageName = packageName,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}

/**
 * Shared front layout for the notification-style live faces (mail/messages and the
 * generic notification tile): the sender [avatar] as a small circular thumbnail
 * (falling back to initials when there is no photo), the sender name + message
 * [ConversationPreview.snippet] beside it, and an optional shared-[picture]
 * thumbnail at the end of the row — mirroring a collapsed Android notification.
 */
@Composable
internal fun NotificationFaceContent(
    preview: ConversationPreview,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderAvatar(name = preview.sender, photo = avatar)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preview.sender.ifBlank { "someone" },
                color = FaceText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview.snippet.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preview.snippet,
                    color = FaceText.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (picture != null) {
            Spacer(Modifier.width(8.dp))
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
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

/**
 * The sender thumbnail: the contact [photo] cropped to a circle when present, else
 * a tinted initials avatar (prototype `.av`). Used by both notification-style faces.
 */
@Composable
internal fun SenderAvatar(name: String, photo: ImageBitmap?) {
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(28.dp).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier.size(28.dp).background(Color(0x33FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials(name),
                color = FaceText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
