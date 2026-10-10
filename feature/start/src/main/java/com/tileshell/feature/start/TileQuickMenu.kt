package com.tileshell.feature.start

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.shortcutIconDrawable
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.feature.livetiles.ConversationItem
import com.tileshell.feature.livetiles.NotificationCenter
import com.tileshell.feature.livetiles.QuickAction
import kotlin.math.roundToInt

private val MINI_TILE = 54.dp
private val MINI_GAP = 3.dp
private val NOTIFICATION_CARD_HEIGHT = 170.dp

/**
 * One mini-tile of the cluster: a Windows Phone-style action square next to the pressed tile. [shortcut] (an app's
 * own launcher shortcut, drawn with its own icon) or [icon] (a monoline glyph) is shown over [label].
 */
private class MiniAction(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val shortcut: AppEntry? = null,
    val onClick: () -> Unit,
)

/**
 * The quick-action cluster that opens on a long press of a Start tile: the app's own shortcuts (compose, search, …,
 * up to two), then size, tile settings (colour and the rest, opened directly) and move (edit mode for this tile only), app info and unpin, as small accent tiles
 * beside the tile while the rest of Start dims. A folder or widget stack gets "open folder" / "open stack" first and "ungroup" instead of unpin. Its own composable (not inline in `StartScreen`) because that layout
 * lambda is near the register limit.
 */
