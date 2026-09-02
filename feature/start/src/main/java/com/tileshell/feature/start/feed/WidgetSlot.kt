package com.tileshell.feature.start.feed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.CalendarSystemTile
import com.tileshell.core.data.CommodityTile
import com.tileshell.core.data.CountdownTile
import com.tileshell.core.data.SportsTile
import com.tileshell.core.data.StockTile
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.settings.LiveRefreshRate
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.Glass
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.isLightBackground
import com.tileshell.feature.livetiles.AlarmTileFace
import com.tileshell.feature.livetiles.BatteryTileFace
import com.tileshell.feature.livetiles.CalendarFace
import com.tileshell.feature.livetiles.CalendarSystemTileFace
import com.tileshell.feature.livetiles.ClockTileFace
import com.tileshell.feature.livetiles.CommodityTileFace
import com.tileshell.feature.livetiles.CountdownTileFace
import com.tileshell.feature.livetiles.FlashlightTileFace
import com.tileshell.feature.livetiles.FlipState
import com.tileshell.feature.livetiles.MoonPhaseTileFace
import com.tileshell.feature.livetiles.NotesTileFace
import com.tileshell.feature.livetiles.NowPlaying
import com.tileshell.feature.livetiles.SportsTileFace
import com.tileshell.feature.livetiles.StepsTileFace
import com.tileshell.feature.livetiles.StickyNoteTileFace
import com.tileshell.feature.livetiles.StockTileFace
import com.tileshell.feature.livetiles.TasksTileFace
import com.tileshell.feature.livetiles.WeatherSnapshot
import com.tileshell.feature.livetiles.rememberAppIconBitmap
import com.tileshell.feature.livetiles.rememberFlipState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

private const val WIDGET_HOST_ID = 0x54_53 // "TS"
private const val WIDGET_MIN_H = 72
private const val WIDGET_MAX_H = 720

/**
 * Starting heights for the three built-in glance cards the first time they're
 * resized (a `HostedWidget.heightDp` of 0 means "never manually resized" — see
 * [BuiltinCardView]) — a real hosted widget gets its starting height from its
 * provider's own declared minimum; a built-in card has no provider, so these are
 * plain reasonable guesses matching each card's typical un-resized content
 * height. A resize handle drag can only ever grow a card past its own natural
 * content height (see [BuiltinCardView]'s `heightIn(min = ...)`), so a guess
 * that's too small just means the card renders at its natural height until
 * dragged taller — never clipped.
 */
private const val BUILTIN_WEATHER_DEFAULT_HEIGHT_DP = 190
private const val BUILTIN_AGENDA_DEFAULT_HEIGHT_DP = 190
private const val BUILTIN_NOWPLAYING_DEFAULT_HEIGHT_DP = 130

/**
 * Starting height for a just-added custom card, before any manual resize
 * (every custom card is still height-resizable afterward, same as a real
 * hosted widget). Rough per-kind guesses matching each face's typical
 * un-resized content height at [com.tileshell.core.data.TileSize.WIDE] on
 * Start — a guess that's too small just means the card renders at its
 * natural content height until dragged taller, never clipped.
 */
private fun customCardDefaultHeightDp(kind: CustomCardKind): Int = when (kind) {
    CustomCardKind.TASKS -> 220
    CustomCardKind.NOTEPAD, CustomCardKind.STICKYNOTE -> 190
    CustomCardKind.STOCK, CustomCardKind.COMMODITY, CustomCardKind.SPORTS,
    CustomCardKind.CALENDAR_SYSTEM, CustomCardKind.MOONPHASE, CustomCardKind.COUNTDOWN -> 160
    CustomCardKind.CLOCK, CustomCardKind.ALARM -> 140
    CustomCardKind.BATTERY, CustomCardKind.FLASHLIGHT, CustomCardKind.STEPS -> 120
}

/**
 * Auto-rotate interval for a feed widget stack — matches Start's own widget-stack
 * cadence (`STACK_ROTATE_MS` in `StartScreen.kt`): long enough to actually read a
 * widget's content before it moves on.
 */
private const val WIDGET_STACK_ROTATE_MS = 10000L

/**
 * Width of the right-edge strip on a stacked card that captures a vertical drag to
 * flip members, mirroring `STACK_EDGE_DRAG_ZONE_DP` on Start. Deliberately narrow:
 * everywhere else on the card, touches have to reach the hosted widget's own views
 * (taps, internal scrolling, buttons) exactly as they do on an un-stacked widget.
 * Unlike Start's virtual tiles, a real `AppWidgetHostView` has its own interaction
 * to protect, so confining the flip gesture here is load-bearing, not just polish.
 */
