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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
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
import kotlin.math.ceil
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
    var expanded by remember(req) { mutableStateOf(false) }
    var replyOpen by remember(req) { mutableStateOf(false) }
    // The buttons the app put on this very notification: they live in its card, and act on its key.
    fun offers(action: QuickAction) = target?.takeIf { action in it.quickActions }
    val card = target?.let { item ->
        CardModel(
            item = item,
            appLabel = appLabel,
            canReply = offers(QuickAction.REPLY) != null,
            canMarkRead = offers(QuickAction.MARK_READ) != null,
            canArchive = offers(QuickAction.ARCHIVE) != null,
            expanded = expanded,
            replyOpen = replyOpen,
            onToggleExpanded = { expanded = !expanded },
            onToggleReply = { replyOpen = !replyOpen },
            onSend = { text ->
                viewModel.closeTileMenu()
                if (!NotificationCenter.performQuickAction(context, item.notificationKey, QuickAction.REPLY, text)) {
                    Toast.makeText(context, "couldn't send — open the app to reply", Toast.LENGTH_SHORT).show()
                }
            },
            onMarkRead = {
                viewModel.closeTileMenu()
                if (!NotificationCenter.performQuickAction(context, item.notificationKey, QuickAction.MARK_READ)) {
                    Toast.makeText(context, "couldn't mark as read — open the app", Toast.LENGTH_SHORT).show()
                }
            },
            onArchive = {
                viewModel.closeTileMenu()
                if (!NotificationCenter.performQuickAction(context, item.notificationKey, QuickAction.ARCHIVE)) {
                    Toast.makeText(context, "couldn't archive — open the app", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    val actions = buildList {
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
        card = card,
        onDismiss = viewModel::closeTileMenu,
    )
}

/** Everything the notification card shows and does (see [NotificationCard]). */
private class CardModel(
    val item: ConversationItem,
    val appLabel: String,
    val canReply: Boolean,
    val canMarkRead: Boolean,
    val canArchive: Boolean,
    val expanded: Boolean,
    val replyOpen: Boolean,
    val onToggleExpanded: () -> Unit,
    val onToggleReply: () -> Unit,
    val onSend: (String) -> Unit,
    val onMarkRead: () -> Unit,
    val onArchive: () -> Unit,
) {
    val hasButtons get() = canReply || canMarkRead || canArchive
}

private val CARD_TEXT_HEIGHT = 118.dp
private val CARD_BUTTON_ROW = 52.dp

@Composable
private fun TileQuickMenu(
    bounds: Rect,
    actions: List<MiniAction>,
    accent: Color,
    card: CardModel?,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(160), label = "tileMenu")
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    // imePadding: while replying the keyboard takes the bottom of the screen, and the card moves up to stay visible.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding()) {
        val item = with(density) { MINI_TILE.toPx() }
        val screenH = constraints.maxHeight.toFloat()
        // The card sits right next to the tile. Collapsed it shows the first lines of the message and its buttons;
        // expanded (or while replying) it grows to hold the whole message and the mini tiles step aside.
        val collapsedHeight = CARD_TEXT_HEIGHT + 52.dp + if (card?.hasButtons == true) CARD_BUTTON_ROW else 0.dp
        // Expanded: as tall as the message needs (about 30 characters a line), up to most of the screen.
        val messageLines = card?.let { c ->
            c.item.fullText.ifBlank { c.item.snippet }.split('\n').sumOf { ceil(it.length / 34f).toInt().coerceAtLeast(1) }
        } ?: 0
        val expandedHeight = (collapsedHeight + 23.dp * (messageLines - 3).coerceAtLeast(0))
            .coerceAtMost(minOf(with(density) { (screenH * 0.58f).toDp() }, 460.dp))
            .coerceAtLeast(collapsedHeight)
        val cardHeightDp = if (card == null) 0.dp else if (card.expanded) expandedHeight else collapsedHeight
        val tilesVisible = card == null || (!card.expanded && !card.replyOpen)
        val plan = quickMenuPlan(
            tile = bounds,
            count = if (tilesVisible) actions.size else 0,
            cardHeight = with(density) { cardHeightDp.toPx() },
            screenW = constraints.maxWidth.toFloat(),
            screenH = screenH,
            item = item,
            gap = with(density) { MINI_GAP.toPx() },
            margin = with(density) { 10.dp.toPx() },
            topInset = statusTop,
            bottomInset = navBottom,
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
        val cardRect = plan.card
        if (card != null && cardRect != null) {
            NotificationCard(
                card = card,
                accent = accent,
                modifier = Modifier
                    .offset { IntOffset(cardRect.left.roundToInt(), cardRect.top.roundToInt()) }
                    .size(width = with(density) { cardRect.width.toDp() }, height = cardHeightDp)
                    .graphicsLayer { alpha = progress },
            )
        }
        if (tilesVisible) {
            actions.forEachIndexed { i, action ->
                val at = plan.slots.getOrNull(i) ?: return@forEachIndexed
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

/**
 * The notification the cluster acts on, right next to the tile: its app, sender and message (the first lines, with
 * "more" to read all of it), and the buttons the app put on it — reply (opens a text box inside the card), mark
 * read, archive — so each one visibly belongs to this message.
 */
@Composable
private fun NotificationCard(card: CardModel, accent: Color, modifier: Modifier) {
    val message = card.item.fullText.ifBlank { card.item.snippet }
    var overflowing by remember(card.item.notificationKey) { mutableStateOf(false) }
    var reply by remember(card.item.notificationKey) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(card.replyOpen) { if (card.replyOpen) focus.requestFocus() }
    Row(
        modifier = modifier
            .background(Color(0xFF1C1C21))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(Modifier.fillMaxHeight().width(4.dp).background(accent))
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)) {
            Text(
                text = card.appLabel.lowercase(),
                color = Color(0x99FFFFFF),
                fontSize = 11.sp,
                maxLines = 1,
            )
            Text(
                text = card.item.sender.ifBlank { "someone" },
                color = Color(0xCCFFFFFF),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The message: a few lines when collapsed, the whole thing (scrolling if it is long) when expanded.
            Box(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    maxLines = if (card.expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!card.expanded) overflowing = it.hasVisualOverflow },
                    modifier = if (card.expanded) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                )
            }
            if (overflowing || card.expanded) {
                Text(
                    text = if (card.expanded) "less ▴" else "more ▾",
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClick = card.onToggleExpanded)
                        .padding(vertical = 4.dp),
                )
            }
            if (card.replyOpen && card.canReply) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f).background(Color(0xFF2A2A31)).padding(horizontal = 10.dp, vertical = 10.dp)) {
                        if (reply.isEmpty()) {
                            Text("reply to ${card.item.sender.ifBlank { "someone" }}", color = Color(0x80FFFFFF), fontSize = 14.sp, maxLines = 1)
                        }
                        BasicTextField(
                            value = reply,
                            onValueChange = { reply = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    CardButton("send", null, accent, enabled = reply.isNotBlank()) { card.onSend(reply.trim()) }
                }
            } else if (card.hasButtons) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (card.canReply) CardButton("reply", TileIcons["messages"], accent, onClick = card.onToggleReply)
                    if (card.canMarkRead) CardButton("mark read", TileIcons["check"], accent, onClick = card.onMarkRead)
                    if (card.canArchive) CardButton("archive", TileIcons["download"], accent, onClick = card.onArchive)
                }
            }
        }
    }
}

@Composable
private fun CardButton(label: String, icon: ImageVector?, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .background(if (enabled) accent else accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
