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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay

private val FaceText = Color.White

// How long each notification is shown on the back face before cycling to the next.
private const val NOTIF_CYCLE_MS = 2_600L

/**
 * The live mail / messages tile (FR-2). Front face shows the total notification count
 * prominently (big number + "unread"/"new") so it is immediately visible. The back
 * face cycles through each pending notification in turn (newest first, one every 2.6 s)
 * so no message is missed. With a single notification the back face shows it without
 * cycling. Reads [NotificationCenter] — when nothing is pending it renders [fallback].
 */
@Composable
fun ConversationTileFace(
    kind: LiveFace,
    packageName: String,
    flipped: Boolean,
    active: Boolean,
    fallback: @Composable () -> Unit,
    size: TileSize = TileSize.MEDIUM,
    modifier: Modifier = Modifier,
) {
    val snapshot by NotificationCenter.snapshot.collectAsState()
    val preview = snapshot.conversationFor(packageName) ?: return fallback()
    val images by NotificationCenter.images.collectAsState()
    val imgs = images[packageName]

    // Cycle through notifications on the back face.
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

    val countWord = if (kind == LiveFace.MESSAGES) "new" else "unread"
    Box(modifier = modifier.fillMaxSize()) {
        FlipTile(
            flipped = flipped,
            modifier = Modifier.fillMaxSize(),
            front = { ConversationCountFace(preview.count, countWord) },
            back = {
                NotificationFaceContent(
                    item = current,
                    avatar = imgs?.avatar?.asImageBitmap(),
                    picture = imgs?.picture?.asImageBitmap(),
                    size = size,
                )
            },
        )
        AppIconCorner(
            packageName = packageName,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}

/**
 * Big count + label face — the front face for all notification-style tiles.
 * Reused by [NotificationTileFace] for generic apps ("notifications") and by
 * [ConversationTileFace] for mail ("unread") / messages ("new").
 */
@Composable
internal fun ConversationCountFace(count: Int, word: String) {
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
 * Shared notification-content layout used on the back face of mail/messages/generic
 * tiles. Layout scales with [size]: MEDIUM = compact row, WIDE = two-column with
 * picture hero, LARGE = full-area hero.
 */
@Composable
internal fun NotificationFaceContent(
    item: ConversationItem,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
    size: TileSize = TileSize.MEDIUM,
) {
    when (size) {
        TileSize.LARGE -> NotificationFaceContentLarge(item, avatar, picture)
        TileSize.WIDE  -> NotificationFaceContentWide(item, avatar, picture)
        else           -> NotificationFaceContentMedium(item, avatar, picture)
    }
}

// ── MEDIUM (2×2) ──────────────────────────────────────────────────────────────

@Composable
private fun NotificationFaceContentMedium(
    item: ConversationItem,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderAvatar(name = item.sender, photo = avatar, sizeDp = 28)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.sender.ifBlank { "someone" },
                color = FaceText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.snippet.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.snippet,
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

@Composable
private fun NotificationFaceContentWide(
    item: ConversationItem,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 14.dp, top = 28.dp, end = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(name = item.sender, photo = avatar, sizeDp = 40)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.sender.ifBlank { "someone" },
                    color = FaceText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.snippet.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.snippet,
                    color = FaceText.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    maxLines = if (picture != null) 4 else 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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

@Composable
private fun NotificationFaceContentLarge(
    item: ConversationItem,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
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
                text = item.sender.ifBlank { "someone" },
                color = FaceText.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.snippet.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.snippet,
                    color = FaceText,
                    fontSize = 13.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(name = item.sender, photo = avatar, sizeDp = 36)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.sender.ifBlank { "someone" },
                    color = FaceText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.snippet.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.snippet,
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

/**
 * Sender thumbnail: contact [photo] cropped to a circle when present, else a tinted
 * initials avatar. Used by all notification-style faces.
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