private const val WIDGET_STACK_EDGE_ZONE_DP = 40

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
 * The central fraction of a drop target, on both axes, that counts as "merge into
 * this one" rather than "reorder next to it" — the same 22–78% merge zone the Start
 * grid uses for its own tile merges (see CLAUDE.md's normative gesture values).
 */
private const val MERGE_ZONE_MIN = 0.22f
private const val MERGE_ZONE_MAX = 0.78f

/**
 * The much tighter join zone used when the drop target is **already a stack** —
 * roughly its centre third. The wide default zone covers 56% of each axis, which on
 * an existing stack left almost nowhere to aim for "put this next to the stack"
 * (user-reported: another widget couldn't be placed beside one). Joining a stack is
 * the rarer intent of the two, so it's the one that has to be aimed at.
 */
private const val STACK_MERGE_ZONE_MIN = 0.34f
private const val STACK_MERGE_ZONE_MAX = 0.66f

/**
 * Whether a drop point sits inside a target's inner merge zone — i.e. deliberately
 * *on* the target rather than merely overlapping its edge on the way past. The band
 * defaults to [MERGE_ZONE_MIN]/[MERGE_ZONE_MAX] and tightens for an
 * already-stacked target (see [STACK_MERGE_ZONE_MIN]). Takes the rect and point as
 * plain floats so it stays pure and unit-testable with no Compose geometry types.
 */
internal fun isInMergeZone(
    rectLeft: Float,
    rectTop: Float,
    rectWidth: Float,
    rectHeight: Float,
    pointX: Float,
    pointY: Float,
    zoneMin: Float = MERGE_ZONE_MIN,
    zoneMax: Float = MERGE_ZONE_MAX,
): Boolean {
    if (rectWidth <= 0f || rectHeight <= 0f) return false
    val fx = (pointX - rectLeft) / rectWidth
    val fy = (pointY - rectTop) / rectHeight
    return fx in zoneMin..zoneMax && fy in zoneMin..zoneMax
}

/**
 * Whether [dragged] could ever merge into [target]'s stack — everything about the
 * pair EXCEPT where the finger actually is (that's [isInMergeZone]'s job). Pure so
 * the id/kind/stack/width eligibility rules are unit-testable without a real drag.
 *
 * A built-in glance card (sentinel negative id with no [HostedWidget.customKind] —
 * see `BUILTIN_WEATHER_WIDGET_ID` etc.) has no carousel-able content at all, so it
 * can never join or receive a stack. A TileShell custom card is ALSO a negative
 * sentinel id, but its content is our own Compose face rather than a real
 * `AppWidgetHostView` — two custom cards CAN stack with each other exactly like two
 * real hosted widgets can (see `WidgetStackMemberView`'s `customKind` branch), just
 * never mixed with a real hosted widget's own `rememberWidgetInfo`-driven path.
 */
internal fun isStackMergeEligible(dragged: HostedWidget, target: HostedWidget): Boolean {
    if ((dragged.widgetId < 0 && dragged.customKind.isEmpty()) ||
        (target.widgetId < 0 && target.customKind.isEmpty())
    ) {
        return false
    }
    if (dragged.customKind.isNotEmpty() != target.customKind.isNotEmpty()) return false
    // A drag that starts on a stack only ever reorders. Merging is per-widget, so
    // letting a stacked widget merge tore just its dragged member out of the group
    // and dissolved the rest — the stack appeared to fall apart instead of moving
    // (user-reported). Combining two stacks isn't supported.
    if (dragged.stackId != null) return false
    // Half-width and full-width can't share one card, mirroring Start's rule that a
    // stack's members are uniformly sized.
    if (dragged.halfWidth != target.halfWidth) return false
    return true
}

/**
 * One rendered card in the widgets section: either a lone widget or a whole stack
 * shown as a single swipeable carousel. Rows are built out of these rather than out
 * of raw widgets, so a stack can share a row with something else exactly like a lone
 * half-width widget can.
 */
internal sealed class WidgetCard {
    /** Whether this card renders at half the row width. A stack's members always
     * agree on this (only same-width widgets can merge), so the first speaks for all. */
    abstract val halfWidth: Boolean

    /**
     * The widget id that owns this card's on-screen rect and drag identity. For a
     * stack that's its first member: the group renders as one card, so one rect
     * stands for all of it, and both [reorderWidgets] and [mergeIntoStack] resolve
     * any member id to the group it belongs to.
     */
    abstract val hitId: Int

    data class Solo(val widget: HostedWidget) : WidgetCard() {
        override val halfWidth get() = widget.halfWidth
        override val hitId get() = widget.widgetId
    }

    /** ≥2 widgets sharing a [HostedWidget.stackId] — see `WidgetStackView`. */
    data class Stack(val members: List<HostedWidget>) : WidgetCard() {
        override val halfWidth get() = members.first().halfWidth
        override val hitId get() = members.first().widgetId
    }
}

/** One rendered row of the feed's widgets section: one card, or two half-width ones. */
internal sealed class WidgetRow {
    abstract val hitIds: List<Int>

    /** A single card on its own row — a full-width one, or a half-width one with no partner. */
    data class Single(val card: WidgetCard) : WidgetRow() {
        override val hitIds get() = listOf(card.hitId)
    }

    /** Two half-width cards side by side; either may be a stack. */
    data class Pair(val first: WidgetCard, val second: WidgetCard) : WidgetRow() {
        override val hitIds get() = listOf(first.hitId, second.hitId)
    }
}

/**
 * The contiguous run of widgets sharing `widgets[index]`'s [HostedWidget.stackId]
 * (or just `index..index` when it isn't stacked). Group membership is always
 * contiguous in the persisted order — an invariant every mutation here preserves —
 * so this is a cheap outward scan rather than a whole-list grouping pass.
 */
private fun groupSpan(widgets: List<HostedWidget>, index: Int): IntRange {
    val stackId = widgets[index].stackId ?: return index..index
    var start = index
    while (start > 0 && widgets[start - 1].stackId == stackId) start--
    var end = index
    while (end < widgets.size - 1 && widgets[end + 1].stackId == stackId) end++
    return start..end
}

/**
 * Splits the list into ordered blocks — a widget stack is one block (all members,
 * in order); an un-stacked widget is a block of one — so block-level operations
 * can't slice a stack apart.
 */
private fun blocksOf(widgets: List<HostedWidget>): List<List<HostedWidget>> {
    val blocks = mutableListOf<List<HostedWidget>>()
    var i = 0
    while (i < widgets.size) {
        val span = groupSpan(widgets, i)
        blocks.add(widgets.subList(span.first, span.last + 1).toList())
        i = span.last + 1
    }
    return blocks
}

/**
 * Groups the ordered widget list into the cards that will actually be rendered: a
 * contiguous run of ≥2 widgets sharing a [HostedWidget.stackId] becomes one
 * [WidgetCard.Stack]; everything else is a [WidgetCard.Solo]. A group that has
 * somehow dropped to a single member is treated as un-stacked, so a stale
 * `stackId` can never render as a one-member carousel.
 */
private fun cardsOf(widgets: List<HostedWidget>): List<WidgetCard> =
    blocksOf(widgets).map { block ->
        if (block.size > 1) WidgetCard.Stack(block) else WidgetCard.Solo(block.single())
    }

/**
 * Packs the ordered widget list into rows for rendering. Works on whole *cards*
 * ([cardsOf]) rather than raw widgets, so a stack participates in row packing on
 * equal footing with a lone widget: a full-width card gets its own row, two
 * consecutive half-width cards pair side by side (mirroring the built-in
 * weather+today row), and a half-width card left without a partner — an odd count,
 * or its former partner was just removed — still gets its own row at half width,
 * never stretched to fill it just because it's currently alone.
 *
 * A half-width **stack** therefore sits alongside a half-width widget instead of
 * hogging a whole row with dead space beside it, which is what it did when rows were
 * packed from raw widgets and a stack was hardcoded to take a row of its own
 * (user-reported). Pure list logic, no Compose/Android dependency.
 */
internal fun packWidgetRows(widgets: List<HostedWidget>): List<WidgetRow> {
    val rows = mutableListOf<WidgetRow>()
    var pendingHalf: WidgetCard? = null

    fun flushPendingHalf() {
        pendingHalf?.let { rows.add(WidgetRow.Single(it)) }
        pendingHalf = null
    }

    for (card in cardsOf(widgets)) {
        if (card.halfWidth) {
            val prev = pendingHalf
            if (prev != null) {
                rows.add(WidgetRow.Pair(prev, card))
                pendingHalf = null
            } else {
                pendingHalf = card
            }
        } else {
            flushPendingHalf()
            rows.add(WidgetRow.Single(card))
        }
    }
    flushPendingHalf()
    return rows
}

/**
 * Move [dragId] to sit where [targetId] currently is — same splice-and-reinsert
 * algorithm as [com.tileshell.feature.start.reorderTiles] (a forward drag lands
 * after the target, a backward drag lands before it), just keyed by widget id
 * instead of tile id since widgets aren't part of the tile grid. Operates on whole
 * [blocksOf] blocks, so dragging a stacked widget (or dropping onto one) moves or
 * targets its entire stack as a unit — a plain reorder never splits a stack;
 * forming and leaving one is [mergeIntoStack]/[removeFromStack]'s job. Returns a
 * new list; the input is untouched. No-op when either id is absent, the two are
 * equal, or both already sit in the same block.
 */
internal fun reorderWidgets(widgets: List<HostedWidget>, dragId: Int, targetId: Int): List<HostedWidget> {
    if (dragId == targetId) return widgets
    val blocks = blocksOf(widgets)
    val di = blocks.indexOfFirst { block -> block.any { it.widgetId == dragId } }
    val ti = blocks.indexOfFirst { block -> block.any { it.widgetId == targetId } }
    if (di < 0 || ti < 0 || di == ti) return widgets
    val out = blocks.toMutableList()
    val draggedBlock = out.removeAt(di)
    out.add(ti.coerceAtMost(out.size), draggedBlock)
    return out.flatten()
}

/**
 * Groups [draggedId] into the same widget stack as [targetId] — what dropping one
 * widget's card onto another's merge zone does, forming a swipeable carousel
 * instead of two rows. The dragged widget leaves its own position (dissolving its
 * former group first if that would strand a single member there) and is reinserted
 * directly after the target's group span, adopting the target's stack id — freshly
 * minted from the target's own [HostedWidget.widgetId] when the target wasn't
 * stacked yet. No-op when the two ids are equal, either is absent, or both are
 * already in one group.
 *
 * Pure list logic: enforcing that only same-width widgets merge is the caller's
 * job (see `WidgetSection`'s drag hit-test), not this function's.
 */
internal fun mergeIntoStack(widgets: List<HostedWidget>, draggedId: Int, targetId: Int): List<HostedWidget> {
    if (draggedId == targetId) return widgets
    val di = widgets.indexOfFirst { it.widgetId == draggedId }
    val ti = widgets.indexOfFirst { it.widgetId == targetId }
    if (di < 0 || ti < 0) return widgets
    val dragged = widgets[di]
    val target = widgets[ti]
    if (dragged.stackId != null && dragged.stackId == target.stackId) return widgets

    val out = widgets.toMutableList()
    out.removeAt(di)
    // Leaving a group behind: if only one member is left there, it stops being a stack.
    val formerStackId = dragged.stackId
    if (formerStackId != null && out.count { it.stackId == formerStackId } == 1) {
        val soleIdx = out.indexOfFirst { it.stackId == formerStackId }
        out[soleIdx] = out[soleIdx].copy(stackId = null)
    }

    val newTi = out.indexOfFirst { it.widgetId == targetId }
    if (newTi < 0) return widgets
    val stackId = target.stackId ?: target.widgetId
    if (target.stackId == null) out[newTi] = out[newTi].copy(stackId = stackId)
    val span = groupSpan(out, newTi)
    out.add((span.last + 1).coerceAtMost(out.size), dragged.copy(stackId = stackId))
    return out
}

/**
 * Takes [widgetId] out of its widget stack, leaving it in place as its own row just
 * after the group it left. If that leaves only one member behind, the group
 * dissolves entirely (a stack of one doesn't exist). No-op when the widget isn't
 * stacked or isn't found.
 */
internal fun removeFromStack(widgets: List<HostedWidget>, widgetId: Int): List<HostedWidget> {
    val idx = widgets.indexOfFirst { it.widgetId == widgetId }
    if (idx < 0) return widgets
    val stackId = widgets[idx].stackId ?: return widgets
    val out = widgets.toMutableList()
    out[idx] = out[idx].copy(stackId = null)
    if (out.count { it.stackId == stackId } == 1) {
        val soleIdx = out.indexOfFirst { it.stackId == stackId }
        out[soleIdx] = out[soleIdx].copy(stackId = null)
    }
    return out
}

/**
 * The shared card height for a widget stack: the largest of its members' own
 * persisted heights, so flipping between differently-sized widgets never makes the
 * card jump. Resizing the stack writes the new height to every member
 * ([WidgetStore.setStackSize]), which converges them.
 */
internal fun stackHeightDp(members: List<HostedWidget>): Int = members.maxOf { effectiveMemberHeightDp(it) }

/**
 * A member's real height, falling back to its own kind's default when it's
 * never been manually resized ([HostedWidget.heightDp] `0`, the value every
 * custom card starts at — see [WidgetStore.addCustomCard]). A lone custom
 * card already gets this fallback via [BuiltinCardView]'s own `heightDp > 0`
 * check; [stackHeightDp] took the raw stored value with no such fallback, so
 * a stack whose members had never been resized collapsed to 0dp and rendered
 * as nothing — a real widget still occupying its row's width but showing
 * blank, which read as "the other half of this row is empty and can't be
 * dropped into" (user-reported). A real hosted widget's own height is always
 * seeded to a real value when it's first bound (never via this code path with
 * `customKind` blank), so the 110dp floor below is a defensive fallback, not
 * an expected case.
 */
private fun effectiveMemberHeightDp(widget: HostedWidget): Int {
    if (widget.heightDp > 0) return widget.heightDp
    val kind = CustomCardKind.entries.find { it.iconKey == widget.customKind }
    return kind?.let { customCardDefaultHeightDp(it) } ?: 110
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
 * Hosts any number of Android app widgets on the feed's glance tab, **plus** the
 * three built-in glance cards (weather/agenda/now-playing) folded into the same
 * ordered list — see [BUILTIN_WEATHER_WIDGET_ID] etc. Self-contained: owns an
 * [AppWidgetHost] (started while composed), adds widgets through a custom
 * preview picker + the bind/configure flow (via activity-result launchers — the
 * composition is already activity-hosted, so `:app` needs no plumbing), persists the
 * bound ids + heights in [WidgetStore], and renders each live
 * [android.appwidget.AppWidgetHostView] through [AndroidView] at its stored height.
 * All guarded — a device that blocks third-party hosting just shows the "add a
 * widget" prompt.
 *
 * A single [editMode] toggle (see [FeedPage]'s "edit"/"done" header action, and
 * Quick Panel's identically-shaped toggle) drives every card's resize/move handles
 * at once — there's no more per-card "edit" tap-in.
 */
@Composable
fun WidgetSection(
    accent: Color,
    tokens: ColorTokens,
    labelColor: Color = tokens.fgDim,
    weatherSnapshot: WeatherSnapshot?,
    onWeatherClick: () -> Unit,
    agenda: CalendarFace,
    calendarGranted: Boolean,
    onAddSchedule: () -> Unit,
    onAgendaClick: () -> Unit,
    nowPlaying: NowPlaying?,
    nowPlayingPackage: String?,
    nowPlayingArt: Bitmap?,
    onNowPlayingClick: (() -> Unit)?,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    // For sizing the "tileshell card" cards' live faces to match
    // Personalize's "live data refresh" setting.
    stockRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    commodityRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    sportsRefreshRate: LiveRefreshRate = LiveRefreshRate.DEFAULT,
    // A new or re-tapped custom card's own picker sheet can't be rendered
    // from in here — this composable sits inside FeedPage's own
    // Modifier.verticalScroll() column, and those picker sheets contain a
    // LazyColumn, which crashes ("measured with infinite height") if
    // composed anywhere under a vertical scroll. FeedPage owns that sheet
    // instead, rendered from its own non-scrolling outer Box; these two
    // callbacks are how this composable asks for one to open.
    onAddCustomCard: (CustomCardKind) -> Unit = {},
    onCustomCardTap: (widgetId: Int, kind: CustomCardKind) -> Unit = { _, _ -> },
) {
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
    // Custom cards flip between their two faces the same way Start's own live
    // tiles do — one shared scheduler (rememberFlipState, from :feature:
    // livetiles, already generic — not Start-specific) turning a random one
    // every ~2.6s, paused while editing.
    val customCardIds = remember(widgets.widgets) {
        widgets.widgets.filter { it.customKind.isNotEmpty() }.map { it.widgetId.toString() }
    }
    val customCardFlipState = rememberFlipState(customCardIds, active = !editMode)
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
    LaunchedEffect(Unit) { store.seedBuiltinsIfAbsent() }

    val widgetBounds = remember { mutableStateMapOf<Int, Rect>() }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragTargetId by remember { mutableStateOf<Int?>(null) }
    // Whether releasing right now would MERGE into `dragTargetId` (forming a widget
    // stack) rather than reorder next to it — true only while the drop point sits
    // well inside the target and both sides render at the same width. See below.
    var dragMergeCandidate by remember { mutableStateOf(false) }

    fun onWidgetDragBy(id: Int, delta: Offset) {
        dragDelta += delta
        val origin = widgetBounds[id]?.center ?: return
        val point = origin + dragDelta
        val hit = widgetBounds.entries
            .firstOrNull { (otherId, rect) -> otherId != id && rect.contains(point) }
        dragTargetId = hit?.key
        val rect = hit?.value
        val dragged = widgets.widgets.firstOrNull { it.widgetId == id }
        val target = hit?.let { h -> widgets.widgets.firstOrNull { it.widgetId == h.key } }
        // Conditions for a merge; a miss on any of them means a plain reorder.
        dragMergeCandidate = when {
            rect == null || dragged == null || target == null -> false
            !isStackMergeEligible(dragged, target) -> false
            // Aim required: the drop has to land in the target's inner zone, not just
            // brush its edge on the way past — and much closer to dead centre when the
            // target is already a stack, so "place this beside it" stays easy.
            else -> {
                val stacked = target.stackId != null
                isInMergeZone(
                    rect.left, rect.top, rect.width, rect.height, point.x, point.y,
                    zoneMin = if (stacked) STACK_MERGE_ZONE_MIN else MERGE_ZONE_MIN,
                    zoneMax = if (stacked) STACK_MERGE_ZONE_MAX else MERGE_ZONE_MAX,
                )
            }
        }
    }

    fun onWidgetDragEnd(id: Int) {
        val target = dragTargetId
        if (target != null) {
            val current = widgets.widgets
            val next = if (dragMergeCandidate) {
                mergeIntoStack(current, id, target)
            } else {
                reorderWidgets(current, id, target)
            }
            // `reorder` replaces the list wholesale, so it persists a merge's
            // stackId changes just as well as a pure resequence.
            if (next != current) scope.launch { store.reorder(next) }
        }
        draggingId = null
        dragDelta = Offset.Zero
        dragTargetId = null
        dragMergeCandidate = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            "widgets", actionText = "add", accent = accent, labelColor = labelColor, showPlus = true, onAction = { showPicker = true },
            secondaryActionText = if (editMode) "done" else "edit",
            onSecondaryAction = { onEditModeChange(!editMode) },
        )

        // The now-playing built-in card keeps its reserved slot in the *persisted*
        // order regardless of whether anything's playing right now — only the
        // render pass drops it, so it reappears in the same relative position once
        // playback resumes instead of losing its place in the list. Any half-width
        // neighbor it was paired with just gets its own solo row for this pass,
        // exactly like packWidgetRows already handles an orphaned half-width card.
        val renderedWidgets = remember(widgets.widgets, nowPlaying) {
            if (nowPlaying == null) widgets.widgets.filterNot { it.widgetId == BUILTIN_NOWPLAYING_WIDGET_ID }
            else widgets.widgets
        }
        val rows = remember(renderedWidgets) { packWidgetRows(renderedWidgets) }

        // Only ids that currently own a rendered card belong in `widgetBounds`. A
        // stack reports one rect under its first member's id, so its other members
        // have no live rect at all — without pruning, a just-merged member's stale
        // pre-merge rect would linger as a phantom drop target.
        val hitIds = remember(rows) { rows.flatMap { it.hitIds }.toSet() }
        LaunchedEffect(hitIds) { widgetBounds.keys.retainAll(hitIds) }

        fun reconfigure(widgetId: Int, info: AppWidgetProviderInfo) {
            val cfg = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            runCatching { editLauncher.launch(cfg) }
        }

        fun deleteWidget(widgetId: Int) {
            runCatching { host.deleteAppWidgetId(widgetId) }
            scope.launch { store.remove(widgetId) }
        }

        @Composable
        fun widgetView(hw: HostedWidget, modifier: Modifier) {
            key(hw.widgetId) {
                WidgetView(
                    host = host,
                    manager = manager,
                    widget = hw,
                    widthDp = widthDp,
                    accent = accent,
                    editing = editMode,
                    onEditingChange = { onEditModeChange(false) },
                    onRequestEdit = { onEditModeChange(true) },
                    isDragging = draggingId == hw.widgetId,
                    dragOffset = if (draggingId == hw.widgetId) dragDelta else Offset.Zero,
                    isMergeTarget = dragTargetId == hw.widgetId && dragMergeCandidate,
                    onDragStart = {
                        draggingId = hw.widgetId
                        dragDelta = Offset.Zero
                        dragTargetId = null
                        dragMergeCandidate = false
                    },
                    onDragBy = { delta -> onWidgetDragBy(hw.widgetId, delta) },
                    onDragEnd = { onWidgetDragEnd(hw.widgetId) },
                    onBoundsChanged = { rect -> widgetBounds[hw.widgetId] = rect },
                    onResize = { newH, newHalf -> scope.launch { store.setSize(hw.widgetId, newH, newHalf) } },
                    onEdit = { info -> reconfigure(hw.widgetId, info) },
                    onRemove = { deleteWidget(hw.widgetId) },
                    modifier = modifier,
                )
            }
        }

        @Composable
        fun stackView(members: List<HostedWidget>, modifier: Modifier) {
            // The group's first member is its handle for everything list-level:
            // bounds reporting, drag identity, and edit state. `reorderWidgets` and
            // `mergeIntoStack` both resolve a member id to its whole group, so one
            // id is enough to move or target the stack as a unit.
            val anchor = members.first()
            val stackId = anchor.stackId ?: anchor.widgetId
            key(stackId) {
                WidgetStackView(
                    host = host,
                    manager = manager,
                    members = members,
                    widthDp = widthDp,
                    accent = accent,
                    editing = editMode,
                    customCardFlipState = customCardFlipState,
                    stockRefreshRate = stockRefreshRate,
                    commodityRefreshRate = commodityRefreshRate,
                    sportsRefreshRate = sportsRefreshRate,
                    onCustomCardTap = onCustomCardTap,
                    onEditingChange = { onEditModeChange(false) },
                    onRequestEdit = { onEditModeChange(true) },
                    isDragging = draggingId == anchor.widgetId,
                    dragOffset = if (draggingId == anchor.widgetId) dragDelta else Offset.Zero,
                    isMergeTarget = dragTargetId == anchor.widgetId && dragMergeCandidate,
                    onDragStart = {
                        draggingId = anchor.widgetId
                        dragDelta = Offset.Zero
                        dragTargetId = null
                        dragMergeCandidate = false
                    },
                    onDragBy = { delta -> onWidgetDragBy(anchor.widgetId, delta) },
                    onDragEnd = { onWidgetDragEnd(anchor.widgetId) },
                    onBoundsChanged = { rect -> widgetBounds[anchor.widgetId] = rect },
                    // One card, one size: a resize writes through to every member.
                    onResize = { newH, newHalf -> scope.launch { store.setStackSize(stackId, newH, newHalf) } },
                    onEdit = { widgetId, info -> reconfigure(widgetId, info) },
                    onRemove = { widgetId -> deleteWidget(widgetId) },
                    onUnstack = { widgetId ->
                        scope.launch { store.reorder(removeFromStack(store.read().widgets, widgetId)) }
                    },
                    modifier = modifier,
                )
            }
        }

        @Composable
        fun builtinCardView(
            hw: HostedWidget,
            defaultHeightDp: Int,
            modifier: Modifier,
            removable: Boolean = false,
            onRemove: () -> Unit = {},
            content: @Composable () -> Unit,
        ) {
            key(hw.widgetId) {
                BuiltinCardView(
                    id = hw.widgetId,
                    halfWidth = hw.halfWidth,
                    heightDp = hw.heightDp,
                    defaultHeightDp = defaultHeightDp,
                    widthDp = widthDp,
                    accent = accent,
                    editing = editMode,
                    isDragging = draggingId == hw.widgetId,
                    dragOffset = if (draggingId == hw.widgetId) dragDelta else Offset.Zero,
                    onDragStart = {
                        draggingId = hw.widgetId
                        dragDelta = Offset.Zero
                        dragTargetId = null
                        dragMergeCandidate = false
                    },
                    onDragBy = { delta -> onWidgetDragBy(hw.widgetId, delta) },
                    onDragEnd = { onWidgetDragEnd(hw.widgetId) },
                    onBoundsChanged = { rect -> widgetBounds[hw.widgetId] = rect },
                    onResize = { newHeight, newHalf -> scope.launch { store.setSize(hw.widgetId, newHeight, newHalf) } },
                    onDismiss = { onEditModeChange(false) },
                    removable = removable,
                    onRemove = onRemove,
                    modifier = modifier,
                    content = content,
                )
            }
        }

        @Composable
        fun customCardView(hw: HostedWidget, modifier: Modifier) {
            val kind = CustomCardKind.entries.find { it.iconKey == hw.customKind } ?: return
            builtinCardView(
                hw,
                customCardDefaultHeightDp(kind),
                modifier,
                removable = true,
                onRemove = { deleteWidget(hw.widgetId) },
            ) {
                // Same accent-filled card background weather/agenda/now-playing
                // already use (AccentCard) — these tile faces render their own
                // text via LocalTileFaceColor, designed for exactly this kind
                // of accent-filled background, same as on Start. Only a
                // needsConfig kind gets an outer tap-to-configure handler — a
                // no-config kind's own interactive elements (the flashlight
                // toggle, a task checkbox) need the tap to reach them
                // directly, not get consumed by an outer no-op click first.
                //
                // Long-press enters edit mode everywhere, the same as tapping
                // the header's "edit" action — except on FLASHLIGHT, whose
                // whole card *is* its own tap-to-toggle (FlashlightTileFace's
                // own `.clickable`); giving AccentCard a long-press there would
                // force a non-null onClick too (combinedClickable requires
                // one), which would steal the toggle's own tap.
                AccentCard(
                    accent = accent,
                    onClick = if (editMode || !kind.needsConfig) null else ({ onCustomCardTap(hw.widgetId, kind) }),
                    onLongClick = if (editMode || kind == CustomCardKind.FLASHLIGHT) null else ({ onEditModeChange(true) }),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CustomCardBody(
                        kind = kind,
                        hw = hw,
                        accent = accent,
                        editMode = editMode,
                        customCardFlipState = customCardFlipState,
                        stockRefreshRate = stockRefreshRate,
                        commodityRefreshRate = commodityRefreshRate,
                        sportsRefreshRate = sportsRefreshRate,
                    )
                }
            }
        }

        // A card is a lone widget, a whole stack, or one of the three built-in
        // glance cards (sentinel negative id — see BUILTIN_WEATHER_WIDGET_ID); all
        // take part in row packing the same way, so a half-width stack (or a
        // built-in) shares its row with a neighbour instead of leaving dead space
        // beside it.
        @Composable
        fun cardView(card: WidgetCard, modifier: Modifier) {
            when (card) {
                is WidgetCard.Solo -> when (card.widget.widgetId) {
                    // Long-press enters edit mode on every glance card, built-ins
                    // included — the same effect as the header's "edit" action.
                    BUILTIN_WEATHER_WIDGET_ID -> builtinCardView(card.widget, BUILTIN_WEATHER_DEFAULT_HEIGHT_DP, modifier) {
                        WeatherCard(
                            snapshot = weatherSnapshot, accent = accent, onClick = onWeatherClick,
                            onLongClick = if (editMode) null else ({ onEditModeChange(true) }),
                        )
                    }
                    BUILTIN_AGENDA_WIDGET_ID -> builtinCardView(card.widget, BUILTIN_AGENDA_DEFAULT_HEIGHT_DP, modifier) {
                        AgendaCard(
                            agenda = agenda, granted = calendarGranted, accent = accent,
                            onAddSchedule = onAddSchedule, onClick = onAgendaClick,
                            onLongClick = if (editMode) null else ({ onEditModeChange(true) }),
                        )
                    }
                    // renderedWidgets already drops this sentinel whenever nowPlaying is
                    // null, so it's always non-null by the time this branch renders.
                    BUILTIN_NOWPLAYING_WIDGET_ID -> builtinCardView(card.widget, BUILTIN_NOWPLAYING_DEFAULT_HEIGHT_DP, modifier) {
                        NowPlayingCard(
                            nowPlaying = nowPlaying!!, packageName = nowPlayingPackage,
                            art = nowPlayingArt, accent = accent, onClick = onNowPlayingClick,
                            onLongClick = if (editMode) null else ({ onEditModeChange(true) }),
                        )
                    }
                    else -> if (card.widget.customKind.isNotEmpty()) customCardView(card.widget, modifier) else widgetView(card.widget, modifier)
                }
                is WidgetCard.Stack -> stackView(card.members, modifier)
            }
        }

        rows.forEach { row ->
            when (row) {
                // Back to Modifier.weight(1f): the REAL layout width of a paired
                // card is only ever its committed half-share now (see
                // WidgetView/BuiltinCardView's committedWidthDp) — the live drag
                // preview is a graphicsLayer scale on top, which paints past the
                // weighted cell's own bounds without needing to escape the
                // constraint at all. (A prior attempt dropped weight(1f) instead,
                // relying on a real relayout every drag frame — technically un-
                // capped, but the per-frame reflow of real card/widget content is
                // what actually read as "not smooth"; the scale-based preview
                // fixes that at the source, so the safer weighted layout is back.)
                is WidgetRow.Pair -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    cardView(row.first, Modifier.weight(1f))
                    cardView(row.second, Modifier.weight(1f))
                }
                is WidgetRow.Single -> cardView(row.card, Modifier.fillMaxWidth())
            }
        }
    }

    if (showPicker) {
        WidgetPicker(
            manager = manager,
            tokens = tokens,
            onPick = { provider -> showPicker = false; addProvider(provider) },
            onPickCustom = { kind -> showPicker = false; onAddCustomCard(kind) },
            onDismiss = { showPicker = false },
            notesAlreadyPinned = widgets.widgets.any { it.customKind == CustomCardKind.NOTEPAD.iconKey },
        )
    }
}

/**
 * Resolves a bound widget's provider info, tolerating providers that register
 * asynchronously.
 *
 * Some OEMs (Samsung's Glance-based widgets — spage news, notes, reminder, Device
 * Care, Digital Wellbeing — confirmed via their async GWT/"Kumiho"/
 * `androidx.glance.session.SessionWorker` provider-registration path in logcat)
 * don't have the info ready the instant a widget is bound, so a null read right
 * after add doesn't necessarily mean "uninstalled." A 2s grace period (4×500ms) was
 * enough for spage but not for Device Care/Digital Wellbeing — confirmed on-device
 * they were being deleted by this exact logic on every cold app start (many widgets
 * registering at once slows each one down further) even though they're pre-installed
 * system apps that can never actually be "uninstalled." Widened to ~15s before
 * concluding it's actually gone, instead of deleting on the spot; only then is
 * [onMissing] called.
 */
@Composable
private fun rememberWidgetInfo(
    manager: AppWidgetManager,
    widgetId: Int,
    onMissing: () -> Unit,
): AppWidgetProviderInfo? {
    var infoState by remember(widgetId) { mutableStateOf(manager.getAppWidgetInfo(widgetId)) }
    val missing = rememberUpdatedState(onMissing)
    LaunchedEffect(widgetId) {
        if (infoState == null) {
            repeat(15) {
                delay(1000)
                infoState = manager.getAppWidgetInfo(widgetId)
                if (infoState != null) return@LaunchedEffect
            }
            missing.value()
        }
    }
    return infoState
}

/**
 * Long-press-to-enter-edit-mode duration for every glance widget/card — real
 * hosted widgets ([LongPressPassthroughFrame]) and the Compose-rendered ones
 * (`AccentCard`'s `combinedClickable`, `FeedPage.kt`) alike. Deliberately
 * longer than the platform's own long-press timeout (~500ms), the same class
 * of deliberate deviation as the app list's own long-press-to-pin (`700ms`,
 * see `AppListScreen.kt`'s `APP_LIST_LONG_PRESS_MS`) — a glance card competes
 * with more surrounding gestures than a plain list row (the page's own
 * vertical scroll, the two-finger quick-panel/quick-search swipes, drag-to-
 * reorder/resize once already editing), so a shorter window is more prone to
 * misfiring. User-reported: edit mode opening on its own "at the time of
 * scrolling up" — a scroll that starts with a brief dwell (finger pauses,
 * then drags) can sit still for the whole default timeout before the drag
 * itself moves far enough to register, especially on a page that's mostly
 * live-tile cards rather than a plain button/list row.
 */
internal const val GLANCE_LONG_PRESS_MS = 900L

/**
 * Long-press cancellation distance, as a fraction of the platform's own touch
 * slop, for every glance widget/card's long-press-to-edit detector (native
 * [LongPressPassthroughFrame] and Compose `combinedClickable` via
 * `GlanceLongPressViewConfiguration`, `FeedPage.kt`, alike). User-reported: "a
 * slight press and scroll up ... open in edit mode, and same for scroll
 * down" — [GLANCE_LONG_PRESS_MS]'s 900ms window alone only cancels a long
 * press once the finger has moved past the *full* platform touch slop
 * (~8dp), so a deliberate but slow/gentle scroll — one that stays within that
 * distance of its starting point for a while before picking up — could still
 * sit through the whole timeout and fire the long-press. A page that's
 * mostly live-tile cards has no other affordance competing for a small,
 * careful drag the way a plain button does, so shrinking the cancel-distance
 * threshold (not the timeout, which stays 900ms for a genuinely still press)
 * makes any real, sustained directional movement cancel much sooner, without
 * making a stationary press-and-hold any more sensitive to natural finger
 * tremor.
 */
internal const val GLANCE_LONG_PRESS_TOUCH_SLOP_SCALE = 0.4f

/**
 * A [FrameLayout] that watches for a long-press over its content without ever
 * intercepting or consuming a touch — [onInterceptTouchEvent] always returns
 * `false`, so every event still reaches the real [AppWidgetHostView] child
 * exactly as it would with no wrapper at all. This is the standard Android
 * technique for detecting one gesture "alongside" a child that owns its own
 * touch handling, and it's the only safe way to add long-press-to-edit over a
 * real hosted widget: a Compose `pointerInput`/`combinedClickable` wrapping
 * the `AndroidView` would have to CONSUME part of the gesture to recognize
 * it, which would steal taps/scrolls/button presses the widget's own content
 * needs (a real regression this app hit once already — see `AccentCard`'s
 * `onLongClick` doc comment for the same lesson on the FLASHLIGHT custom
 * card, whose fix works differently only because that content is Compose,
 * not a native `AndroidView`).
 *
 * Runs its own [GLANCE_LONG_PRESS_MS] timer via a plain [Handler] instead of
 * `GestureDetector` — a raw `GestureDetector`'s long-press timeout comes from
 * the platform's `ViewConfiguration.getLongPressTimeout()`, which can't be
 * overridden to match [GLANCE_LONG_PRESS_MS], and its cancellation doesn't
 * reliably account for a second finger landing (e.g. the two-finger
 * quick-panel/quick-search swipe starting over a widget) the way this
 * explicit [MotionEvent.ACTION_POINTER_DOWN] check does.
 */
private class LongPressPassthroughFrame(context: Context) : FrameLayout(context) {
    var onLongPress: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlopSquare = (ViewConfiguration.get(context).scaledTouchSlop * GLANCE_LONG_PRESS_TOUCH_SLOP_SCALE)
        .let { it * it }
    private var downX = 0f
    private var downY = 0f
    private val fireLongPress = Runnable { onLongPress?.invoke() }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                handler.removeCallbacks(fireLongPress)
                handler.postDelayed(fireLongPress, GLANCE_LONG_PRESS_MS)
            }
            // A second finger means a multi-finger gesture — never a long-press.
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                handler.removeCallbacks(fireLongPress)
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (dx * dx + dy * dy > touchSlopSquare) handler.removeCallbacks(fireLongPress)
            }
        }
        return false
    }
}

