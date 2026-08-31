package com.tileshell.feature.start.feed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

/**
 * One hosted app widget: its bound `AppWidgetHost` id, its current height (dp), and
 * whether it renders at half the feed's row width (paired alongside another
 * half-width widget, or alone at half width) versus the full row. [widthDp] is a
 * legacy field from an earlier continuous-width model — kept only so old save
 * files still parse; no longer read for sizing (see [halfWidth]).
 *
 * [stackId] groups this widget with others into a single swipeable carousel slot —
 * a **widget stack** — instead of each taking its own row; null means un-stacked.
 * Members of one stack all carry the same [stackId] (by convention the founding
 * widget's own [widgetId] — see `mergeIntoStack`) and always sit contiguously in
 * [WidgetData.widgets]; every mutation that touches grouping preserves that, since
 * the row packer finds a group by scanning outward rather than re-grouping the
 * whole list.
 */
data class HostedWidget(
    val widgetId: Int,
    val heightDp: Int,
    val widthDp: Int = 0,
    val halfWidth: Boolean = false,
    val stackId: Int? = null,
    // A user-added TileShell "card" (stock/commodity/sports/calsys — see
    // CUSTOM_CARD_KINDS) rather than a real AppWidgetHost binding or one of
    // the three fixed builtins: blank for both of those. [customConfig] is
    // that card's own encoded selection, in the exact same format its Start
    // tile counterpart uses (StockTile.encode*, CommodityTile.encode, ...) —
    // blank until the user has actually picked something.
    val customKind: String = "",
    val customConfig: String = "",
)

/**
 * Card kinds addable via the glance page's own "add widget" facility — each
 * mirrors a Start live tile of the same [CustomCardKind.iconKey], the same
 * set `WidgetListSheet`'s own catalog offers on Start (minus weather and
 * calendar/agenda, which the glance page already always shows as its two
 * permanent built-in cards — see [BUILTIN_WEATHER_WIDGET_ID]/
 * [BUILTIN_AGENDA_WIDGET_ID] — so offering them again here would just be a
 * confusing duplicate of what's already on screen).
 *
 * [needsConfig] is true whenever tapping the card (outside its own inline
 * interactive bits, e.g. a task's checkbox) should open something — a
 * per-card picker for a kind with an actual selection to make (a stock
 * symbol, a sticky note's own text, ...), or the shared management sheet for
 * a kind whose live-tile face is preview-only and can't add a new item by
 * itself (Tasks/Notes only ever toggle or show the latest entry inline —
 * adding a new one needs the real TaskListSheet/NotesSheet, exactly like
 * tapping the Tasks/Notes tile on Start opens the same two sheets). False
 * only for a kind that's genuinely just a live display with nothing to open
 * (clock, battery, ...) — those render immediately once added and stay that
 * way, exactly like pinning them on Start does.
 *
 * [colorId] is a [com.tileshell.core.design.TileAccents] id, matching the same
 * kind's entry in `WidgetListSheet`'s `WIDGET_CATALOG` (Start's own "add
 * widget" catalog) exactly — the glance picker's colourful icon plate
 * (`CustomCardPickerRow`, `WidgetSlot.kt`) previously fell back to a flat
 * monochrome tile-foreground plate for every kind, which read as a
 * regression next to Start's colour-coded catalog (user-reported).
 */
enum class CustomCardKind(val iconKey: String, val label: String, val needsConfig: Boolean, val colorId: String) {
    STOCK("stock", "stock market", needsConfig = true, colorId = "teal"),
    COMMODITY("commodity", "commodities", needsConfig = true, colorId = "mauve"),
    SPORTS("sports", "sports", needsConfig = true, colorId = "red"),
    CALENDAR_SYSTEM("calsys", "calendar systems", needsConfig = true, colorId = "cobalt"),
    COUNTDOWN("countdown", "countdown", needsConfig = true, colorId = "magenta"),
    STICKYNOTE("stickynote", "sticky note", needsConfig = true, colorId = "amber"),
    TASKS("tasks", "tasks", needsConfig = true, colorId = "blue"),
    NOTEPAD("notepad", "notes", needsConfig = true, colorId = "amber"),
    CLOCK("clock", "clock", needsConfig = false, colorId = "cobalt"),
    BATTERY("battery", "battery", needsConfig = false, colorId = "green"),
    ALARM("alarm", "alarm", needsConfig = false, colorId = "purple"),
    MOONPHASE("moonphase", "moon phase", needsConfig = false, colorId = "slate"),
    FLASHLIGHT("flashlight", "flashlight", needsConfig = false, colorId = "steel"),
    STEPS("steps", "steps", needsConfig = false, colorId = "lime"),
}

