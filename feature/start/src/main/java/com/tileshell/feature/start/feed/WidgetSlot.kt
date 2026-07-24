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
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.ColorTokens
import com.tileshell.feature.livetiles.rememberAppIconBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val WIDGET_HOST_ID = 0x54_53 // "TS"
private const val WIDGET_MIN_H = 72
private const val WIDGET_MAX_H = 720

/**
 * Whether a widget should default to half the feed's row width (so it can pair
 * up alongside another half-width widget, mirroring the built-in weather+today
 * cards) rather than the full row — decided purely from its own declared
 * natural width in dp against the feed's full content width, so this is
 * unit-testable without an Android runtime. A widget only "fits" at half width
 * if it's comfortably narrower than half the row, not merely narrower than all
 * of it.
 */
internal fun isHalfWidthWidget(naturalWidthDp: Float, feedContentWidthDp: Int): Boolean =
    naturalWidthDp > 0f && naturalWidthDp <= feedContentWidthDp * 0.55f

/**
 * Packs the ordered widget list into rows for rendering: a full-width widget
 * always gets its own row; two consecutive half-width widgets pair into one
 * row (mirroring the built-in weather+today row); a half-width widget left
 * without a partner — an odd count, or its former partner was just removed —
 * still gets its own row at half width, never stretched to fill it just
 * because it's currently alone. Pure list logic, no Compose/Android
 * dependency.
 */
internal fun packWidgetRows(widgets: List<HostedWidget>): List<List<HostedWidget>> {
    val rows = mutableListOf<List<HostedWidget>>()
    var pendingHalf: HostedWidget? = null
    for (w in widgets) {
        if (w.halfWidth) {
            val prev = pendingHalf
            if (prev != null) {
                rows.add(listOf(prev, w))
                pendingHalf = null
            } else {
                pendingHalf = w
            }
        } else {
            pendingHalf?.let { rows.add(listOf(it)) }
            pendingHalf = null
            rows.add(listOf(w))
        }
    }
    pendingHalf?.let { rows.add(listOf(it)) }
    return rows
}

/**
 * Move [dragId] to sit where [targetId] currently is — same splice-and-reinsert
 * algorithm as [com.tileshell.feature.start.reorderTiles] (a forward drag lands
 * after the target, a backward drag lands before it), just keyed by widget id
 * instead of tile id since widgets aren't part of the tile grid. Returns a new
 * list; the input is untouched. No-op when either id is absent or the two are
 * equal.
 */
internal fun reorderWidgets(widgets: List<HostedWidget>, dragId: Int, targetId: Int): List<HostedWidget> {
    if (dragId == targetId) return widgets
    val di = widgets.indexOfFirst { it.widgetId == dragId }
    val ti = widgets.indexOfFirst { it.widgetId == targetId }
    if (di < 0 || ti < 0) return widgets
    val out = widgets.toMutableList()
    val dragged = out.removeAt(di)
    out.add(ti.coerceAtMost(out.size), dragged)
    return out
}

private fun providerMinWidthDp(info: AppWidgetProviderInfo, density: Float): Int =
    (info.minWidth / density).roundToInt()

/**
 * Half the row width for a half-width widget, but never less than the provider's
 * own declared minimum width — some providers (confirmed on-device: Samsung Device
 * Care's SMWidgetOneButton) show their own "Can't show content" fallback rather
 * than clip when given less room than they declare needing.
 */