/**
 * Hosts one live [AppWidgetHostView] at the given size. Shared by the plain
 * per-row widget and each member of a widget stack, so there's a single place
 * that knows how to talk to the host and report a size to the provider.
 *
 * [onLongPress] (when non-null) enters edit mode, the same as long-pressing
 * any other glance card — see [LongPressPassthroughFrame] for why a real
 * hosted widget needs this rather than a plain Compose long-press modifier.
 */
@Composable
private fun WidgetHostedView(
    host: AppWidgetHost,
    widgetId: Int,
    info: AppWidgetProviderInfo,
    contentWidthDp: Int,
    heightDp: Int,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    // User-reported: "borders are square [while] as glance card borders are
    // curved" — the caller's own Modifier.clip(RoundedCornerShape(20.dp))
    // (below, at both call sites) reliably rounds a Compose-drawn card's own
    // background, but does not reliably round a real hosted
    // AppWidgetHostView's native content. Clipping at the native level
    // (ViewOutlineProvider + clipToOutline + an explicit hardware layer, on
    // both the real widget view and its wrapper) is a further best-effort
    // attempt at the same fix — confirmed via temporary instrumentation to
    // run correctly (right size, right radius, hardware-accelerated,
    // attached) but still not visibly rounding every widget's corners
    // on-device, most likely because these widgets flip faces via an inner
    // ViewFlipper and Android's clipToOutline is known to be unreliable
    // against animating children. Left in as a harmless best effort rather
    // than reverted outright; a fully reliable fix (e.g. rendering the
    // widget through a snapshot bitmap that Compose can clip like any other
    // image) is a larger change than this pass covers.
    val cornerRadiusPx = with(LocalDensity.current) { 20.dp.toPx() }
    key(widgetId) {
        AndroidView(
            factory = { ctx ->
                val hosted = host.createView(ctx.applicationContext, widgetId, info)
                val roundedOutline = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                    }
                }
                hosted.elevation = 0f
                hosted.clipToOutline = true
                hosted.outlineProvider = roundedOutline
                hosted.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                LongPressPassthroughFrame(ctx).apply {
                    // `onLongPress` unqualified here would resolve to the closure-
                    // captured composable parameter of the same name (shadows the
                    // receiver), not this instance's own field — `this.` disambiguates.
                    this.onLongPress = { currentOnLongPress?.invoke() }
                    addView(hosted, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    clipToOutline = true
                    outlineProvider = roundedOutline
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }
            },
            update = { frame ->
                val hosted = frame.getChildAt(0) as AppWidgetHostView
                runCatching {
                    // Bundle.EMPTY is Android's immutable singleton — updateAppWidgetSize
                    // calls putInt() on the options bundle internally, which threw
                    // UnsupportedOperationException here (silently, into this runCatching)
                    // on every call, so the provider never actually learned its real size
                    // and kept rendering its smallest/narrowest layout regardless of how
                    // big our container was. A fresh mutable Bundle fixes that.
                    hosted.updateAppWidgetSize(Bundle(), contentWidthDp, heightDp, contentWidthDp, heightDp)
                }
            },
            // No backing fill here — any margin the widget's own content doesn't
            // cover (e.g. a square widget's internal padding) should show the feed's
            // wallpaper through it, not a flat theme colour.
            modifier = modifier,
        )
    }
}