/**
 * The next free custom-card [HostedWidget.widgetId] — strictly below the
 * three fixed builtin sentinels (-1..-3) so it can never collide with them,
 * and below every custom card already added. Pure so id allocation is
 * unit-testable without a real DataStore.
 */
internal fun nextCustomWidgetId(current: List<HostedWidget>): Int =
    (current.filter { it.widgetId < -3 }.minOfOrNull { it.widgetId } ?: -3) - 1

/**
 * Sentinel [HostedWidget.widgetId]s for the feed's built-in glance cards (weather,
 * agenda, now-playing), which have no real `AppWidgetHost`-bound id — see
 * `WidgetSlot.kt`'s `BuiltinCardView`. Negative so they can never collide with a
 * real `AppWidgetHost.allocateAppWidgetId()` result (always ≥ 1); reusing the
 * existing `HostedWidget`/`WidgetCodec`/packing/reorder machinery for them (rather
 * than a parallel model) works because none of that logic actually requires
 * `widgetId` to resolve to a real widget, only to be a stable unique `Int`.
 */
const val BUILTIN_WEATHER_WIDGET_ID = -1
const val BUILTIN_AGENDA_WIDGET_ID = -2
const val BUILTIN_NOWPLAYING_WIDGET_ID = -3
private val BUILTIN_WIDGET_IDS = listOf(BUILTIN_WEATHER_WIDGET_ID, BUILTIN_AGENDA_WIDGET_ID, BUILTIN_NOWPLAYING_WIDGET_ID)

/**
 * Pure core of [WidgetStore.seedBuiltinsIfAbsent] — inserts any of the three
 * built-in sentinel ids missing from [current], at the front, ahead of whatever's
 * already there; a no-op (returns [current] unchanged) once all three exist.
 * Weather and agenda default to half-width, now-playing to full width; `heightDp`
 * is irrelevant for a built-in card (see [BUILTIN_WEATHER_WIDGET_ID]'s doc) so
 * it's seeded as 0.
 */
internal fun seedMissingBuiltinWidgets(current: List<HostedWidget>): List<HostedWidget> {
    val missing = BUILTIN_WIDGET_IDS.filterNot { id -> current.any { it.widgetId == id } }
    if (missing.isEmpty()) return current
    val seeded = missing.map { id -> HostedWidget(id, heightDp = 0, halfWidth = id != BUILTIN_NOWPLAYING_WIDGET_ID) }
    return seeded + current
}

/**
 * The app widgets hosted in the feed's glance tab, in display order. Each carries a
 * user-adjustable height. Empty = none added. Kept in its own DataStore (mirroring
 * the other feed stores) so widgets survive relaunches.
 */
data class WidgetData(val widgets: List<HostedWidget> = emptyList())

/**
 * One widget per line as
 * `id,heightDp,widthDp,halfWidth,stackId,customKind,customConfig`; tolerant
 * (bad lines dropped, missing fields all fall back to defaults) — the
 * trailing `halfWidth`/`stackId`/`customKind`/`customConfig` fields are all
 * optional so lines written before any of them existed still decode fine.
 * An un-stacked widget writes `stackId` as an empty field rather than a
 * sentinel like `-1`, so it decodes identically to an older file that never
 * carried the column at all. [HostedWidget.customConfig] is read as
 * everything from the 7th field onward rejoined with `,` — defensive only,
 * since every real encoded selection uses `:`/`|` as its own delimiters, not
 * a comma, but this keeps a hypothetical embedded comma from truncating it.
 */
object WidgetCodec {
    fun encode(data: WidgetData): String =
        data.widgets.joinToString("\n") {
            "${it.widgetId},${it.heightDp},${it.widthDp},${it.halfWidth},${it.stackId ?: ""},${it.customKind},${it.customConfig}"
        }

    fun decode(text: String): WidgetData = WidgetData(
        text.lineSequence().mapNotNull { line ->
            val parts = line.split(",")
            val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val h = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 110
            val w = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
            val half = parts.getOrNull(3)?.trim()?.toBooleanStrictOrNull() ?: false
            val stackId = parts.getOrNull(4)?.trim()?.toIntOrNull()
            val customKind = parts.getOrNull(5)?.trim().orEmpty()
            val customConfig = if (parts.size > 6) parts.drop(6).joinToString(",").trim() else ""
            HostedWidget(id, h, w, half, stackId, customKind, customConfig)
        }.toList(),
    )
}

private object WidgetSerializer : Serializer<WidgetData> {
    override val defaultValue = WidgetData()

    override suspend fun readFrom(input: InputStream): WidgetData =
        WidgetCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: WidgetData, output: OutputStream) {
        output.write(WidgetCodec.encode(t).encodeToByteArray())
    }
}

