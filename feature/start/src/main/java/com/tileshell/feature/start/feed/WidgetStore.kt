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
 * an optional custom width (dp) — only square widgets are user-resizable in width
 * (diagonal resize, keeping height == width); everything else derives its width
 * live from the feed's slot width, so 0 means "no custom width, use the default."
 */
data class HostedWidget(val widgetId: Int, val heightDp: Int, val widthDp: Int = 0)

/**
 * The app widgets hosted in the feed's glance tab, in display order. Each carries a
 * user-adjustable height. Empty = none added. Kept in its own DataStore (mirroring
 * the other feed stores) so widgets survive relaunches.
 */
data class WidgetData(val widgets: List<HostedWidget> = emptyList())

/** One widget per line as `id,heightDp,widthDp`; tolerant (bad lines dropped, missing width → 0). */
object WidgetCodec {
    fun encode(data: WidgetData): String =
        data.widgets.joinToString("\n") { "${it.widgetId},${it.heightDp},${it.widthDp}" }

    fun decode(text: String): WidgetData = WidgetData(
        text.lineSequence().mapNotNull { line ->
            val parts = line.split(",")
            val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val h = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 110
            val w = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
            HostedWidget(id, h, w)
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

    suspend fun setSize(widgetId: Int, heightDp: Int, widthDp: Int) {
        store.updateData {
            it.copy(
                widgets = it.widgets.map { w ->
                    if (w.widgetId == widgetId) w.copy(heightDp = heightDp, widthDp = widthDp) else w
                },
            )
        }
    }

    /** Swap [widgetId] with its neighbour, moving it up (or down) one position. */
    suspend fun move(widgetId: Int, up: Boolean) {
        store.updateData { data ->
            val list = data.widgets.toMutableList()
            val i = list.indexOfFirst { it.widgetId == widgetId }
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j !in list.indices) return@updateData data
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
            data.copy(widgets = list)
        }
    }

    companion object {
        fun create(context: Context): WidgetStore =
            WidgetStore(context.applicationContext.widgetDataStore)
    }
}