/**
 * Accent outline drawn over a card that a drag is currently hovering in "merge"
 * position — releasing here groups the two into a widget stack rather than
 * reordering, so the distinction needs to be visible before the finger lifts.
 */
@Composable
private fun BoxScope.MergeTargetHighlight(accent: Color) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.18f))
            .border(2.dp, accent, RoundedCornerShape(20.dp)),
    )
}

/**
 * The in-place edit overlay shared by a plain widget card and a widget stack's card:
 * scrim, reorder drag handle, reconfigure/remove actions, and the three resize
 * handles. [extraActions] lets a stack add its own "unstack" action alongside the
 * standard ones.
 *
 * Not a window-level `Popup`. A Popup here used to position its own window relative
 * to the card's anchor, which doesn't reliably track a card inside a scrolling page:
 * one lower on the page could show its controls detached from itself, and scrolling
 * or reordering while editing could dismiss edit mode outright. A plain Box in the
 * same composition scrolls, reorders, and clips exactly like the card's own content,
 * since it's real Compose layout rather than a separate window.
 *
 * The live sizes come in as [MutableState] rather than plain values on purpose: the
 * resize handles' gesture callbacks are captured once per `pointerInput` block, so
 * reading through the state is what keeps them seeing the current size instead of
 * whatever it was when the gesture started.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.WidgetEditOverlay(
    accent: Color,
    dragKey: Int,
    /** Null for a built-in glance card (weather/agenda/now-playing — see cardsOf's
     *  sentinel-id integration), which has no real AppWidgetHost provider. Only
     *  gates the "edit"/reconfigure pill and the width-drag provider-minimum floor;
     *  everything else (drag, resize handles, remove) works identically either way. */
    info: AppWidgetProviderInfo?,
    halfWidthDp: Int,
    fullWidthDp: Int,
    liveWidthState: MutableState<Int>,
    liveHeightState: MutableState<Int>,
    currentHalfWidth: () -> Boolean,
    /** False for a built-in glance card (B6: fixed content-sized height, not
     *  drag-resizable) — hides the height and corner (both-axes) handles, leaving
     *  only the width handle. */
    resizableHeight: Boolean = true,
    /** False for a built-in glance card — it can be reordered/resized but never removed. */
    removable: Boolean = true,
    onResize: (heightDp: Int, halfWidth: Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onEdit: (AppWidgetProviderInfo) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    extraActions: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current.density
    var liveWidth by liveWidthState
    var liveHeight by liveHeightState

    BackHandler { onDismiss() }
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        // Action pills in their own top-right row (edit/remove/unstack).
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                extraActions()
                if (info != null && info.configure != null) EditPill("edit", accent) { onEdit(info) }
                if (removable) EditPill("remove", Color(0xFFD6262B)) { onRemove() }
            }
        }
        // Move/reorder handle — top-center, straddling the card's own top edge,
        // matching Quick Panel's identically-shaped-and-positioned move handle
        // exactly (same grip-dot pill, same offset) per explicit user request
        // ("use same type of handle and position even on glance screen") — no
        // longer sharing a row with the action pills (that used to squeeze both
        // into the top-left corner; a stack's extra "unstack" pill could crowd
        // the handle out entirely on a narrow card).
        //
        // Drag it up or down past another card to swap places with it, or onto
        // its centre to stack them. The reorder only commits once, on release —
        // see `onWidgetDragEnd` — so the list never restructures mid-gesture.
        DragHandlePill(
            accent = accent,
            widgetId = dragKey,
            onDragStart = onDragStart,
            onDragBy = onDragBy,
            onDragEnd = onDragEnd,
        )
        // Three independent resize handles — bottom edge (height only), right edge
        // (width only), corner (both at once, diagonal) — so any widget can be
        // resized in whichever direction makes sense for it, rather than the host
        // guessing from its shape. Width dragging moves continuously between
        // halfWidthDp and fullWidthDp for smooth visual feedback, but only those two
        // sizes are ever persisted — crossing the midpoint on release flips the
        // half/full classification (and the row's pairing) rather than storing an
        // arbitrary in-between width. A built-in glance card has no provider to floor
        // against and (per resizableHeight) no height/corner handles at all — only
        // its width can be dragged, between a fixed half/full pair.
        val widthFloor = maxOf(WIDGET_MIN_H, info?.let { providerMinWidthDp(it, density) } ?: WIDGET_MIN_H)
        fun settleWidth(): Boolean {
            val midpoint = (halfWidthDp + fullWidthDp) / 2f
            val newHalf = liveWidth < midpoint
            liveWidth = if (newHalf) halfWidthDp else fullWidthDp
            return newHalf
        }
        // Positioned with an OUTWARD offset (past the card's own edge), not inward
        // padding — sitting at the true border instead of well inside the card,
        // per on-device feedback ("handle position is inside widget, it should be
        // at the border"), which also made the width handle hard to grab cleanly
        // since it competed with the card's own content underneath it.
        if (resizableHeight) {
            ResizeHandle(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 4.dp),
                width = 32.dp,
                height = 4.dp,
                widgetId = dragKey,
                onDrag = { _, dy ->
                    liveHeight = (liveHeight + dy / density).roundToInt().coerceIn(WIDGET_MIN_H, WIDGET_MAX_H)
                },
                onDragEnd = { onResize(liveHeight, currentHalfWidth()) },
            )
        }
        ResizeHandle(
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 4.dp),
            width = 4.dp,
            height = 32.dp,
            widgetId = dragKey,
            onDrag = { dx, _ ->
                liveWidth = (liveWidth + dx / density).roundToInt().coerceIn(widthFloor, fullWidthDp)
            },
            onDragEnd = { onResize(liveHeight, settleWidth()) },
        )
        if (resizableHeight) {
            CornerArcHandle(
                modifier = Modifier.align(Alignment.BottomEnd),
                widgetId = dragKey,
                onDrag = { dx, dy ->
                    liveWidth = (liveWidth + dx / density).roundToInt().coerceIn(widthFloor, fullWidthDp)
                    liveHeight = (liveHeight + dy / density).roundToInt().coerceIn(WIDGET_MIN_H, WIDGET_MAX_H)
                },
                onDragEnd = { onResize(liveHeight, settleWidth()) },
            )
        }
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
    // Turns editing OFF only — see WidgetEditOverlay's onDismiss, its one caller.
    onEditingChange: (Boolean) -> Unit,
    // Turns editing ON — a separate callback rather than `onEditingChange(true)`,
    // since every call site wires `onEditingChange` as an unconditional "turn
    // off" (it ignores the boolean it's given), which silently broke this
    // exact "enter" case the first time it was tried here.
    onRequestEdit: () -> Unit,
    isDragging: Boolean,
    dragOffset: Offset,
    isMergeTarget: Boolean,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onResize: (heightDp: Int, halfWidth: Boolean) -> Unit,
    onEdit: (AppWidgetProviderInfo) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = rememberWidgetInfo(manager, widget.widgetId, onRemove) ?: return

    val density = LocalDensity.current.density
    // Live height while dragging; reset to the persisted value when it changes.
    val liveHeightState = remember(widget.widgetId, widget.heightDp) { mutableStateOf(widget.heightDp) }
    // The two widths this widget can ever render at: half the row (paired, or alone
    // at half width) or the full row — [widthDp] here is always the full row width,
    // regardless of which one this instance is currently rendering at
    // (`WidgetSection` decides that via row packing). Every widget can be dragged
    // between the two; which one it's currently classified as drives both the
    // default live width and the drag's flip-over point.
    val halfWidthDp = remember(widget.widgetId, info, widthDp) { halfContentWidthDp(info, widthDp, density) }
    val fullWidthDp = widthDp
    val liveWidthState = remember(widget.widgetId, widget.halfWidth, widthDp) {
        mutableStateOf(if (widget.halfWidth) halfWidthDp else fullWidthDp)
    }
    val liveWidth by liveWidthState
    val liveHeight by liveHeightState

    // The REAL layout stays pinned at the last-committed size throughout a drag —
    // see the shared rationale on BuiltinCardView's identical committedWidthDp/
    // scaleX/scaleY block below.
    val committedWidthDp = if (widget.halfWidth) halfWidthDp else fullWidthDp
    val committedHeightDp = widget.heightDp
    val scaleX = if (committedWidthDp > 0) liveWidth.toFloat() / committedWidthDp else 1f
    val scaleY = if (committedHeightDp > 0) liveHeight.toFloat() / committedHeightDp else 1f
    val isResizing = scaleX != 1f || scaleY != 1f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(committedWidthDp.dp)
                .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
                .graphicsLayer {
                    translationX = if (isDragging) dragOffset.x else 0f
                    translationY = if (isDragging) dragOffset.y else 0f
                    if (isResizing) {
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
                .zIndex(if (isDragging || isResizing) 1f else 0f),
        ) {
            WidgetHostedView(
                host = host,
                widgetId = widget.widgetId,
                info = info,
                contentWidthDp = committedWidthDp,
                heightDp = committedHeightDp,
                onLongPress = if (editing) null else onRequestEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(committedHeightDp.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )

            if (isMergeTarget) MergeTargetHighlight(accent)

            if (editing) {
                WidgetEditOverlay(
                    accent = accent,
                    dragKey = widget.widgetId,
                    info = info,
                    halfWidthDp = halfWidthDp,
                    fullWidthDp = fullWidthDp,
                    liveWidthState = liveWidthState,
                    liveHeightState = liveHeightState,
                    currentHalfWidth = { widget.halfWidth },
                    onResize = onResize,
                    onDragStart = onDragStart,
                    onDragBy = onDragBy,
                    onDragEnd = onDragEnd,
                    onEdit = onEdit,
                    onRemove = onRemove,
                    onDismiss = { onEditingChange(false) },
                )
            }
        }
    }
}