@Composable
internal fun TileQuickMenuLayer(
    viewModel: StartViewModel,
    tiles: List<TileModel>,
    accentId: String,
    dark: Boolean,
    lockLayout: Boolean,
) {
    val request by viewModel.tileMenu.collectAsState()
    val req = request ?: return
    val childRef = parseFolderChildId(req.tileId)?.let { (folderId, rowId) ->
        (tiles.firstOrNull { it.id == folderId } as? TileModel.Folder)
            ?.children?.firstOrNull { it.rowId == rowId }?.let { folderId to it }
    }
    val tile = tiles.firstOrNull { it.id == req.tileId }
    if (tile == null && childRef == null) {
        LaunchedEffect(req) { viewModel.closeTileMenu() }
        return
    }
    BackHandler { viewModel.closeTileMenu() }
    val context = LocalContext.current
    val app = tile as? TileModel.App
    val packageName = app?.packageName ?: childRef?.second?.packageName.orEmpty()
    val shortcuts by produceState(emptyList<AppEntry>(), packageName) {
        value = if (packageName.isBlank()) emptyList() else viewModel.appShortcuts(packageName).take(2)
    }
    val expandedFolderId by viewModel.expandedFolderId.collectAsState()
    // Buttons the app itself put on its pending notifications (reply, mark read, archive), pressed on its behalf.
    // The notification the tile was showing when it was pressed (else its newest): shown large below / above the
    // cluster, and the one reply / mark read / archive act on, so none of them can hit a different notification.
    val target = remember(req) {
        if (packageName.isBlank()) {
            null
        } else {
            val list = NotificationCenter.snapshot.value.conversationFor(packageName)?.items.orEmpty()
            val shown = NotificationCenter.displayedKeyFor(packageName)
            list.firstOrNull { shown != null && it.notificationKey == shown } ?: list.firstOrNull()
        }
    }
    val appLabel = remember(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault("")
    }
    var replying by remember(req.tileId) { mutableStateOf<ConversationItem?>(null) }
    val accentForReply = TileAccents.forId(accentId)
    replying?.let { item ->
        ReplyBar(
            sender = item.sender.ifBlank { "someone" },
            quote = item.fullText.ifBlank { item.snippet },
            accent = accentForReply,
            onSend = { text ->
                viewModel.closeTileMenu()
                if (!NotificationCenter.performQuickAction(context, item.notificationKey, QuickAction.REPLY, text)) {
                    Toast.makeText(context, "couldn't send — open the app to reply", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { replying = null },
        )
        return
    }
    val actions = buildList {
        fun itemFor(action: QuickAction) = target?.takeIf { action in it.quickActions }
        itemFor(QuickAction.REPLY)?.let { item ->
            add(MiniAction("reply", "reply", TileIcons["messages"]) { replying = item })
        }
        itemFor(QuickAction.MARK_READ)?.let { item ->
            add(MiniAction("read", "mark read", TileIcons["check"]) {
                viewModel.closeTileMenu()
                if (!NotificationCenter.performQuickAction(context, item.notificationKey, QuickAction.MARK_READ)) {
                    Toast.makeText(context, "couldn't mark as read — open the app", Toast.LENGTH_SHORT).show()
                }
            })
        }
        itemFor(QuickAction.ARCHIVE)?.let { item ->
            add(MiniAction("archive", "archive", TileIcons["download"]) {
                viewModel.closeTileMenu()
                if (!NotificationCenter.performQuickAction(context, item.notificationKey, QuickAction.ARCHIVE)) {
                    Toast.makeText(context, "couldn't archive — open the app", Toast.LENGTH_SHORT).show()
                }
            })
        }
        if (tile is TileModel.Folder) {
            val open = expandedFolderId == tile.id
            val noun = if (tile.isStack) "stack" else "folder"
            add(MiniAction("folder", if (open) "close $noun" else "open $noun", TileIcons[if (tile.isStack) "stack" else "folder"]) {
                viewModel.closeTileMenu()
                viewModel.toggleFolder(tile.id)
            })
        }
        shortcuts.forEach { entry ->
            add(
                MiniAction("shortcut:${entry.activityName}", entry.label, shortcut = entry) {
                    viewModel.closeTileMenu()
                    AppLauncher.launch(context, entry.packageName, entry.activityName)
                },
            )
        }
        if (!lockLayout) {
            add(MiniAction("size", "size", TileIcons["widgets"]) {
                viewModel.closeTileMenu()
                viewModel.requestTilePicker(TilePickerRequest(req.tileId, TilePickerKind.SIZE))
            })
            add(MiniAction("settings", "tile settings", TileIcons["settings"]) {
                viewModel.closeTileMenu()
                viewModel.requestTilePicker(TilePickerRequest(req.tileId, TilePickerKind.COLOR))
            })
            add(MiniAction("move", "move", TileIcons["grip"]) {
                viewModel.closeTileMenu()
                viewModel.enterTileEdit(req.tileId)
            })
        }
        if (packageName.isNotBlank()) {
            add(MiniAction("info", "app info", TileIcons["help"]) {
                viewModel.closeTileMenu()
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            })
        }
        if (!lockLayout) {
            if (tile is TileModel.Folder) {
                // Every app in the folder goes back to Start as its own tile; nothing is lost.
                add(MiniAction("ungroup", "ungroup", TileIcons["unpin"]) {
                    viewModel.closeTileMenu()
                    viewModel.unfoldFolder(tile.id)
                })
            } else if (childRef != null) {
                // The app leaves the folder and goes back to Start as its own tile.
                add(MiniAction("take out", "take out", TileIcons["unpin"]) {
                    viewModel.closeTileMenu()
                    viewModel.removeFolderChild(childRef.first, childRef.second)
                })
            } else {
                add(MiniAction("unpin", "unpin", TileIcons["unpin"]) {
                    viewModel.closeTileMenu()
                    viewModel.unpin(req.tileId)
                })
            }
        }
    }
    val override = when (tile) {
        is TileModel.App -> tile.accentOverride
        is TileModel.Folder -> tile.accentOverride
        null -> childRef?.second?.accentOverride
    }
    val accent = if (override != null) TileAccents.colorForOverride(override, accentId) else TileAccents.forId(accentId)
    TileQuickMenu(
        bounds = Rect(req.left, req.top, req.right, req.bottom),
        actions = actions,
        accent = accent,
        notification = target,
        appLabel = appLabel,
        onDismiss = viewModel::closeTileMenu,
    )
}

@Composable
private fun TileQuickMenu(
    bounds: Rect,
    actions: List<MiniAction>,
    accent: Color,
    notification: ConversationItem?,
    appLabel: String,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(160), label = "tileMenu")
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val item = with(density) { MINI_TILE.toPx() }
        // The notification card sits on whichever half of the screen the tile is not in; the cluster keeps clear of it.
        val cardHeight = if (notification != null) with(density) { NOTIFICATION_CARD_HEIGHT.toPx() } else 0f
        val cardAtTop = bounds.center.y > constraints.maxHeight / 2f
        val slots = quickMenuSlots(
            tile = bounds,
            count = actions.size,
            screenW = constraints.maxWidth.toFloat(),
            screenH = constraints.maxHeight.toFloat(),
            item = item,
            gap = with(density) { MINI_GAP.toPx() },
            margin = with(density) { 10.dp.toPx() },
            topInset = statusTop + if (cardAtTop) cardHeight else 0f,
            bottomInset = navBottom + if (cardAtTop) 0f else cardHeight,
        )
        // Everything but the pressed tile dims; a tap on the dimmed part closes the cluster.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = progress }
                .drawBehind {
                    drawRect(Color(0x99000000))
                    drawRect(Color.Transparent, bounds.topLeft, Size(bounds.width, bounds.height), blendMode = BlendMode.Clear)
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        if (notification != null) {
            NotificationCard(
                item = notification,
                appLabel = appLabel,
                accent = accent,
                modifier = Modifier
                    .align(if (cardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .then(
                        if (cardAtTop) Modifier.padding(top = with(density) { statusTop.toDp() } + 10.dp)
                        else Modifier.padding(bottom = with(density) { navBottom.toDp() } + 10.dp),
                    )
                    .graphicsLayer { alpha = progress },
            )
        }
        actions.forEachIndexed { i, action ->
            val at = slots.getOrNull(i) ?: return@forEachIndexed
            MiniTile(
                action = action,
                accent = accent,
                modifier = Modifier
                    .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.85f + 0.15f * progress
                        scaleY = 0.85f + 0.15f * progress
                    },
            )
        }
    }
}

@Composable
private fun MiniTile(action: MiniAction, accent: Color, modifier: Modifier) {
    val context = LocalContext.current
    val shortcutIcon by produceState<Drawable?>(null, action.key) {
        value = action.shortcut?.let { shortcutIconDrawable(context, it.packageName, it.activityName) }
    }
    Box(
        modifier = modifier
            .size(MINI_TILE)
            .background(accent)
            .clickable(onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val drawable = shortcutIcon
            when {
                action.icon != null -> Icon(action.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                drawable != null -> Image(
                    bitmap = drawable.toBitmap(96, 96).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
                else -> Box(Modifier.size(24.dp))
            }
            Text(
                text = action.label.lowercase(),
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 3.dp),
            )
        }
    }
}

/** A one-line reply box over the dimmed Start: the sender, a text field and a send button. */
@Composable
private fun ReplyBar(sender: String, quote: String, accent: Color, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF1C1C21))
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("replying to $sender", color = Color(0xB3FFFFFF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // The message being answered, so it is clear which notification the reply goes to.
                if (quote.isNotBlank()) {
                    Text(
                        quote,
                        color = Color(0x99FFFFFF),
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(vertical = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (text.isBlank()) accent.copy(alpha = 0.4f) else accent)
                    .clickable(enabled = text.isNotBlank()) { onSend(text.trim()) },
                contentAlignment = Alignment.Center,
            ) {
                Text("send", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/** The notification the cluster acts on, shown large: its app, sender and the whole message. */
@Composable
private fun NotificationCard(item: ConversationItem, appLabel: String, accent: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .size(width = Dp.Unspecified, height = NOTIFICATION_CARD_HEIGHT)
            .background(Color(0xFF1C1C21))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(Modifier.size(width = 4.dp, height = NOTIFICATION_CARD_HEIGHT).background(accent))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = appLabel.lowercase(),
                color = Color(0x99FFFFFF),
                fontSize = 11.sp,
                maxLines = 1,
            )
            Text(
                text = item.sender.ifBlank { "someone" },
                color = Color(0xCCFFFFFF),
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = item.fullText.ifBlank { item.snippet },
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
