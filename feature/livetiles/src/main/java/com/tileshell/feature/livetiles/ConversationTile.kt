package com.tileshell.feature.livetiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.settings.HomeStyle
import com.tileshell.core.data.settings.IconShape
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
            front = {
                ConversationCountFace(preview.count, countWord, size)
                AppIconCorner(
                    packageName = packageName,
                    homeStyle = homeStyle,
                    iconShape = iconShape,
                    themedIcons = themedIcons,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            },
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
    if (size.shortLive) {
        // One row tall: the count and its word on one line (stacked, the word was clipped under the number).
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 38.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = count.toString(), color = FaceText, fontSize = 24.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(text = word, color = FaceText.copy(alpha = 0.82f), fontSize = 13.sp, maxLines = 1)
        }
        return
    }
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
 * The back face of a notification live tile, laid out like a Windows Phone flip tile: the notification's title
 * (the sender) then its text at the top, the sender's photo as a faint circle behind them, the app's name at the
 * bottom-left and its icon at the bottom-right. Used by mail / messages / generic-app / photos tiles.
 *
 * One layout for every [TileSize]: type and line counts follow how much room the face really has (the tile's
 * rows divided by Start's live-face enlargement, see [LocalLiveFaceScale]), a one-row tile drops the app name,
 * and a 1-column tile drops the icon. A notification with a picture is the tile (see [NotificationPhotoFace]).
 * [packageName] null (the people hub, which mixes apps) leaves the footer out.
 */
@Composable
internal fun NotificationFaceContent(
    item: ConversationItem,
    avatar: ImageBitmap?,
    picture: ImageBitmap?,
    size: TileSize = TileSize.MEDIUM,
    packageName: String? = null,
    homeStyle: HomeStyle = HomeStyle.TILES,
    iconShape: IconShape = IconShape.ORIGINAL,
    themedIcons: Boolean = false,
) {
    val look = NotificationLook.of(size, LocalLiveFaceScale.current)
    if (picture != null && size != TileSize.SMALL) {
        NotificationPhotoFace(item, picture, look, packageName, homeStyle, iconShape, themedIcons)
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (avatar != null && !look.oneRow) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.38f,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(minOf(maxWidth, maxHeight) * 0.7f)
                    .clip(CircleShape),
            )
        }
        if (look.oneRow) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (packageName != null) {
                    AppIconCorner(packageName, homeStyle, iconShape, themedIcons)
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    NotificationTitle(item, look, FaceText, look.badgeRoom)
                    NotificationBody(item, look, FaceText.copy(alpha = 0.78f))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = look.padding, end = look.padding, top = look.padding + look.topRoom, bottom = look.padding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    NotificationTitle(item, look, FaceText, look.badgeRoom)
                    NotificationBody(item, look, FaceText.copy(alpha = 0.78f))
                }
                NotificationFooter(packageName, look, FaceText, homeStyle, iconShape, themedIcons)
            }
        }
    }
}

/** Everything about how big the notification back face's type is and how many lines it gets. */
internal data class NotificationLook(
    val oneRow: Boolean,
    val narrow: Boolean,
    val padding: Dp,
    val titleSp: Float,
    val bodySp: Float,
    val labelSp: Float,
    val titleLines: Int,
    val bodyLines: Int,
    /** Space kept free at the end of the title for the count badge in the tile's corner. */
    val badgeRoom: Dp,
    /** A 1-column tile has no width to spare beside the badge, so its text starts below it instead. */
    val topRoom: Dp,
) {
    companion object {
        /** [liveScale] is Start's enlargement of this face (1 on tiles up to 2x2): a 4x4 drawn at 1.6 has the room of a ~2.5-row tile. */
        fun of(size: TileSize, liveScale: Float): NotificationLook {
            val oneRow = size.rows <= 1
            val narrow = size.narrowLive
            val rows = size.rows / liveScale.coerceAtLeast(1f)
            val titleSp = if (narrow) 13f else 16f
            val bodySp = if (narrow) 11f else 13f
            return NotificationLook(
                oneRow = oneRow,
                narrow = narrow,
                padding = if (narrow) 8.dp else 12.dp,
                titleSp = if (oneRow) 14f else titleSp,
                bodySp = if (oneRow) 12f else bodySp,
                labelSp = if (narrow) 11f else 13f,
                titleLines = if (oneRow) 1 else if (rows >= 3f) 3 else 2,
                bodyLines = when {
                    oneRow -> 1
                    rows < 2.2f -> 3
                    rows < 2.7f -> 4
                    rows < 3.5f -> 7
                    else -> 12
                },
                badgeRoom = if (narrow) 0.dp else 24.dp,
                topRoom = if (narrow) 16.dp else 0.dp,
            )
        }
    }
}

@Composable
private fun NotificationTitle(item: ConversationItem, look: NotificationLook, color: Color, endPadding: Dp = 0.dp) {
    Text(
        text = item.sender.ifBlank { "someone" },
        color = color,
        fontSize = look.titleSp.sp,
        lineHeight = (look.titleSp + 5).sp,
        maxLines = look.titleLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = endPadding),
    )
}

@Composable
private fun NotificationBody(item: ConversationItem, look: NotificationLook, color: Color) {
    if (item.snippet.isEmpty()) return
    Text(
        text = item.snippet,
        color = color,
        fontSize = look.bodySp.sp,
        lineHeight = (look.bodySp + 4).sp,
        maxLines = look.bodyLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 3.dp),
    )
}

/** The app's name (lowercase, like every tile label) at the bottom-left and its icon at the bottom-right. */
@Composable
private fun NotificationFooter(
    packageName: String?,
    look: NotificationLook,
    color: Color,
    homeStyle: HomeStyle,
    iconShape: IconShape,
    themedIcons: Boolean,
) {
    if (packageName == null) return
    val context = LocalContext.current
    val label = remember(packageName) { appLabelOrNull(context, packageName).orEmpty().lowercase() }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = color,
            fontSize = look.labelSp.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!look.narrow) {
            Spacer(Modifier.width(6.dp))
            AppIconCorner(packageName, homeStyle, iconShape, themedIcons)
        }
    }
}

// ── Photo notifications: the picture is the tile ───────────────────────────────

/**
 * A notification that carries a photo: the photo covers the whole tile, with the title and text at the top and the
 * app's name and icon at the bottom, over a dark gradient at both ends. The text is always white, whatever the
 * theme: it sits on the photo, not on the tile colour.
 */
@Composable
private fun NotificationPhotoFace(
    item: ConversationItem,
    picture: ImageBitmap,
    look: NotificationLook,
    packageName: String?,
    homeStyle: HomeStyle,
    iconShape: IconShape,
    themedIcons: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = picture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0xAA000000), 0.4f to Color(0x11000000), 0.7f to Color(0x22000000), 1f to Color(0xCC000000)),
            ),
        )
        if (look.oneRow) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (packageName != null) {
                    AppIconCorner(packageName, homeStyle, iconShape, themedIcons)
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    NotificationTitle(item, look, Color.White, look.badgeRoom)
                    NotificationBody(item, look, Color.White.copy(alpha = 0.85f))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = look.padding, end = look.padding, top = look.padding + look.topRoom, bottom = look.padding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    NotificationTitle(item, look, Color.White, look.badgeRoom)
                    NotificationBody(item, look, Color.White.copy(alpha = 0.85f))
                }
                NotificationFooter(packageName, look, Color.White, homeStyle, iconShape, themedIcons)
            }
        }
    }
}