/**
 * One of the feed's three built-in glance cards (weather/agenda/now-playing —
 * sentinel [HostedWidget.widgetId], see [BUILTIN_WEATHER_WIDGET_ID]) participating
 * in the same drag/resize/persistence machinery as a real hosted widget, minus
 * everything [AppWidgetHost]-specific: no [WidgetHostedView], no
 * [rememberWidgetInfo] (there's no provider to resolve), and — unlike [WidgetView]
 * — never a merge/stack target (a built-in card has no carousel to join; see the
 * `dragged.widgetId < 0` guard in [WidgetSection]'s merge-candidate check). Its
 * [content] is whichever of `WeatherCard`/`AgendaCard`/`NowPlayingCard`
 * (`FeedPage.kt`) the caller supplies. Mirrors [WidgetView]'s own shape closely so
 * the two read as one family.
 */
@Composable
private fun BuiltinCardView(
    id: Int,
    halfWidth: Boolean,
    /** 0 means "never manually resized" — falls back to [defaultHeightDp] rather
     *  than rendering a near-zero-height card. */
    heightDp: Int,
    defaultHeightDp: Int,
    widthDp: Int,
    accent: Color,
    editing: Boolean,
    isDragging: Boolean,
    dragOffset: Offset,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onResize: (heightDp: Int, halfWidth: Boolean) -> Unit,
    onDismiss: () -> Unit,
    // false/no-op for the three fixed builtins (weather/agenda/now-playing —
    // can't be removed, only a user-added custom card can).
    removable: Boolean = false,
    onRemove: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val halfWidthDp = widthDp / 2
    val fullWidthDp = widthDp
    val committedWidthDp = if (halfWidth) halfWidthDp else fullWidthDp
    val committedHeightDp = if (heightDp > 0) heightDp else defaultHeightDp
    val liveWidthState = remember(halfWidth, widthDp) { mutableStateOf(committedWidthDp) }
    val liveWidth by liveWidthState
    val liveHeightState = remember(heightDp, defaultHeightDp) { mutableStateOf(committedHeightDp) }
    val liveHeight by liveHeightState

    // The REAL layout (.width/.height below) stays pinned at the last-committed
    // size for the ENTIRE drag — it only jumps once on commit, when the row
    // repacks. The live drag feedback comes entirely from a graphicsLayer
    // scale instead: cheap (GPU-compositing, no relayout/reflow per frame — no
    // text re-wrapping, no repeated real-widget updateAppWidgetSize IPC calls
    // for real hosted widgets), and immune to a paired half-width card's
    // Modifier.weight(1f) capping its real measured width at 50% (a real,
    // now-fixed bug: relaying out to liveWidth directly every frame either got
    // silently clamped to the weighted share, or — after an earlier, since-
    // reverted attempt to fix that by dropping weight(1f) — pushed a
    // real-widget-content reflow on every pixel, which is what actually read
    // as "not smooth"). zIndex ensures the scaled-up card draws above its
    // neighbour instead of underneath it (composition order alone would put
    // whichever card composes later on top, not necessarily the growing one).
    val scaleX = if (committedWidthDp > 0) liveWidth.toFloat() / committedWidthDp else 1f
    val scaleY = if (committedHeightDp > 0) liveHeight.toFloat() / committedHeightDp else 1f
    val isResizing = scaleX != 1f || scaleY != 1f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(committedWidthDp.dp)
                .height(committedHeightDp.dp)
                .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
                .graphicsLayer {
                    translationX = if (isDragging) dragOffset.x else 0f
                    translationY = if (isDragging) dragOffset.y else 0f
                    if (isResizing) {
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
                .zIndex(if (isDragging || isResizing) 1f else 0f),
        ) {
            content()
            if (editing) {
                WidgetEditOverlay(
                    accent = accent,
                    dragKey = id,
                    info = null,
                    halfWidthDp = halfWidthDp,
                    fullWidthDp = fullWidthDp,
                    liveWidthState = liveWidthState,
                    liveHeightState = liveHeightState,
                    currentHalfWidth = { halfWidth },
                    removable = removable,
                    onResize = onResize,
                    onDragStart = onDragStart,
                    onDragBy = onDragBy,
                    onDragEnd = onDragEnd,
                    onEdit = {},
                    onRemove = onRemove,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/**
 * A **widget stack**: two or more hosted widgets sharing a [HostedWidget.stackId],
 * rendered as one card that carousels between them instead of each taking its own
 * row — the feed's equivalent of Start's own widget stack (`StackTileContent` in
 * `StartScreen.kt`), which this deliberately mirrors.
 *
 * The card auto-advances every [WIDGET_STACK_ROTATE_MS] (paused while editing), with
 * a random per-stack phase offset so two stacks on screen don't move in lockstep,
 * and members slide vertically in the direction of travel. **Manual navigation** is
 * a vertical drag that starts in the [WIDGET_STACK_EDGE_ZONE_DP] strip at the card's
 * right edge, where the position indicator sits: drag up for the next member, down
 * for the previous, engaging immediately at plain touch-slop.
 *
 * That edge confinement is the crucial difference from Start's version. Start's
 * stack members are virtual tiles this app draws itself, so capturing a gesture
 * anywhere on them costs nothing; here each member is a real `AppWidgetHostView`
 * that owns its taps, internal scrolling, and buttons. So a touch starting anywhere
 * outside the strip is left entirely unconsumed and reaches the hosted widget
 * exactly as it would on an un-stacked card — there's no tap-to-launch or
 * long-press-to-edit competing for it (the "edit" pill covers that instead).
 *
 * Only the visible member is composed; a hidden one keeps receiving updates anyway,
 * because [AppWidgetHost.startListening] caches every bound widget's latest
 * `RemoteViews` host-wide whether or not a view is currently inflated for it, so
 * flipping back shows current content with no extra bookkeeping.
 */
@Composable
private fun WidgetStackView(
    host: AppWidgetHost,
    manager: AppWidgetManager,
    members: List<HostedWidget>,
    widthDp: Int,
    accent: Color,
    editing: Boolean,
    customCardFlipState: FlipState,
    stockRefreshRate: LiveRefreshRate,
    commodityRefreshRate: LiveRefreshRate,
    sportsRefreshRate: LiveRefreshRate,
    onCustomCardTap: (widgetId: Int, kind: CustomCardKind) -> Unit,
    // Turns editing OFF only — see WidgetEditOverlay's onDismiss, its one caller.
    onEditingChange: (Boolean) -> Unit,
    // Turns editing ON — see WidgetView's identical param for why this can't
    // just be `onEditingChange(true)`.
    onRequestEdit: () -> Unit,
    isDragging: Boolean,
    dragOffset: Offset,
    isMergeTarget: Boolean,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onResize: (heightDp: Int, halfWidth: Boolean) -> Unit,
    onEdit: (widgetId: Int, info: AppWidgetProviderInfo) -> Unit,
    onRemove: (widgetId: Int) -> Unit,
    onUnstack: (widgetId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = members.size
    val density = LocalDensity.current.density
    // Every member shares the card's size: its height is the largest of theirs, and
    // they all carry the same half/full classification (only same-width widgets can
    // merge in the first place), so the first member speaks for the group.
    val sharedHeightDp = stackHeightDp(members)
    val groupHalfWidth = members.first().halfWidth

    val pageIndex = remember(count) { mutableIntStateOf(0) }
    val safeIndex = pageIndex.intValue.coerceIn(0, (count - 1).coerceAtLeast(0))
    // Direction of the last member change (+1 next / −1 previous) — drives the slide.
    val lastDir = remember { mutableIntStateOf(1) }
    val visibleMember = members[safeIndex]

    // Every REAL member's provider info, resolved up front and keyed so the slots
    // stay stable as the list changes. It has to be all of them, not just the
    // visible one: `halfContentWidthDp` floors the width at the provider's own
    // declared minimum, and members can declare different minimums — sizing from
    // whichever happened to be showing made the card visibly resize every time it
    // rotated. `onMissing` is a no-op here so the single removal path stays
    // [WidgetStackMemberView]'s and a vanished provider can't be reported twice.
    // A TileShell custom member has no real provider at all — resolving one for it
    // would just spend rememberWidgetInfo's 15s "missing" grace period polling a
    // widgetId AppWidgetManager was never going to know about, then delete the card
    // outright as if its provider had been uninstalled — so custom members short-
    // circuit to null directly, without ever composing rememberWidgetInfo.
    val memberInfos = members.map { m ->
        if (m.customKind.isNotEmpty()) null else key(m.widgetId) { rememberWidgetInfo(manager, m.widgetId, onMissing = {}) }
    }
    // Used for the overlay's own controls (its configure action, resize floor) —
    // null both when the provider hasn't resolved yet and, structurally the same
    // as far as this overlay's own logic goes, when the visible member is a custom
    // card (see [WidgetEditOverlay]'s existing info-less path, also used by
    // [BuiltinCardView]).
    val visibleInfo = memberInfos.getOrNull(safeIndex)

    val liveHeightState = remember(sharedHeightDp) { mutableStateOf(sharedHeightDp) }
    val halfWidthDp = memberInfos.filterNotNull()
        .maxOfOrNull { halfContentWidthDp(it, widthDp, density) }
        ?: (widthDp / 2)
    val fullWidthDp = widthDp
    val liveWidthState = remember(groupHalfWidth, widthDp, halfWidthDp) {
        mutableStateOf(if (groupHalfWidth) halfWidthDp else fullWidthDp)
    }
    val liveWidth by liveWidthState
    val liveHeight by liveHeightState

    // Auto-rotate. Runs for the composition's lifetime so the interval never resets
    // when edit mode toggles briefly; the guard is checked after each full delay
    // rather than being a LaunchedEffect key, so a short interruption doesn't
    // shorten the next interval. Same shape as Start's own stack rotation.
    val editingRef = rememberUpdatedState(editing)
    val rotateOffset = remember(count) { Random.nextLong(0L, WIDGET_STACK_ROTATE_MS) }
    LaunchedEffect(count) {
        if (count <= 1) return@LaunchedEffect
        delay(rotateOffset)
        while (true) {
            delay(WIDGET_STACK_ROTATE_MS)
            if (editingRef.value) continue
            lastDir.intValue = 1
            pageIndex.intValue = (pageIndex.intValue + 1) % count
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(liveWidth.dp)
                .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
                .graphicsLayer {
                    translationX = if (isDragging) dragOffset.x else 0f
                    translationY = if (isDragging) dragOffset.y else 0f
                }
                // In edit mode the overlay owns interaction, so the flip gesture is
                // removed entirely rather than competing with the resize handles.
                .then(
                    if (editing) {
                        Modifier
                    } else {
                        Modifier.pointerInput(count) {
                            val slop = viewConfiguration.touchSlop
                            val stepPx = 44.dp.toPx()
                            val edgeZonePx = WIDGET_STACK_EDGE_ZONE_DP.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Anything not starting in the right-edge strip is
                                // the hosted widget's to handle: bail immediately
                                // without consuming, so its own taps/scrolls work.
                                if (count <= 1 || down.position.x < size.width - edgeZonePx) {
                                    return@awaitEachGesture
                                }
                                var anchorY = down.position.y
                                var flipping = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed) break
                                    if (!flipping) {
                                        val dy = change.position.y - anchorY
                                        val dx = change.position.x - down.position.x
                                        if (abs(dy) > slop && abs(dy) > abs(dx)) {
                                            flipping = true
                                            anchorY = change.position.y
                                        }
                                    }
                                    if (flipping) {
                                        change.consume()
                                        val dy = change.position.y - anchorY
                                        if (dy <= -stepPx) {
                                            lastDir.intValue = 1
                                            pageIndex.intValue = (pageIndex.intValue + 1) % count
                                            anchorY = change.position.y
                                        } else if (dy >= stepPx) {
                                            lastDir.intValue = -1
                                            pageIndex.intValue = (pageIndex.intValue - 1 + count) % count
                                            anchorY = change.position.y
                                        }
                                    }
                                    if (!change.pressed) break
                                }
                            }
                        }
                    },
                ),
        ) {
            // Members slide vertically in the direction of travel so each reads as a
            // distinct card moving past — for both the swipe and the auto-rotate.
            AnimatedContent(
                targetState = safeIndex,
                transitionSpec = {
                    val dir = lastDir.intValue
                    (slideInVertically { h -> dir * h } + fadeIn()) togetherWith
                        (slideOutVertically { h -> -dir * h } + fadeOut())
                },
                label = "widgetStackMember",
                modifier = Modifier.height(liveHeight.dp),
            ) { index ->
                val member = members.getOrNull(index)
                if (member != null) {
                    WidgetStackMemberView(
                        host = host,
                        manager = manager,
                        widget = member,
                        contentWidthDp = liveWidth,
                        heightDp = liveHeight,
                        accent = accent,
                        editing = editing,
                        customCardFlipState = customCardFlipState,
                        stockRefreshRate = stockRefreshRate,
                        commodityRefreshRate = commodityRefreshRate,
                        sportsRefreshRate = sportsRefreshRate,
                        onCustomCardTap = onCustomCardTap,
                        onEnterEditMode = onRequestEdit,
                        onMissing = { onRemove(member.widgetId) },
                    )
                }
            }

            // Right-edge position indicator: a track with a bright thumb tracking the
            // current member — also the visible affordance for the swipe-to-flip zone.
            //
            // Two deliberate differences from Start's otherwise-identical indicator.
            // Its height is an explicit fraction of the card rather than
            // `fillMaxHeight(0.5f)`: the feed is a vertically scrolling column, so the
            // incoming max height here is unbounded and a fill fraction would resolve
            // to nothing (Start's tiles are fixed-size, so the same idiom works
            // there). And the track is dark rather than white, because a hosted
            // widget can be any colour — a white-on-white track vanished outright on
            // light widgets, so this borrows the "edit" pill's dark backing, which
            // reads on both light and dark content.
            if (count > 1 && !editing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 5.dp)
                        .width(3.dp)
                        .height((liveHeight / 2).dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.22f)),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (safeIndex > 0) Spacer(Modifier.weight(safeIndex.toFloat()))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.95f)),
                        )
                        val below = count - 1 - safeIndex
                        if (below > 0) Spacer(Modifier.weight(below.toFloat()))
                    }
                }
            }

            if (isMergeTarget) MergeTargetHighlight(accent)

            // Edit mode acts on whichever member is showing — that's the one the user
            // is looking at — while resize applies to the whole stack, since they
            // share one card. A custom member has no real provider info (visibleInfo
            // is deliberately null for it — see memberInfos above) but still gets the
            // overlay: WidgetEditOverlay already treats a null info as "no
            // edit/reconfigure pill, everything else works the same," the exact same
            // path BuiltinCardView already relies on for the three fixed builtins.
            if (editing && (visibleInfo != null || visibleMember.customKind.isNotEmpty())) {
                WidgetEditOverlay(
                    accent = accent,
                    dragKey = visibleMember.widgetId,
                    info = visibleInfo,
                    halfWidthDp = halfWidthDp,
                    fullWidthDp = fullWidthDp,
                    liveWidthState = liveWidthState,
                    liveHeightState = liveHeightState,
                    currentHalfWidth = { groupHalfWidth },
                    onResize = onResize,
                    onDragStart = onDragStart,
                    onDragBy = onDragBy,
                    onDragEnd = onDragEnd,
                    onEdit = { info -> onEdit(visibleMember.widgetId, info) },
                    onRemove = { onRemove(visibleMember.widgetId) },
                    onDismiss = { onEditingChange(false) },
                    extraActions = {
                        EditPill("unstack", accent.copy(alpha = 0.75f)) {
                            onUnstack(visibleMember.widgetId)
                        }
                    },
                )
            }
        }
    }
}

