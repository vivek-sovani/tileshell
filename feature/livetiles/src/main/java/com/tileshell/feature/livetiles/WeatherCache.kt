package com.tileshell.feature.livetiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

/**
 * Persisted weather state (FR-2 weather tile): the last good [snapshot] the
 * worker fetched and the user's [manualCity] fallback. A null snapshot means
 * "no data yet" — the tile degrades to static until the worker writes one.
 *
 * [manualCity] is kept here (not in LauncherSettings) so the weather feature is
 * self-contained; a settings entry UI to set it lands later (DECISIONS S21).
 */
data class WeatherCacheData(
    val snapshot: WeatherSnapshot? = null,
    val manualCity: String? = null,
)

/**
 * Flat `key=value` codec for [WeatherCacheData], mirroring the project's
 * SettingsCodec approach (DECISIONS S17): pure Kotlin, JVM-testable, and
 * tolerant — malformed lines and missing keys fall back to "no snapshot". The
 * condition/detail/place values run to end-of-line so spaces survive; they must
 * therefore never contain a newline (none do).
 */
object WeatherCacheCodec {

    fun encode(data: WeatherCacheData): String = buildString {
        append("manualCity=").append(data.manualCity.orEmpty()).append('\n')
        val s = data.snapshot
        if (s != null) {
            append("temp=").append(s.tempC).append('\n')
            append("high=").append(s.highC).append('\n')
            append("low=").append(s.lowC).append('\n')
            append("fetchedAt=").append(s.fetchedAtMillis).append('\n')
            append("place=").append(s.place).append('\n')
            append("detail=").append(s.detail).append('\n')
            // condition last: it is the presence marker for a valid snapshot.
            append("condition=").append(s.condition)
        }
    }

    fun decode(text: String): WeatherCacheData {
        var manualCity: String? = null
        var temp: Int? = null
        var high: Int? = null
        var low: Int? = null
        var fetchedAt = 0L
        var place = ""
        var detail = ""
        var condition: String? = null
        text.lineSequence().forEach { line ->
            val sep = line.indexOf('=')
            if (sep <= 0) return@forEach
            val key = line.substring(0, sep).trim()
            val value = line.substring(sep + 1)
            when (key) {
                "manualCity" -> manualCity = value.trim().ifEmpty { null }
                "temp" -> temp = value.trim().toIntOrNull()
                "high" -> high = value.trim().toIntOrNull()
                "low" -> low = value.trim().toIntOrNull()
                "fetchedAt" -> fetchedAt = value.trim().toLongOrNull() ?: 0L
                "place" -> place = value
                "detail" -> detail = value
                "condition" -> condition = value.ifEmpty { null }
            }
        }
        // A snapshot is only valid with the numeric fields and a condition.
        val snapshot = if (temp != null && high != null && low != null && condition != null) {
            WeatherSnapshot(
                tempC = temp!!,
                condition = condition!!,
                highC = high!!,
                lowC = low!!,
                detail = detail,
                place = place,
                fetchedAtMillis = fetchedAt,
            )
        } else {
            null
        }
        return WeatherCacheData(snapshot = snapshot, manualCity = manualCity)
    }
}

private object WeatherCacheSerializer : Serializer<WeatherCacheData> {
    override val defaultValue = WeatherCacheData()

    override suspend fun readFrom(input: InputStream): WeatherCacheData =
        WeatherCacheCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: WeatherCacheData, output: OutputStream) {
        output.write(WeatherCacheCodec.encode(t).encodeToByteArray())
    }
}

private val Context.weatherDataStore: DataStore<WeatherCacheData> by dataStore(
    fileName = "weather_cache.pb",
    serializer = WeatherCacheSerializer,
)

/** Reads/writes the weather cache. Backed by its own DataStore file. */
class WeatherCache(private val store: DataStore<WeatherCacheData>) {

    val data: Flow<WeatherCacheData> = store.data

    suspend fun read(): WeatherCacheData = store.data.first()

    suspend fun putSnapshot(snapshot: WeatherSnapshot) {
        store.updateData { it.copy(snapshot = snapshot) }
    }

    suspend fun setManualCity(city: String?) {
        store.updateData { it.copy(manualCity = city?.trim()?.ifEmpty { null }) }
    }

    companion object {
        fun create(context: Context): WeatherCache =
            WeatherCache(context.applicationContext.weatherDataStore)
    }
}
