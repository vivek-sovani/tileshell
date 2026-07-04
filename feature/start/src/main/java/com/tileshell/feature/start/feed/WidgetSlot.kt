package com.tileshell.feature.start.feed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.ColorTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val WIDGET_HOST_ID = 0x54_53 // "TS"
private const val WIDGET_MIN_H = 72
private const val WIDGET_MAX_H = 720

/**
 * Hosts any number of Android app widgets on the feed's glance tab. Self-contained:
 * owns an [AppWidgetHost] (started while composed), adds widgets through a custom
 * preview picker + the bind/configure flow (via activity-result launchers — the
 * composition is already activity-hosted, so `:app` needs no plumbing), persists the
 * bound ids + heights in [WidgetStore], and renders each live
 * [android.appwidget.AppWidgetHostView] through [AndroidView] at its stored height.
 * Each widget has resize (±) / edit / remove controls. All guarded — a device that
 * blocks third-party hosting just shows the "add a widget" prompt.
 */
@Composable
fun WidgetSection(accent: Color, tokens: ColorTokens) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val host = remember { FeedAppWidgetHost(appContext, WIDGET_HOST_ID) }
    DisposableEffect(host) {
        runCatching { host.startListening() }
        onDispose { runCatching { host.stopListening() } }
    }
    val manager = remember { AppWidgetManager.getInstance(appContext) }
    val store = remember(context) { WidgetStore.create(context) }
    val widgets by store.data.collectAsStateWithLifecycle(initialValue = WidgetData())
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val widthDp = (LocalConfiguration.current.screenWidthDp - 28).coerceAtLeast(120)

    var showPicker by remember { mutableStateOf(false) }
    var pendingBindId by remember { mutableStateOf(-1) }
    var pendingProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var pendingConfigureId by remember { mutableStateOf(-1) }

    fun commit(id: Int, provider: AppWidgetProviderInfo) {
        // Start from the provider's preferred/min height; collection widgets
        // (calendar/agenda lists) report tall sizes — don't clamp them short.
        val preferred = if (android.os.Build.VERSION.SDK_INT >= 31 && provider.targetCellHeight > 0) {
            provider.targetCellHeight * 60 // ~60dp per cell
        } else {
            (provider.minHeight / density).roundToInt()
        }
        val h = preferred.coerceIn(96, 480)
        scope.launch { store.add(HostedWidget(id, h)) }
    }

    val configureLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val id = pendingConfigureId
        pendingConfigureId = -1
        val provider = if (id != -1) manager.getAppWidgetInfo(id) else null
        if (result.resultCode == Activity.RESULT_OK && id != -1 && provider != null) {
            commit(id, provider)
        } else if (id != -1) {
            runCatching { host.deleteAppWidgetId(id) }
        }
    }

    fun afterBind(id: Int, provider: AppWidgetProviderInfo) {
        if (provider.configure != null) {
            pendingConfigureId = id
            val cfg = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(provider.configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            runCatching { configureLauncher.launch(cfg) }.onFailure { commit(id, provider) }
        } else {
            commit(id, provider)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val id = pendingBindId
        val provider = pendingProvider
        pendingBindId = -1
        pendingProvider = null
        if (result.resultCode == Activity.RESULT_OK && id != -1 && provider != null) {
            afterBind(id, provider)
        } else if (id != -1) {
            runCatching { host.deleteAppWidgetId(id) }
        }
    }

    // Re-configure an existing widget (the "edit" action); result is ignored — the
    // widget updates itself, and we keep its stored height.
    val editLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { }

    fun addProvider(provider: AppWidgetProviderInfo) {
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull() ?: return
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, provider.provider) }
            .getOrDefault(false)
        if (bound) {
            afterBind(id, provider)
        } else {
            pendingBindId = id
            pendingProvider = provider
            val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            runCatching { bindLauncher.launch(bind) }
                .onFailure { runCatching { host.deleteAppWidgetId(id) } }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        widgets.widgets.forEachIndexed { index, hw ->
            key(hw.widgetId) {
                WidgetView(
                    host = host,
                    manager = manager,
                    widget = hw,
                    widthDp = widthDp,
                    accent = accent,
                    tokens = tokens,
                    canMoveUp = index > 0,
                    canMoveDown = index < widgets.widgets.lastIndex,
                    onMoveUp = { scope.launch { store.move(hw.widgetId, up = true) } },
                    onMoveDown = { scope.launch { store.move(hw.widgetId, up = false) } },
                    onResize = { newH -> scope.launch { store.setHeight(hw.widgetId, newH) } },
                    onEdit = { info ->
                        val cfg = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                            .setComponent(info.configure)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, hw.widgetId)
                        runCatching { editLauncher.launch(cfg) }
                    },
                    onRemove = {
                        runCatching { host.deleteAppWidgetId(hw.widgetId) }
                        scope.launch { store.remove(hw.widgetId) }
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, tokens.tileLine, RoundedCornerShape(20.dp))
                .clickable { showPicker = true }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ add a widget", color = accent, fontSize = 14.sp)
        }
    }

    if (showPicker) {
        WidgetPicker(
            manager = manager,
            tokens = tokens,
            onPick = { provider -> showPicker = false; addProvider(provider) },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun WidgetView(
    host: AppWidgetHost,
    manager: AppWidgetManager,
    widget: HostedWidget,
    widthDp: Int,
    accent: Color,
    tokens: ColorTokens,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onResize: (Int) -> Unit,
    onEdit: (AppWidgetProviderInfo) -> Unit,
    onRemove: () -> Unit,
) {
    val info = manager.getAppWidgetInfo(widget.widgetId)
    // A widget whose provider was uninstalled returns null info — forget it.
    LaunchedEffect(widget.widgetId, info) { if (info == null) onRemove() }
    if (info == null) return

    val density = LocalDensity.current.density
    var editing by remember(widget.widgetId) { mutableStateOf(false) }
    // Live height while dragging; reset to the persisted value when it changes.
    var liveHeight by remember(widget.widgetId, widget.heightDp) { mutableStateOf(widget.heightDp) }

    Box(modifier = Modifier.fillMaxWidth()) {
        key(widget.widgetId) {
            AndroidView(
                factory = { ctx ->
                    host.createView(ctx.applicationContext, widget.widgetId, info)
                },
                update = { view ->
                    runCatching {
                        view.updateAppWidgetSize(Bundle.EMPTY, widthDp, liveHeight, widthDp, liveHeight)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(liveHeight.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(tokens.sheet),
            )
        }

        // Persistent "edit" pill at top-right — tap to enter edit mode for this widget.
        if (!editing) {
            Text(
                "edit",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.20f))
                    .clickable { editing = true }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }

        if (editing) {
            // Dim scrim (visual only; Popup below owns touch handling).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
            )
            // Window-level Popup anchored to this widget Box. dismissOnClickOutside
            // fires onDismissRequest for any touch outside the widget's bounds, so
            // tapping anywhere else on the feed page also exits edit mode.
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { editing = false },
                properties = PopupProperties(
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .width(widthDp.dp)
                        .height(liveHeight.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { editing = false },
                ) {
                    // Top-left controls: reorder up / down.
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (canMoveUp) EditPill("↑", accent, onClick = onMoveUp)
                        if (canMoveDown) EditPill("↓", accent, onClick = onMoveDown)
                    }
                    // Top-right controls: edit (reconfigure) + remove.
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (info.configure != null) EditPill("edit", accent) { onEdit(info) }
                        EditPill("remove", Color(0xFFD6262B)) { onRemove() }
                    }
                    // Bottom drag handle: drag up/down to resize.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                            .size(width = 44.dp, height = 6.dp)
                            .pointerInput(widget.widgetId) {
                                detectDragGestures(
                                    onDrag = { change, drag ->
                                        change.consume()
                                        liveHeight = (liveHeight + (drag.y / density)).roundToInt()
                                            .coerceIn(WIDGET_MIN_H, WIDGET_MAX_H)
                                    },
                                    onDragEnd = { onResize(liveHeight) },
                                )
                            },
                    )
                }
            }
        }
    }
}

/** One app's widgets in the picker — [appLabel] drives both sort order and the group header. */
private data class WidgetAppGroup(
    val packageName: String,
    val appLabel: String,
    val providers: List<AppWidgetProviderInfo>,
)

/**
 * Full-screen widget picker dialog: installed providers with preview + label,
 * grouped by owning app (a phone can easily have 20+ widgets across a handful of
 * apps — a flat list made it hard to find, say, "the calendar app's" widgets
 * among everything else). Groups and their contents are both sorted by label so
 * the picker reads the same way every time it's opened.
 */
@Composable
private fun WidgetPicker(
    manager: AppWidgetManager,
    tokens: ColorTokens,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val groups = remember {
        runCatching {
            val pm = context.packageManager
            manager.installedProviders
                .groupBy { it.provider.packageName }
                .map { (packageName, providers) ->
                    val appLabel = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                    }.getOrDefault(packageName)
                    WidgetAppGroup(
                        packageName = packageName,
                        appLabel = appLabel,
                        providers = providers.sortedBy { it.loadLabel(pm).lowercase() },
                    )
                }
                .sortedBy { it.appLabel.lowercase() }
        }.getOrDefault(emptyList())
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 540.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(tokens.bg)
                .padding(16.dp),
        ) {
            Text("choose a widget", color = tokens.fg, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
            if (groups.isEmpty()) {
                Text("no widgets available", color = tokens.fgDim, fontSize = 14.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    groups.forEach { group ->
                        item(key = "header/${group.packageName}") {
                            WidgetGroupHeader(group.appLabel, tokens)
                        }
                        items(
                            group.providers,
                            key = { "${group.packageName}/${it.provider.className}" },
                        ) { p ->
                            WidgetPickerRow(p, tokens) { onPick(p) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetGroupHeader(appLabel: String, tokens: ColorTokens) {
    Text(
        text = appLabel,
        color = tokens.fgDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun WidgetPickerRow(
    provider: AppWidgetProviderInfo,
    tokens: ColorTokens,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val label = remember(provider) {
        runCatching { provider.loadLabel(context.packageManager) }.getOrNull().orEmpty()
    }
    val preview = remember(provider) {
        runCatching { provider.loadPreviewImage(context, 0) ?: provider.loadIcon(context, 0) }
            .getOrNull()?.toBitmapOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.sheet)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            label.ifEmpty { "widget" },
            color = tokens.fg,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun EditPill(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Renders a [Drawable] (widget preview/icon) to a bitmap for Compose. */
private fun Drawable.toBitmapOrNull(): Bitmap? = runCatching {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.coerceIn(1, 1024)
    val h = intrinsicHeight.coerceIn(1, 1024)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    bmp
}.getOrNull()
