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
import kotlin.math.roundToInt

private val MINI_TILE = 54.dp
private val MINI_GAP = 3.dp

/**
 * One mini-tile of the cluster: a Windows Phone-style action square next to the pressed tile. [shortcut] (an app's
 * own launcher shortcut, drawn with its own icon) or [icon] (a monoline glyph) is shown over [label].
 */
private class QuickAction(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val shortcut: AppEntry? = null,
    val onClick: () -> Unit,
)

/**
 * The quick-action cluster that opens on a long press of a Start tile: the app's own shortcuts (compose, search, …,
 * up to two), then size and colour (their pickers open directly), edit (edit mode with this tile selected), app info and unpin, as small accent tiles
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
    val tile = tiles.firstOrNull { it.id == req.tileId }
    if (tile == null) {
        LaunchedEffect(req) { viewModel.closeTileMenu() }
        return
    }
    BackHandler { viewModel.closeTileMenu() }
    val context = LocalContext.current
    val app = tile as? TileModel.App
    val packageName = app?.packageName.orEmpty()
    val shortcuts by produceState(emptyList<AppEntry>(), packageName) {
        value = if (packageName.isBlank()) emptyList() else viewModel.appShortcuts(packageName).take(2)
    }
    val expandedFolderId by viewModel.expandedFolderId.collectAsState()
    val actions = buildList {
        if (tile is TileModel.Folder) {
            val open = expandedFolderId == tile.id
            val noun = if (tile.isStack) "stack" else "folder"
            add(QuickAction("folder", if (open) "close $noun" else "open $noun", TileIcons[if (tile.isStack) "stack" else "folder"]) {
                viewModel.closeTileMenu()
                viewModel.toggleFolder(tile.id)
            })
        }
        shortcuts.forEach { entry ->
            add(
                QuickAction("shortcut:${entry.activityName}", entry.label, shortcut = entry) {
                    viewModel.closeTileMenu()
                    AppLauncher.launch(context, entry.packageName, entry.activityName)
                },
            )
        }
        if (!lockLayout) {
            add(QuickAction("size", "size", TileIcons["widgets"]) {
                viewModel.closeTileMenu()
                viewModel.requestTilePicker(TilePickerRequest(tile.id, TilePickerKind.SIZE))
            })
            add(QuickAction("colour", "colour", TileIcons["palette"]) {
                viewModel.closeTileMenu()
                viewModel.requestTilePicker(TilePickerRequest(tile.id, TilePickerKind.COLOR))
            })
            add(QuickAction("edit", "edit", TileIcons["edit"]) {
                viewModel.closeTileMenu()
                viewModel.enterTileEdit(tile.id)
            })
        }
        if (packageName.isNotBlank()) {
            add(QuickAction("info", "app info", TileIcons["settings"]) {
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
                add(QuickAction("ungroup", "ungroup", TileIcons["unpin"]) {
                    viewModel.closeTileMenu()
                    viewModel.unfoldFolder(tile.id)
                })
            } else {
                add(QuickAction("unpin", "unpin", TileIcons["unpin"]) {
                    viewModel.closeTileMenu()
                    viewModel.unpin(tile.id)
                })
            }
        }
    }
    val override = when (tile) {
        is TileModel.App -> tile.accentOverride
        is TileModel.Folder -> tile.accentOverride
    }
    val accent = if (override != null) TileAccents.colorForOverride(override, accentId) else TileAccents.forId(accentId)
    TileQuickMenu(
        bounds = Rect(req.left, req.top, req.right, req.bottom),
        actions = actions,
        accent = accent,
        onDismiss = viewModel::closeTileMenu,
    )
}

@Composable
private fun TileQuickMenu(bounds: Rect, actions: List<QuickAction>, accent: Color, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(160), label = "tileMenu")
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val item = with(density) { MINI_TILE.toPx() }
        val slots = quickMenuSlots(
            tile = bounds,
            count = actions.size,
            screenW = constraints.maxWidth.toFloat(),
            screenH = constraints.maxHeight.toFloat(),
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
private fun MiniTile(action: QuickAction, accent: Color, modifier: Modifier) {
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
