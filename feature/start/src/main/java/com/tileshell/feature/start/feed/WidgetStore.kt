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
 */
data class HostedWidget(
    val widgetId: Int,
    val heightDp: Int,
    val widthDp: Int = 0,
    val halfWidth: Boolean = false,
)

/**
 * The app widgets hosted in the feed's glance tab, in display order. Each carries a
 * user-adjustable height. Empty = none added. Kept in its own DataStore (mirroring
 * the other feed stores) so widgets survive relaunches.
 */
data class WidgetData(val widgets: List<HostedWidget> = emptyList())

/**
 * One widget per line as `id,heightDp,widthDp,halfWidth`; tolerant (bad lines
 * dropped, missing height/width/halfWidth all fall back to defaults) — the
 * trailing `halfWidth` field is optional so lines written before it existed
 * still decode fine.
 */
object WidgetCodec {
    fun encode(data: WidgetData): String =
        data.widgets.joinToString("\n") { "${it.widgetId},${it.heightDp},${it.widthDp},${it.halfWidth}" }

    fun decode(text: String): WidgetData = WidgetData(
        text.lineSequence().mapNotNull { line ->
            val parts = line.split(",")
            val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val h = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 110
            val w = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
            val half = parts.getOrNull(3)?.trim()?.toBooleanStrictOrNull() ?: false
            HostedWidget(id, h, w, half)
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

    suspend fun remove(widgetId: Int) {
        store.updateData { it.copy(widgets = it.widgets.filterNot { w -> w.widgetId == widgetId }) }
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
