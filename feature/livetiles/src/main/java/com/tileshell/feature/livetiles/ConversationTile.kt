package com.tileshell.feature.livetiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.tileshell.core.data.TileSize

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
    size: TileSize = TileSize.MEDIUM,
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
                    size = size,
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
 * generic notification tile). Layout scales with [size]:
 *
 * - MEDIUM: compact single row — avatar + name/snippet + optional thumbnail.
 * - WIDE: two-column — text on the left (bigger avatar, more snippet lines),
 *   picture hero on the right half when present.
 * - LARGE: full-area hero — picture fills the tile, name + headline below (or
 *   headline fills the tile when there is no picture).
 */
@Composable
internal fun NotificationFaceContent(
    preview: ConversationPreview,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
    size: TileSize = TileSize.MEDIUM,
) {
    when (size) {
        TileSize.LARGE -> NotificationFaceContentLarge(preview, avatar, picture)
        TileSize.WIDE  -> NotificationFaceContentWide(preview, avatar, picture)
        else           -> NotificationFaceContentMedium(preview, avatar, picture)
    }
}

// ── MEDIUM (2×2) ──────────────────────────────────────────────────────────────

@Composable
private fun NotificationFaceContentMedium(
    preview: ConversationPreview,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderAvatar(name = preview.sender, photo = avatar, sizeDp = 28)
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

// ── WIDE (4×2) ────────────────────────────────────────────────────────────────
// Left side: avatar + sender name + snippet (more lines). When a picture is
// present it fills the right ~40 % of the tile as a portrait-style hero image.

@Composable
private fun NotificationFaceContentWide(
    preview: ConversationPreview,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Text column — takes remaining space after the picture (if any).
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 14.dp, top = 28.dp, end = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(name = preview.sender, photo = avatar, sizeDp = 40)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = preview.sender.ifBlank { "someone" },
                    color = FaceText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview.snippet.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = preview.snippet,
                    color = FaceText.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    // More lines fit because the tile is taller than MEDIUM
                    maxLines = if (picture != null) 4 else 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Picture hero — right ~38 % of the tile, full height, no padding.
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(130.dp)
                    .clip(RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)),
            )
        }
    }
}

// ── LARGE (3×3) ───────────────────────────────────────────────────────────────
// Full-area hero: picture fills the tile, name + headline below it in larger
// type. When there is no picture the headline itself becomes the hero.

@Composable
private fun NotificationFaceContentLarge(
    preview: ConversationPreview,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // Leave room for the app-icon corner badge (top-left) drawn by the caller.
        Spacer(Modifier.height(22.dp))
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = preview.sender.ifBlank { "someone" },
                color = FaceText.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview.snippet.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = preview.snippet,
                    color = FaceText,
                    fontSize = 13.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(name = preview.sender, photo = avatar, sizeDp = 36)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = preview.sender.ifBlank { "someone" },
                    color = FaceText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview.snippet.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = preview.snippet,
                    color = FaceText.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
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
internal fun SenderAvatar(name: String, photo: ImageBitmap?, sizeDp: Int = 28) {
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier.size(sizeDp.dp).background(Color(0x33FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials(name),
                color = FaceText,
                fontSize = (sizeDp * 0.42f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