/** One member of a widget stack: resolves its own provider info, then hosts it. */
@Composable
private fun WidgetStackMemberView(
    host: AppWidgetHost,
    manager: AppWidgetManager,
    widget: HostedWidget,
    contentWidthDp: Int,
    heightDp: Int,
    accent: Color,
    editing: Boolean,
    customCardFlipState: FlipState,
    stockRefreshRate: LiveRefreshRate,
    commodityRefreshRate: LiveRefreshRate,
    sportsRefreshRate: LiveRefreshRate,
    onCustomCardTap: (widgetId: Int, kind: CustomCardKind) -> Unit,
    onEnterEditMode: () -> Unit,
    onMissing: () -> Unit,
) {
    if (widget.customKind.isNotEmpty()) {
        val kind = CustomCardKind.entries.find { it.iconKey == widget.customKind } ?: return
        // Same accent-filled background + tap-to-configure convention as a lone
        // custom card (see WidgetSection's own customCardView) — a card doesn't
        // change how it looks or behaves just because it's currently stacked.
        // Same long-press-enters-edit-mode / FLASHLIGHT exception too.
        AccentCard(
            accent = accent,
            onClick = if (editing || !kind.needsConfig) null else ({ onCustomCardTap(widget.widgetId, kind) }),
            onLongClick = if (editing || kind == CustomCardKind.FLASHLIGHT) null else onEnterEditMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            CustomCardBody(
                kind = kind,
                hw = widget,
                accent = accent,
                editMode = editing,
                customCardFlipState = customCardFlipState,
                stockRefreshRate = stockRefreshRate,
                commodityRefreshRate = commodityRefreshRate,
                sportsRefreshRate = sportsRefreshRate,
            )
        }
        return
    }
    val info = rememberWidgetInfo(manager, widget.widgetId, onMissing) ?: return
    WidgetHostedView(
        host = host,
        widgetId = widget.widgetId,
        info = info,
        contentWidthDp = contentWidthDp,
        heightDp = heightDp,
        onLongPress = if (editing) null else onEnterEditMode,
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(20.dp)),
    )
}

