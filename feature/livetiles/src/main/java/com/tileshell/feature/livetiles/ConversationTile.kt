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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import kotlinx.coroutines.delay

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

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
    val itemImages by NotificationCenter.itemImages.collectAsState()
    val fallbackImages by NotificationCenter.images.collectAsState()

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
    // Use the per-notification image (correct group/sender avatar) falling back to
    // the package-level image for notifications whose key predates this change.
    val imgs = itemImages[current.notificationKey] ?: fallbackImages[packageName]

    val countWord = if (kind == LiveFace.MESSAGES) "new" else "unread"
    Box(modifier = modifier.fillMaxSize()) {
        FlipTile(
            flipped = flipped,
            modifier = Modifier.fillMaxSize(),
            front = { ConversationCountFace(preview.count, countWord, size) },
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
internal fun ConversationCountFace(count: Int, word: String, size: TileSize = TileSize.MEDIUM) {
    // TALL/COLUMN are only 1 column wide (same as SMALL) — centre and shrink
    // slightly so the count + word both stay clear of the narrow edges.
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = count.toString(),
            color = FaceText,
            fontSize = if (narrow) 28.sp else 34.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = word,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (narrow) 11.sp else 13.sp,
            maxLines = 1,
            overflow = if (narrow) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

/**
 * Shared notification-content layout used on the back face of mail/messages/generic
 * tiles. Layout scales with [size]: MEDIUM = compact row, WIDE/WIDE_MEDIUM =
 * two-column with picture hero, LARGE/XLARGE = full-area hero (XLARGE scaled up
 * further still), TALL/COLUMN (1 column wide) = stacked and centred instead of the
 * horizontal avatar+text row the others use, which clips at that width, TALL_MEDIUM =
 * a taller compact layout that spends its extra row height on more snippet lines
 * instead of empty padding, BANNER = a short full-width single-line row, WIDE_SMALL =
 * a short two-column sliver with no room for a separate snippet line, so sender and
 * snippet are combined into the one line that fits. Every one of the eleven
 * [TileSize] presets gets a branch tuned to its own shape rather than silently
 * falling back to MEDIUM's fixed compact layout regardless of how much more space a
 * bigger/differently-shaped tile actually has (see docs/DECISIONS.md "Non-standard
 * notification tile sizes now use their full available space").
 */
@Composable
internal fun NotificationFaceContent(
    item: ConversationItem,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
    size: TileSize = TileSize.MEDIUM,
) {
    when {
        size.narrowLive -> NotificationFaceContentNarrow(item, avatar, size)
        size == TileSize.XLARGE -> NotificationFaceContentXLarge(item, avatar, picture)
        size == TileSize.LARGE -> NotificationFaceContentLarge(item, avatar, picture)
        size == TileSize.WIDE || size == TileSize.WIDE_MEDIUM -> NotificationFaceContentWide(item, avatar, picture)
        size == TileSize.TALL_MEDIUM -> NotificationFaceContentTallMedium(item, avatar, picture)
        size == TileSize.BANNER -> NotificationFaceContentBanner(item, avatar, picture)
        size == TileSize.WIDE_SMALL -> NotificationFaceContentWideSmall(item, avatar)
        else -> NotificationFaceContentMedium(item, avatar, picture)
    }
}

// ── TALL / COLUMN (1 column wide) ──────────────────────────────────────────────

@Composable
private fun NotificationFaceContentNarrow(
    item: ConversationItem,
    avatar: ImageBitmap?,
    size: TileSize,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SenderAvatar(name = item.sender, photo = avatar, sizeDp = 28)
        Text(
            text = item.sender.ifBlank { "someone" },
            color = FaceText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (item.snippet.isNotEmpty()) {
            Text(
                text = item.snippet,
                color = FaceText.copy(alpha = 0.82f),
                fontSize = 12.sp,
                // COLUMN's 4 rows have room for more of the snippet than TALL's 2.
                maxLines = if (size.rows >= 4) 5 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
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

// ── WIDE_SMALL (2×1) ──────────────────────────────────────────────────────────

/**
 * Only one row tall — no room for a separate snippet line underneath the sender
 * name, so the two are combined into the single line that fits, maximizing how much
 * of the notification is actually readable rather than dropping the snippet
 * entirely.
 */
@Composable
private fun NotificationFaceContentWideSmall(item: ConversationItem, avatar: ImageBitmap?) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderAvatar(name = item.sender, photo = avatar, sizeDp = 22)
        Spacer(Modifier.width(6.dp))
        val sender = item.sender.ifBlank { "someone" }
        Text(
            text = if (item.snippet.isNotEmpty()) "$sender: ${item.snippet}" else sender,
            color = FaceText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── BANNER (4×1) ──────────────────────────────────────────────────────────────

/** Full grid width but only one row tall — a short, wide single-line row rather
 *  than the taller centred layout [NotificationFaceContentMedium] assumes. */
@Composable
private fun NotificationFaceContentBanner(
    item: ConversationItem,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderAvatar(name = item.sender, photo = avatar, sizeDp = 26)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.sender.ifBlank { "someone" },
                color = FaceText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.snippet.isNotEmpty()) {
                Text(
                    text = item.snippet,
                    color = FaceText.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (picture != null) {
            Spacer(Modifier.width(10.dp))
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxHeight().width(70.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}

// ── TALL_MEDIUM (2×3) ─────────────────────────────────────────────────────────

/** Same width as MEDIUM but one row taller — the extra height goes to more
 *  snippet lines (or a taller picture) instead of sitting unused as padding. */
@Composable
private fun NotificationFaceContentTallMedium(
    item: ConversationItem,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    // See NotificationFaceContentLarge's comment: a picture fills the remaining
    // space via its own weight regardless of arrangement; with no picture the
    // header+snippet block is centred instead of pinned to the top with empty
    // space below it.
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = if (picture != null) Arrangement.Top else Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SenderAvatar(name = item.sender, photo = avatar, sizeDp = 32)
            Spacer(Modifier.width(10.dp))
            Text(
                text = item.sender.ifBlank { "someone" },
                color = FaceText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (picture != null) {
            Spacer(Modifier.height(8.dp))
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)),
            )
            if (item.snippet.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.snippet,
                    color = FaceText.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (item.snippet.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.snippet,
                color = FaceText.copy(alpha = 0.88f),
                fontSize = 13.sp,
                maxLines = 7,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── WIDE (4×2) / WIDE_MEDIUM (3×2) ─────────────────────────────────────────────

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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            // Centred rather than pinned to the top — the header+snippet block
            // doesn't fill a WIDE/WIDE_MEDIUM tile's full height, so anchoring
            // it to the top just leaves empty space sitting below it.
            verticalArrangement = Arrangement.Center,
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
    // A picture hero still anchors to the top (it fills all remaining space via
    // its own weight regardless of arrangement); with no picture there's no
    // weighted child to soak up the leftover height, so the text block is
    // centred instead of sitting pinned to the top with empty space below it.
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = if (picture != null) Arrangement.Top else Arrangement.Center,
    ) {
        if (picture != null) {
            Spacer(Modifier.height(22.dp))
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
        }
    }
}

// ── XLARGE (4×4) ──────────────────────────────────────────────────────────────

/**
 * The single biggest tile — a scaled-up [NotificationFaceContentLarge]: bigger
 * avatar/fonts and a much higher snippet line cap so the extra canvas actually
 * shows more of the notification, rather than the same LARGE-sized text sitting
 * inside a bigger tile with the leftover space spent on empty spacer padding.
 */
@Composable
private fun NotificationFaceContentXLarge(
    item: ConversationItem,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
) {
    // See NotificationFaceContentLarge's comment: a picture hero fills the
    // remaining space via its own weight regardless of arrangement; with no
    // picture the text block is centred instead of pinned to the top with
    // empty space below it.
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = if (picture != null) Arrangement.Top else Arrangement.Center,
    ) {
        if (picture != null) {
            Spacer(Modifier.height(20.dp))
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.sender.ifBlank { "someone" },
                color = FaceText.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.snippet.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.snippet,
                    color = FaceText,
                    fontSize = 15.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(name = item.sender, photo = avatar, sizeDp = 48)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = item.sender.ifBlank { "someone" },
                    color = FaceText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.snippet.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = item.snippet,
                    color = FaceText.copy(alpha = 0.92f),
                    fontSize = 16.sp,
                    maxLines = 18,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