private fun halfContentWidthDp(info: AppWidgetProviderInfo, widthDp: Int, density: Float): Int =
    maxOf(widthDp / 2, providerMinWidthDp(info, density)).coerceAtMost(widthDp)

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
fun WidgetSection(accent: Color, tokens: ColorTokens, labelColor: Color = tokens.fgDim) {
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
        // Scale to the provider's own recommended aspect ratio rather than just
        // its raw minHeight — a widget designed for a narrow cell (e.g. 2 columns,
        // ~110dp) looks squat and undersized once stretched across our fixed
        // full-device-width slot unless the height scales up to match. API 31+
        // providers publish an explicit recommended cell footprint
        // (targetCellWidth/Height); older ones only report min width/height,
        // used as the next-best proxy for "recommended."
        val minWidthDp = (provider.minWidth / density).takeIf { it > 0 }
        val minHeightDp = (provider.minHeight / density).takeIf { it > 0 }
        val aspect = if (android.os.Build.VERSION.SDK_INT >= 31 && provider.targetCellWidth > 0 && provider.targetCellHeight > 0) {
            provider.targetCellHeight.toFloat() / provider.targetCellWidth.toFloat()
        } else if (minWidthDp != null && minHeightDp != null) {
            minHeightDp / minWidthDp
        } else {
            null
        }
        // A widget declared narrow enough to comfortably fit in half the row
        // (e.g. a 2-column icon/toggle widget) defaults to half-row width, so it
        // can pair up alongside another half-width widget — mirrors the built-in
        // weather+today row. Anything wider defaults to the full row.
        val halfWidth = minWidthDp != null && isHalfWidthWidget(minWidthDp, widthDp)
        // Scale against the width it'll actually render at (half, when paired) —
        // scaling a half-width widget's aspect against the full row width would
        // store a height meant for twice the display width.
        val contentWidthDp = if (halfWidth) halfContentWidthDp(provider, widthDp, density) else widthDp
        val preferred = aspect?.let { (contentWidthDp * it).roundToInt() } ?: minHeightDp?.roundToInt() ?: 180
        val h = preferred.coerceIn(96, 480)
        scope.launch { store.add(HostedWidget(id, h, widthDp = 0, halfWidth = halfWidth)) }
    }

    // Some OEM configure activities (confirmed on-device: Samsung Health's
    // "Daily activity" widget settings screen, DaHomeWidgetSettingActivityOneUI7)
    // finish() without ever calling setResult(RESULT_OK), even when the user
    // genuinely saved — trusting resultCode alone deleted a widget the user had
    // just successfully configured. The bind itself already happened before
    // configure ever launched, so a still-valid provider lookup is a more
    // reliable "did this actually work" signal than the OEM's result code; only
    // delete when the widget id itself is no longer bound to anything.
    val configureLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { _ ->
        val id = pendingConfigureId
        pendingConfigureId = -1
        val provider = if (id != -1) manager.getAppWidgetInfo(id) else null
        if (id != -1 && provider != null) {
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

    // Drag-to-reorder state: each widget reports its own on-screen bounds (root
    // coordinates) as it's laid out; dragging accumulates a delta from the
    // dragged widget's own bounds and hit-tests that live point against every
    // OTHER widget's bounds — mirroring the Start grid's own tile-drag pattern
    // (`reorderTiles`/`onReorderTo` in StartScreen.kt). The actual reorder is
    // only committed once, on release (not continuously while the hit target
    // changes): committing mid-drag would reshuffle `packWidgetRows`'s output
    // and could reparent the dragged widget's own composable — including the
    // very drag-handle gesture detector currently tracking the finger — right
    // out from under the in-progress gesture. `dragTargetId` just tracks the
    // live candidate for that one final commit.
    //
    // `editing` is hoisted here (per widget id) rather than left as `WidgetView`
    // local `remember` state for the same structural reason: reordering can
    // move a widget from its own row into a paired one (or back), which is a
    // genuine reparent in the composition tree — local `remember` doesn't
    // survive that, so a reorder used to silently drop back out of edit mode.
    // State keyed by id in this stable parent survives regardless of which row
    // the widget ends up packed into.
    val widgetBounds = remember { mutableStateMapOf<Int, Rect>() }
    val editingIds = remember { mutableStateMapOf<Int, Boolean>() }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragTargetId by remember { mutableStateOf<Int?>(null) }

    fun onWidgetDragBy(id: Int, delta: Offset) {
        dragDelta += delta
        val origin = widgetBounds[id]?.center ?: return
        val point = origin + dragDelta
        dragTargetId = widgetBounds.entries
            .firstOrNull { (otherId, rect) -> otherId != id && rect.contains(point) }
            ?.key
    }

    fun onWidgetDragEnd(id: Int) {
        val target = dragTargetId
        if (target != null) {
            val current = widgets.widgets
            val reordered = reorderWidgets(current, id, target)
            if (reordered != current) {
                scope.launch { store.reorder(reordered) }
            }
        }
        draggingId = null
        dragDelta = Offset.Zero
        dragTargetId = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("widgets", actionText = "add", accent = accent, labelColor = labelColor, showPlus = true, onAction = { showPicker = true })

        val rows = remember(widgets.widgets) { packWidgetRows(widgets.widgets) }

        @Composable
        fun widgetView(hw: HostedWidget, modifier: Modifier) {
            key(hw.widgetId) {
                WidgetView(
                    host = host,
                    manager = manager,
                    widget = hw,
                    widthDp = widthDp,
                    accent = accent,
                    editing = editingIds[hw.widgetId] == true,
                    onEditingChange = { open -> editingIds[hw.widgetId] = open },
                    isDragging = draggingId == hw.widgetId,
                    dragOffset = if (draggingId == hw.widgetId) dragDelta else Offset.Zero,
                    onDragStart = { draggingId = hw.widgetId; dragDelta = Offset.Zero; dragTargetId = null },
                    onDragBy = { delta -> onWidgetDragBy(hw.widgetId, delta) },
                    onDragEnd = { onWidgetDragEnd(hw.widgetId) },
                    onBoundsChanged = { rect -> widgetBounds[hw.widgetId] = rect },
                    onResize = { newH, newHalf -> scope.launch { store.setSize(hw.widgetId, newH, newHalf) } },
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
                    modifier = modifier,
                )
            }
        }

        rows.forEach { row ->
            if (row.size == 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { hw -> widgetView(hw, Modifier.weight(1f)) }
                }
            } else {
                widgetView(row.single(), Modifier.fillMaxWidth())
            }
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
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    isDragging: Boolean,
    dragOffset: Offset,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onResize: (heightDp: Int, halfWidth: Boolean) -> Unit,
    onEdit: (AppWidgetProviderInfo) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Some OEMs (Samsung's Glance-based widgets — spage news, notes, reminder,
    // Device Care, Digital Wellbeing — confirmed via their async GWT/"Kumiho"/
    // androidx.glance.session.SessionWorker provider-registration path in logcat)
    // don't have the provider info ready the instant a widget is bound, so a null
    // read right after add doesn't necessarily mean "uninstalled." A 2s grace
    // period (4×500ms) was enough for spage but not for Device Care/Digital
    // Wellbeing — confirmed on-device they were being deleted by this exact
    // logic on every cold app start (many widgets registering at once slows
    // each one down further) even though they're pre-installed system apps
    // that can never actually be "uninstalled." Widened to ~15s before
    // concluding it's actually gone, instead of deleting on the spot.
    var infoState by remember(widget.widgetId) { mutableStateOf(manager.getAppWidgetInfo(widget.widgetId)) }
    LaunchedEffect(widget.widgetId) {
        if (infoState == null) {
            repeat(15) {
                delay(1000)
                infoState = manager.getAppWidgetInfo(widget.widgetId)
                if (infoState != null) return@LaunchedEffect
            }
            onRemove()
        }
    }
    val info = infoState ?: return

    val density = LocalDensity.current.density
    // Live height while dragging; reset to the persisted value when it changes.
    var liveHeight by remember(widget.widgetId, widget.heightDp) { mutableStateOf(widget.heightDp) }
    // The two widths this widget can ever render at: half the row (paired, or
    // alone at half width) or the full row — [widthDp] here is always the full
    // row width, regardless of which one this instance is currently rendering
    // at (`WidgetSection` decides that via row packing). Every widget can be
    // dragged between the two; which one it's currently classified as drives
    // both the default live width and the drag's flip-over point.
    val halfWidthDp = remember(widget.widgetId, info, widthDp) { halfContentWidthDp(info, widthDp, density) }
    val fullWidthDp = widthDp
    val defaultContentWidthDp = if (widget.halfWidth) halfWidthDp else fullWidthDp
    var liveWidth by remember(widget.widgetId, widget.halfWidth, widthDp) {
        mutableStateOf(defaultContentWidthDp)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Box(
        modifier = Modifier
            .width(liveWidth.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .graphicsLayer {
                translationX = if (isDragging) dragOffset.x else 0f
                translationY = if (isDragging) dragOffset.y else 0f
            },
    ) {
        key(widget.widgetId) {
            AndroidView(
                factory = { ctx ->
                    host.createView(ctx.applicationContext, widget.widgetId, info)
                },
                update = { view ->
                    runCatching {
                        // Bundle.EMPTY is Android's immutable singleton — updateAppWidgetSize
                        // calls putInt() on the options bundle internally, which threw
                        // UnsupportedOperationException here (silently, into this runCatching)
                        // on every call, so the provider never actually learned its real size
                        // and kept rendering its smallest/narrowest layout regardless of how
                        // big our container was. A fresh mutable Bundle fixes that.
                        view.updateAppWidgetSize(Bundle(), liveWidth, liveHeight, liveWidth, liveHeight)
                    }
                },
                // No backing fill here — any margin the widget's own content
                // doesn't cover (e.g. a square widget's internal padding) should
                // show the feed's wallpaper through it, not a flat theme colour.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(liveHeight.dp)
                    .clip(RoundedCornerShape(20.dp)),
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
                    .clickable { onEditingChange(true) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }

        if (editing) {
            BackHandler { onEditingChange(false) }
            // In-place overlay — not a window-level Popup. A Popup here used to
            // position its own window relative to this widget's anchor, which
            // doesn't reliably track a widget that sits inside a scrolling page:
            // a widget lower on the page could show its controls detached from
            // itself, and scrolling or reordering while editing could dismiss
            // edit mode outright. A plain Box in the same composition scrolls,
            // reorders, and clips exactly like the rest of this widget's content,
            // since it's real Compose layout rather than a separate window.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onEditingChange(false) },
            ) {
                // Top-left: a single drag handle to reorder — press and drag up
                // or down past another widget to swap places with it. Replaces
                // a pair of up/down buttons, which used to also drop out of
                // edit mode on every tap (see the hoisted `editing` state in
                // `WidgetSection`). The reorder itself only commits once, on
                // release — see `onWidgetDragEnd` — so nothing about the list
                // restructures while this drag is still in progress.
                DragHandlePill(
                    accent = accent,
                    widgetId = widget.widgetId,
                    onDragStart = onDragStart,
                    onDragBy = onDragBy,
                    onDragEnd = onDragEnd,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
                // Top-right controls: edit (reconfigure) + remove.
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (info.configure != null) EditPill("edit", accent) { onEdit(info) }
                    EditPill("remove", Color(0xFFD6262B)) { onRemove() }
                }
                // Three independent resize handles — bottom edge (height only),
                // right edge (width only), corner (both at once, diagonal) — so any
                // widget can be resized in whichever direction makes sense for it,
                // rather than the host guessing from its shape. Width dragging
                // moves continuously between halfWidthDp and fullWidthDp for smooth
                // visual feedback, but only those two sizes are ever persisted —
                // crossing the midpoint on release flips this widget's half/full
                // classification (and its row's pairing) rather than storing an
                // arbitrary in-between width.
                val widthFloor = maxOf(WIDGET_MIN_H, providerMinWidthDp(info, density))
                fun settleWidth(): Boolean {
                    val midpoint = (halfWidthDp + fullWidthDp) / 2f
                    val newHalf = liveWidth < midpoint
                    liveWidth = if (newHalf) halfWidthDp else fullWidthDp
                    return newHalf
                }
                ResizeHandle(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    width = 44.dp,
                    height = 6.dp,
                    widgetId = widget.widgetId,
                    onDrag = { _, dy ->
                        liveHeight = (liveHeight + dy / density).roundToInt().coerceIn(WIDGET_MIN_H, WIDGET_MAX_H)
                    },
                    onDragEnd = { onResize(liveHeight, widget.halfWidth) },
                )
                ResizeHandle(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    width = 6.dp,
                    height = 44.dp,
                    widgetId = widget.widgetId,
                    onDrag = { dx, _ ->
                        liveWidth = (liveWidth + dx / density).roundToInt().coerceIn(widthFloor, fullWidthDp)
                    },
                    onDragEnd = { onResize(liveHeight, settleWidth()) },
                )
                ResizeHandle(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    width = 14.dp,
                    height = 14.dp,
                    cornerRadius = 6.dp,
                    widgetId = widget.widgetId,
                    onDrag = { dx, dy ->
                        liveWidth = (liveWidth + dx / density).roundToInt().coerceIn(widthFloor, fullWidthDp)
                        liveHeight = (liveHeight + dy / density).roundToInt().coerceIn(WIDGET_MIN_H, WIDGET_MAX_H)
                    },
                    onDragEnd = { onResize(liveHeight, settleWidth()) },
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
    // Collapsed by default — with widgets spread across many apps, showing every
    // app's full widget list at once is exactly the clutter grouping was meant to
    // fix. Keyed by package name so expanding "Calendar" survives recomposition.
    var expanded by remember { mutableStateOf(setOf<String>()) }
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
                        val isExpanded = group.packageName in expanded
                        item(key = "header/${group.packageName}") {
                            WidgetGroupHeader(
                                packageName = group.packageName,
                                appLabel = group.appLabel,
                                count = group.providers.size,
                                expanded = isExpanded,
                                tokens = tokens,
                                onClick = {
                                    expanded = if (isExpanded) {
                                        expanded - group.packageName
                                    } else {
                                        expanded + group.packageName
                                    }
                                },
                            )
                        }
                        if (isExpanded) {
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
}

@Composable
private fun WidgetGroupHeader(
    packageName: String,
    appLabel: String,
    count: Int,
    expanded: Boolean,
    tokens: ColorTokens,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = rememberAppIconBitmap(packageName)
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$appLabel ($count)",
            color = tokens.fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (expanded) "▾" else "▸",
            color = tokens.fgDim,
            fontSize = 13.sp,
        )
    }
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

/** Press-and-drag handle to reorder a widget — same pill styling as [EditPill], but a drag gesture instead of a click. */
@Composable
private fun DragHandlePill(
    accent: Color,
    widgetId: Int,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        "≡",
        color = Color.White,
        fontSize = 14.sp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent)
            .pointerInput(widgetId) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, drag -> change.consume(); onDragBy(drag) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A small draggable pill/dot used to resize a widget in one or both directions. */
@Composable
private fun BoxScope.ResizeHandle(
    modifier: Modifier,
    width: Dp,
    height: Dp,
    widgetId: Int,
    cornerRadius: Dp = minOf(width, height) / 2,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(alpha = 0.9f))
            .size(width = width, height = height)
            .pointerInput(widgetId) {
                detectDragGestures(
                    onDrag = { change, drag -> change.consume(); onDrag(drag.x, drag.y) },
                    onDragEnd = { onDragEnd() },
                )
            },
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