private val Context.widgetDataStore: DataStore<WidgetData> by dataStore(
    fileName = "feed_widget.pb",
    serializer = WidgetSerializer,
)

/** Reads/writes the hosted feed widgets. Backed by its own DataStore file. */
class WidgetStore(private val store: DataStore<WidgetData>) {

    val data: Flow<WidgetData> = store.data

    suspend fun read(): WidgetData = store.data.first()

    suspend fun add(widget: HostedWidget) {
        store.updateData { it.copy(widgets = it.widgets.filterNot { w -> w.widgetId == widget.widgetId } + widget) }
    }

    /**
     * Deletes a widget. If it was in a widget stack and only one member would be
     * left, that survivor is un-stacked too — a stack of one doesn't exist, and a
     * dangling [HostedWidget.stackId] would otherwise keep it rendering as a
     * one-member carousel. Also covers the automatic removal path (a provider that
     * no longer resolves), not just the user's "remove" action.
     */
    suspend fun remove(widgetId: Int) {
        store.updateData { data ->
            val stackId = data.widgets.firstOrNull { it.widgetId == widgetId }?.stackId
            val remaining = data.widgets.filterNot { it.widgetId == widgetId }
            val dissolved = if (stackId != null && remaining.count { it.stackId == stackId } == 1) {
                remaining.map { if (it.stackId == stackId) it.copy(stackId = null) else it }
            } else {
                remaining
            }
            data.copy(widgets = dissolved)
        }
    }

    suspend fun setSize(widgetId: Int, heightDp: Int, halfWidth: Boolean) {
        store.updateData {
            it.copy(
                widgets = it.widgets.map { w ->
                    if (w.widgetId == widgetId) w.copy(heightDp = heightDp, halfWidth = halfWidth) else w
                },
            )
        }
    }

    /**
     * Resizes every member of one widget stack together, in a single atomic update.
     * A stack renders as one card, so its members have to agree on that card's size
     * — whichever member's resize handle was dragged, they all converge on the
     * result (see `WidgetStackView`).
     */
    suspend fun setStackSize(stackId: Int, heightDp: Int, halfWidth: Boolean) {
        store.updateData {
            it.copy(
                widgets = it.widgets.map { w ->
                    if (w.stackId == stackId) w.copy(heightDp = heightDp, halfWidth = halfWidth) else w
                },
            )
        }
    }

    /** Persists a new display order (the same widgets, resequenced) — e.g. after a drag reorder. */
    suspend fun reorder(newOrder: List<HostedWidget>) {
        store.updateData { it.copy(widgets = newOrder) }
    }

    /**
     * Wholesale replace, for backup restore — see `BackupManager`/`StartViewModel
     * .importBackup`. Callers should first drop any [HostedWidget] whose id no
     * longer resolves via `AppWidgetManager` (a foreign/stale id from a
     * cross-device restore or a reinstall) — widget ids are bound to this
     * specific `AppWidgetHost` instance and aren't portable like the rest of a
     * backup.
     */
    suspend fun replaceAll(data: WidgetData) {
        store.updateData { data }
    }

    /** One-time migration — see [seedMissingBuiltinWidgets] for the actual logic. */
    suspend fun seedBuiltinsIfAbsent() {
        store.updateData { data -> data.copy(widgets = seedMissingBuiltinWidgets(data.widgets)) }
    }

    /**
     * Adds a new, not-yet-configured custom card of [kind] to the end of the
     * list (the same "appended, not seeded" placement a newly bound real
     * widget gets) — tap it to open its own picker sheet, same as pinning
     * one of these on Start. Returns the id it was allocated.
     */
    suspend fun addCustomCard(kind: CustomCardKind): Int {
        var newId = 0
        store.updateData { data ->
            newId = nextCustomWidgetId(data.widgets)
            // Calendar systems defaults to full width — its Hindu Panchang face
            // now carries a big moon-phase visual beside a five-line text
            // column (user-requested), which needs real room; every other kind
            // still starts at half width, same as a real hosted widget's own
            // default (still user-resizable afterward either way).
            val halfWidth = kind != CustomCardKind.CALENDAR_SYSTEM
            data.copy(widgets = data.widgets + HostedWidget(newId, heightDp = 0, halfWidth = halfWidth, customKind = kind.iconKey))
        }
        return newId
    }

    /** Overwrites a custom card's own encoded selection once its picker sheet has actually picked something. */
    suspend fun setCustomConfig(widgetId: Int, config: String) {
        store.updateData { data ->
            data.copy(widgets = data.widgets.map { if (it.widgetId == widgetId) it.copy(customConfig = config) else it })
        }
    }

    companion object {
        fun create(context: Context): WidgetStore =
            WidgetStore(context.applicationContext.widgetDataStore)
    }
}
