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
)

/**
 * The app widgets hosted in the feed's glance tab, in display order. Each carries a
 * user-adjustable height. Empty = none added. Kept in its own DataStore (mirroring
 * the other feed stores) so widgets survive relaunches.
 */
data class WidgetData(val widgets: List<HostedWidget> = emptyList())

/**
 * One widget per line as `id,heightDp,widthDp,halfWidth,stackId`; tolerant (bad
 * lines dropped, missing height/width/halfWidth/stackId all fall back to
 * defaults) — the trailing `halfWidth` and `stackId` fields are optional so lines
 * written before either existed still decode fine. An un-stacked widget writes
 * `stackId` as an empty field rather than a sentinel like `-1`, so it decodes
 * identically to an older file that never carried the column at all.
 */
object WidgetCodec {
    fun encode(data: WidgetData): String =
        data.widgets.joinToString("\n") {
            "${it.widgetId},${it.heightDp},${it.widthDp},${it.halfWidth},${it.stackId ?: ""}"
        }

    fun decode(text: String): WidgetData = WidgetData(
        text.lineSequence().mapNotNull { line ->
            val parts = line.split(",")
            val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val h = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 110
            val w = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
            val half = parts.getOrNull(3)?.trim()?.toBooleanStrictOrNull() ?: false
            val stackId = parts.getOrNull(4)?.trim()?.toIntOrNull()
            HostedWidget(id, h, w, half, stackId)
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

    companion object {
        fun create(context: Context): WidgetStore =
            WidgetStore(context.applicationContext.widgetDataStore)
    }
}
