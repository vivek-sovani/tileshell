package com.tileshell.feature.start.feed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

/** One hosted app widget: its bound `AppWidgetHost` id and its current height (dp). */
data class HostedWidget(val widgetId: Int, val heightDp: Int)

/**
 * The app widgets hosted in the feed's glance tab, in display order. Each carries a
 * user-adjustable height. Empty = none added. Kept in its own DataStore (mirroring
 * the other feed stores) so widgets survive relaunches.
 */
data class WidgetData(val widgets: List<HostedWidget> = emptyList())

/** One widget per line as `id,heightDp`; tolerant (bad lines dropped). */
object WidgetCodec {
    fun encode(data: WidgetData): String =
        data.widgets.joinToString("\n") { "${it.widgetId},${it.heightDp}" }

    fun decode(text: String): WidgetData = WidgetData(
        text.lineSequence().mapNotNull { line ->
            val parts = line.split(",")
            val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val h = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 110
            HostedWidget(id, h)
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

    suspend fun setHeight(widgetId: Int, heightDp: Int) {
        store.updateData {
            it.copy(widgets = it.widgets.map { w -> if (w.widgetId == widgetId) w.copy(heightDp = heightDp) else w })
        }
    }

    companion object {
        fun create(context: Context): WidgetStore =
            WidgetStore(context.applicationContext.widgetDataStore)
    }
}
