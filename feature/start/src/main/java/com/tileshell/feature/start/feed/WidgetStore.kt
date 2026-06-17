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
 * The app-widget hosted in the feed's glance tab: a single bound `AppWidgetHost`
 * widget id (-1 = none chosen). Kept in its own DataStore (mirroring the other
 * feed stores) so the widget survives relaunches.
 */
data class WidgetData(val widgetId: Int = -1)

private object WidgetSerializer : Serializer<WidgetData> {
    override val defaultValue = WidgetData()

    override suspend fun readFrom(input: InputStream): WidgetData =
        WidgetData(input.readBytes().decodeToString().trim().toIntOrNull() ?: -1)

    override suspend fun writeTo(t: WidgetData, output: OutputStream) {
        output.write(t.widgetId.toString().encodeToByteArray())
    }
}

private val Context.widgetDataStore: DataStore<WidgetData> by dataStore(
    fileName = "feed_widget.pb",
    serializer = WidgetSerializer,
)

/** Reads/writes the feed widget id. Backed by its own DataStore file. */
class WidgetStore(private val store: DataStore<WidgetData>) {

    val data: Flow<WidgetData> = store.data

    suspend fun read(): WidgetData = store.data.first()

    suspend fun setWidgetId(id: Int) {
        store.updateData { WidgetData(id) }
    }

    suspend fun clear() {
        store.updateData { WidgetData(-1) }
    }

    companion object {
        fun create(context: Context): WidgetStore =
            WidgetStore(context.applicationContext.widgetDataStore)
    }
}