/** One app's widgets in the picker — [appLabel] drives both sort order and the group header. */
/** One row the picker can show under an app group — a real bound-able widget, or one of TileShell's own cards (rendered identically, in the exact same list — see [WidgetPicker]'s doc comment). */
private sealed class WidgetPickerEntry {
    data class Real(val provider: AppWidgetProviderInfo) : WidgetPickerEntry()
    data class Custom(val kind: CustomCardKind) : WidgetPickerEntry()
}

private data class WidgetAppGroup(
    val packageName: String,
    val appLabel: String,
    val entries: List<WidgetPickerEntry>,
)

/**
 * One TileShell custom card's actual live-tile face, dispatched by [kind] — the
 * content that goes inside an [AccentCard]. Shared by a lone custom card
 * ([WidgetSection]'s own `customCardView`) and a custom card stacked with
 * another one ([WidgetStackMemberView]'s `customKind` branch), so a card renders
 * identically whichever way it's shown.
 */
@Composable
private fun CustomCardBody(
    kind: CustomCardKind,
    hw: HostedWidget,
    accent: Color,
    editMode: Boolean,
    customCardFlipState: FlipState,
    stockRefreshRate: LiveRefreshRate,
    commodityRefreshRate: LiveRefreshRate,
    sportsRefreshRate: LiveRefreshRate,
) {
    // Every live-tile face reads LocalTileFaceColor for its own text/icon
    // colour. On Start that's provided once, globally, deliberately independent
    // of any one tile's accent (WP tiles are always white-on-accent). The
    // glance page's own cards (Weather/Agenda/NowPlaying) don't follow that
    // rule — each picks black-or-white per its OWN accent's brightness via
    // Glass.faceTextColor(isLightBackground(accent)), since glance mixes much
    // more varied (including light, wallpaper-derived) accents than Start's
    // fixed global one. A custom card had no local override at all here, so it
    // silently fell through to the ambient default (plain white) regardless of
    // its own accent — invisible-ish on a light accent while its Weather/Agenda
    // neighbour correctly switched to dark text for the very same colour
    // (user-reported). Matching glance's own norm here, not Start's.
    CompositionLocalProvider(LocalTileFaceColor provides Glass.faceTextColor(useDarkText = isLightBackground(accent))) {
    when (kind) {
        CustomCardKind.STOCK -> StockTileFace(
            size = TileSize.WIDE,
            flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
            active = !editMode,
            selection = StockTile.decode(hw.customConfig),
            refreshRate = stockRefreshRate,
            // The glance card never reaches TileSize.LARGE (there's no bigger
            // size to grow into here), but it has plenty of room for a short
            // list — show every picked stock/category member, not just the
            // lead one StockTileFace would otherwise stop at below LARGE.
            showAllMembers = true,
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.COMMODITY -> {
            val decoded = CommodityTile.decode(hw.customConfig)
            CommodityTileFace(
                size = TileSize.WIDE,
                flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
                active = !editMode,
                symbol = decoded?.first,
                displayName = decoded?.second,
                refreshRate = commodityRefreshRate,
                modifier = Modifier.fillMaxSize(),
            )
        }
        CustomCardKind.SPORTS -> {
            val selection = SportsTile.decode(hw.customConfig)
            SportsTileFace(
                size = TileSize.WIDE,
                flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
                active = !editMode,
                tileId = "glance-${hw.widgetId}",
                leagueSlug = selection?.leagueSlug.orEmpty(),
                teamId = selection?.teamId.orEmpty(),
                teamLabel = selection?.teamLabel.orEmpty(),
                refreshRate = sportsRefreshRate,
                modifier = Modifier.fillMaxSize(),
            )
        }
        CustomCardKind.CALENDAR_SYSTEM -> CalendarSystemTileFace(
            size = TileSize.WIDE,
            flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
            active = !editMode,
            systemId = CalendarSystemTile.decode(hw.customConfig),
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.COUNTDOWN -> {
            val (isoDate, label) = CountdownTile.decode(hw.customConfig) ?: ("" to "")
            CountdownTileFace(
                size = TileSize.WIDE,
                flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
                targetIsoDate = isoDate,
                label = label,
                modifier = Modifier.fillMaxSize(),
            )
        }
        CustomCardKind.STICKYNOTE -> StickyNoteTileFace(
            size = TileSize.WIDE,
            text = hw.customConfig,
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.CLOCK -> ClockTileFace(
            size = TileSize.WIDE,
            flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
            active = !editMode,
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.BATTERY -> BatteryTileFace(
            size = TileSize.WIDE,
            flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.ALARM -> AlarmTileFace(
            size = TileSize.WIDE,
            flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
            active = !editMode,
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.MOONPHASE -> MoonPhaseTileFace(
            size = TileSize.WIDE,
            flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
            active = !editMode,
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.TASKS -> TasksTileFace(
            size = TileSize.WIDE,
            interactive = !editMode,
            listId = hw.widgetId.toString(),
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.NOTEPAD -> NotesTileFace(
            size = TileSize.WIDE,
            flipped = customCardFlipState.isFlipped(hw.widgetId.toString()),
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.FLASHLIGHT -> FlashlightTileFace(
            size = TileSize.WIDE,
            interactive = !editMode,
            modifier = Modifier.fillMaxSize(),
        )
        CustomCardKind.STEPS -> StepsTileFace(
            size = TileSize.WIDE,
            fallback = { NoCustomCardDataFace(kind.label) },
            modifier = Modifier.fillMaxSize(),
        )
    }
    }
}

/** Plain-text fallback for a custom card whose data isn't ready yet (e.g. [StepsTileFace] before the sensor reports anything) — text only, no static glyph exists at this size outside Start's own tile grid. */
@Composable
private fun NoCustomCardDataFace(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = label, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
    }
}

/**
 * Full-screen widget picker dialog: installed providers with preview + label,
 * grouped by owning app (a phone can easily have 20+ widgets across a handful of
 * apps — a flat list made it hard to find, say, "the calendar app's" widgets
 * among everything else). Groups and their contents are both sorted by label so
 * the picker reads the same way every time it's opened.
 *
 * TileShell's own cards (stock/commodity/sports/...) are folded into this
 * exact same grouped list, under a synthetic "TileShell" group using this
 * app's own icon/label — not a separate chooser step — so from the user's
 * side, adding a TileShell card and adding a real widget from any other app
 * are the same action in the same list, the app just happens to be this one.
 */
@Composable
private fun WidgetPicker(
    manager: AppWidgetManager,
    tokens: ColorTokens,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onPickCustom: (CustomCardKind) -> Unit,
    onDismiss: () -> Unit,
    // Notes has one shared notepad behind every pinned card on glance too — a
    // second one would just show the same content twice — so it greys out
    // once one exists here, matching Start's own WidgetListSheet dedup rule
    // (user-reported: glance let it be added more than once).
    notesAlreadyPinned: Boolean = false,
) {
    val context = LocalContext.current
    val groups = remember {
        runCatching {
            val pm = context.packageManager
            val realGroups = manager.installedProviders
                .groupBy { it.provider.packageName }
                .map { (packageName, providers) ->
                    val appLabel = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                    }.getOrDefault(packageName)
                    WidgetAppGroup(
                        packageName = packageName,
                        appLabel = appLabel,
                        entries = providers.sortedBy { it.loadLabel(pm).lowercase() }.map { WidgetPickerEntry.Real(it) },
                    )
                }
            val ownPackage = context.packageName
            val ownLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(ownPackage, 0)).toString()
            }.getOrDefault("TileShell")
            val customGroup = WidgetAppGroup(
                packageName = ownPackage,
                appLabel = ownLabel,
                entries = CustomCardKind.entries.sortedBy { it.label }.map { WidgetPickerEntry.Custom(it) },
            )
            // TileShell's own gadgets are pinned first rather than sorted in with
            // everyone else alphabetically — they're the app's own facility, not
            // just another provider that happens to start with a low letter.
            listOf(customGroup) + realGroups.sortedBy { it.appLabel.lowercase() }
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
                                count = group.entries.size,
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
                                group.entries,
                                key = { entry ->
                                    when (entry) {
                                        is WidgetPickerEntry.Real -> "${group.packageName}/${entry.provider.provider.className}"
                                        is WidgetPickerEntry.Custom -> "${group.packageName}/custom/${entry.kind.name}"
                                    }
                                },
                            ) { entry ->
                                when (entry) {
                                    is WidgetPickerEntry.Real -> WidgetPickerRow(entry.provider, tokens) { onPick(entry.provider) }
                                    is WidgetPickerEntry.Custom -> {
                                        val disabled = entry.kind == CustomCardKind.NOTEPAD && notesAlreadyPinned
                                        CustomCardPickerRow(entry.kind, tokens, enabled = !disabled) { onPickCustom(entry.kind) }
                                    }
                                }
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

/** A TileShell card's own row in the merged picker — same shape as [WidgetPickerRow], a monoline glyph plate standing in for a loaded preview image (there's no real provider to load one from). [enabled] false greys the row out and blocks the tap — used for Notes once one is already pinned (one shared notepad, same dedup rule as Start's own catalog). */
@Composable
private fun CustomCardPickerRow(kind: CustomCardKind, tokens: ColorTokens, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.sheet)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                // Matches Start's own "add widget" catalog (WidgetListSheet) — a
                // colour-coded accent plate per kind with a white glyph, not a
                // flat monochrome tile-foreground plate.
                .background(TileAccents.forId(kind.colorId).copy(alpha = alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TileIcons[kind.iconKey], null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Column(modifier = Modifier.padding(start = 12.dp).graphicsLayer { this.alpha = alpha }) {
            Text(kind.label, color = tokens.fg, fontSize = 15.sp)
            if (!enabled) {
                Text("already added to glance", color = tokens.fgDim, fontSize = 12.sp)
            }
        }
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

/**
 * Press-and-drag handle to reorder a widget — a small grip-dot pattern (3
 * columns × 2 rows), deliberately a different SHAPE from the resize handles'
 * single straight bar/arc rather than just a different colour, after on-device
 * feedback that a same-shaped thin bar read as "just another resize handle."
 * The pointerInput/hit area stays a comfortable 40x24dp even though the visible
 * grip itself is much smaller, so the smaller visual doesn't shrink the real
 * touch target.
 */
@Composable
private fun BoxScope.DragHandlePill(
    accent: Color,
    widgetId: Int,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    // A real bug fixed on user report (Quick Panel: "resize small->long once,
    // then long->small on the same tile doesn't happen — and vice versa";
    // this handle shares the identical shape of problem). pointerInput(widgetId)
    // only restarts its gesture coroutine when widgetId itself CHANGES — for
    // repeated gestures on the SAME card across one edit session, widgetId is
    // constant, so the coroutine never restarts and detectDragGestures's own
    // callback closures permanently capture whichever onDragStart/onDragBy/
    // onDragEnd references existed at the FIRST drag. Every later drag on this
    // same card still invokes those frozen first-drag closures — which
    // themselves closed over whatever WidgetSection state existed back then —
    // instead of the fresh ones passed on later recompositions.
    // rememberUpdatedState is the standard fix: the coroutine still never
    // restarts, but it now reads through to the CURRENT callback every time.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragBy by rememberUpdatedState(onDragBy)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-5).dp)
            .size(width = 22.dp, height = 12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(accent)
            .pointerInput(widgetId) {
                detectDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, drag -> change.consume(); currentOnDragBy(drag) },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        GripDots(modifier = Modifier.size(width = 16.dp, height = 8.dp))
    }
}

/** 3×2 grid of small dots — the move-handle glyph shared by [DragHandlePill] and
 *  Quick Panel's own move handle (see `QuickPanelOverlay.kt`'s identical helper). */
@Composable
private fun GripDots(modifier: Modifier = Modifier) {
    ComposeCanvas(modifier = modifier) {
        val dotRadius = 1.3.dp.toPx()
        val cols = 3
        val rows = 2
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = size.width * (c + 0.5f) / cols
                val y = size.height * (r + 0.5f) / rows
                drawCircle(color = Color.White.copy(alpha = 0.9f), radius = dotRadius, center = Offset(x, y))
            }
        }
    }
}

/**
 * A thin draggable bar used to resize a widget in one direction (width or
 * height) — approved One-UI-inspired design. [width]/[height] size the visible
 * bar; the pointerInput hit area is padded out to at least 40dp on the bar's
 * long axis so a thin bar stays comfortably draggable.
 */
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
    // Additive, not maxOf(dimension, 40dp) — a flat 40dp minimum on the bar's
    // thin axis made a 4dp-thick handle's real hit area balloon to 40dp wide,
    // which (combined with sitting right at the card's border — see the
    // outward offset at the call site) reached far enough into a paired
    // half-width neighbour's own bounds to compete with its content/handles
    // for the touch. A modest, additive enlargement keeps the touch target
    // comfortably bigger than the visible bar without that overlap.
    val hitWidth = width + 16.dp
    val hitHeight = height + 16.dp
    // Same stale-closure fix as DragHandlePill — see its comment.
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = modifier.size(width = hitWidth, height = hitHeight)
            .pointerInput(widgetId) {
                detectDragGestures(
                    onDrag = { change, drag -> change.consume(); currentOnDrag(drag.x, drag.y) },
                    onDragEnd = { currentOnDragEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = height)
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color.White.copy(alpha = 0.9f)),
        )
    }
}

/**
 * Bottom-right corner resize handle drawn as a quarter-circle arc (approved
 * One-UI-inspired design) instead of a dot/rounded-square — sweeps from the
 * right edge down to the bottom edge, reading as "pull the corner outward."
 * Drags both width and height at once. Same enlarged-hit-area treatment as
 * [ResizeHandle].
 */
@Composable
private fun BoxScope.CornerArcHandle(
    modifier: Modifier,
    widgetId: Int,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    // Same stale-closure fix as DragHandlePill — see its comment.
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = modifier.size(44.dp)
            .pointerInput(widgetId) {
                detectDragGestures(
                    onDrag = { change, drag -> change.consume(); currentOnDrag(drag.x, drag.y) },
                    onDragEnd = { currentOnDragEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        ComposeCanvas(modifier = Modifier.size(24.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = Color.White.copy(alpha = 0.9f),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
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
