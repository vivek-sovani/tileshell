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
 * worker fetched for the *device's* location, the user's [manualCity] fallback,
 * and one snapshot per user-picked fixed place in [places]. A null snapshot
 * means "no data yet" — the tile degrades to static until the worker writes one.
 *
 * [places] is keyed by [com.tileshell.core.data.WeatherTile.key], so several
 * weather tiles/widgets pointed at the same city share one entry (and one
 * network fetch) while each still reads only its own place. Instances that
 * follow the device's location all read [snapshot] instead — that key
 * (`WeatherTile.CURRENT_KEY`) never appears in [places].
 *
 * [manualCity] is kept here (not in LauncherSettings) so the weather feature is
 * self-contained; a settings entry UI to set it lands later (DECISIONS S21).
 */
data class WeatherCacheData(
    val snapshot: WeatherSnapshot? = null,
    val manualCity: String? = null,
    val places: Map<String, WeatherSnapshot> = emptyMap(),
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
            append("feelsLike=").append(s.feelsLikeC?.toString().orEmpty()).append('\n')
            append("wind=").append(s.windKph?.toString().orEmpty()).append('\n')
            append("humidity=").append(s.humidityPct?.toString().orEmpty()).append('\n')
            append("isDay=").append(s.isDay.codecFlag()).append('\n')
            append("uvMax=").append(s.uvIndexMax?.toString().orEmpty()).append('\n')
            append("utcOffset=").append(s.utcOffsetSeconds?.toString().orEmpty()).append('\n')
            // condition last among the single-value keys: it is the presence
            // marker for a valid snapshot.
            append("condition=").append(s.condition)
            // 7-day outlook (user-requested), one `forecastN=` line per day —
            // `|`-joined since none of these fields can contain a `|`
            // (day labels are "today"/"tomorrow"/a lowercase weekday name,
            // conditions come from the fixed weatherCodeToCondition phrases,
            // isoDate is `YYYY-MM-DD`).
            s.forecast.forEachIndexed { i, day ->
                append('\n').append("forecast").append(i).append('=')
                    .append(day.dayLabel).append('|')
                    .append(day.highC).append('|')
                    .append(day.lowC).append('|')
                    .append(day.condition).append('|')
                    .append(day.isoDate).append('|')
                    .append(day.precipProbabilityMax?.toString().orEmpty()).append('|')
                    .append(day.sunriseMillis?.toString().orEmpty()).append('|')
                    .append(day.sunsetMillis?.toString().orEmpty())
            }
            // Weather hub's next-24h row, one `hourlyN=` line per hour.
            s.hourly.forEachIndexed { i, hour ->
                append('\n').append("hourly").append(i).append('=')
                    .append(hour.hourLabel).append('|')
                    .append(hour.tempC).append('|')
                    .append(hour.condition).append('|')
                    .append(hour.isDay.codecFlag())
            }
        }
        // One `loc=` line per user-picked fixed location, each self-contained
        // (its own forecast days appended) so the whole set round-trips without
        // needing per-place line ordering. `loc`, not `place` — `place=` is
        // already the *device*-location snapshot's own label line above, and a
        // duplicate key would be parsed as that instead. `~` separates fields and `;`/`|` the
        // forecast days — none of which can appear in the values (place names
        // and details are sanitized on the way in, conditions come from the
        // fixed weatherCodeToCondition phrases).
        data.places.forEach { (key, s) ->
            append('\n').append("loc=").append(clean(key)).append('~')
                .append(s.tempC).append('~')
                .append(s.highC).append('~')
                .append(s.lowC).append('~')
                .append(s.fetchedAtMillis).append('~')
                .append(clean(s.condition)).append('~')
                .append(clean(s.place)).append('~')
                .append(clean(s.detail)).append('~')
                .append(
                    s.forecast.joinToString(";") { day ->
                        "${clean(day.dayLabel)}|${day.highC}|${day.lowC}|${clean(day.condition)}" +
                            "|${day.isoDate}|${day.precipProbabilityMax ?: ""}" +
                            "|${day.sunriseMillis ?: ""}|${day.sunsetMillis ?: ""}"
                    },
                ).append('~')
                // Appended after the original 9 fields (index 0-8) so a file
                // written by an older build — which stops at the forecast
                // field — still decodes: decodePlaceLine reads these via
                // getOrNull and defaults to null/empty when absent.
                .append(s.feelsLikeC?.toString().orEmpty()).append('~')
                .append(s.windKph?.toString().orEmpty()).append('~')
                .append(s.humidityPct?.toString().orEmpty()).append('~')
                .append(
                    s.hourly.joinToString(";") { hour ->
                        "${clean(hour.hourLabel)}|${hour.tempC}|${clean(hour.condition)}|${hour.isDay.codecFlag()}"
                    },
                ).append('~')
                .append(s.isDay.codecFlag()).append('~')
                .append(s.uvIndexMax?.toString().orEmpty()).append('~')
                .append(s.utcOffsetSeconds?.toString().orEmpty())
        }
    }

    /** `1`/`0`/"" for a nullable day flag; read back by [decodeFlag]. */
    private fun Boolean?.codecFlag(): String = when (this) {
        true -> "1"
        false -> "0"
        null -> ""
    }

    private fun decodeFlag(value: String?): Boolean? = when (value?.trim()) {
        "1" -> true
        "0" -> false
        else -> null
    }

    /** Strips this codec's own separators so a value can never split a line. */
    private fun clean(value: String): String =
        value.replace('~', ' ').replace(';', ' ').replace('|', ' ').replace('\n', ' ').replace('\r', ' ')

    fun decode(text: String): WeatherCacheData {
        var manualCity: String? = null
        var temp: Int? = null
        var high: Int? = null
        var low: Int? = null
        var fetchedAt = 0L
        var place = ""
        var detail = ""
        var condition: String? = null
        var feelsLike: Int? = null
        var wind: Int? = null
        var humidity: Int? = null
        var isDay: Boolean? = null
        var uvMax: Int? = null
        var utcOffset: Int? = null
        val forecastByIndex = sortedMapOf<Int, DailyForecast>()
        val hourlyByIndex = sortedMapOf<Int, HourlyForecast>()
        val places = LinkedHashMap<String, WeatherSnapshot>()
        text.lineSequence().forEach { line ->
            val sep = line.indexOf('=')
            if (sep <= 0) return@forEach
            val key = line.substring(0, sep).trim()
            val value = line.substring(sep + 1)
            when {
                key == "manualCity" -> manualCity = value.trim().ifEmpty { null }
                key == "temp" -> temp = value.trim().toIntOrNull()
                key == "high" -> high = value.trim().toIntOrNull()
                key == "low" -> low = value.trim().toIntOrNull()
                key == "fetchedAt" -> fetchedAt = value.trim().toLongOrNull() ?: 0L
                key == "place" -> place = value
                key == "detail" -> detail = value
                key == "condition" -> condition = value.ifEmpty { null }
                key == "feelsLike" -> feelsLike = value.trim().toIntOrNull()
                key == "wind" -> wind = value.trim().toIntOrNull()
                key == "humidity" -> humidity = value.trim().toIntOrNull()
                key == "isDay" -> isDay = decodeFlag(value)
                key == "uvMax" -> uvMax = value.trim().toIntOrNull()
                key == "utcOffset" -> utcOffset = value.trim().toIntOrNull()
                key == "loc" -> decodePlaceLine(value)?.let { (placeKey, snapshot) ->
                    places[placeKey] = snapshot
                }
                key.startsWith("forecast") -> {
                    val index = key.removePrefix("forecast").toIntOrNull() ?: return@forEach
                    parseDailyForecastField(value)?.let { forecastByIndex[index] = it }
                }
                key.startsWith("hourly") -> {
                    val index = key.removePrefix("hourly").toIntOrNull() ?: return@forEach
                    parseHourlyForecastField(value)?.let { hourlyByIndex[index] = it }
                }
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
                forecast = forecastByIndex.values.toList(),
                hourly = hourlyByIndex.values.toList(),
                feelsLikeC = feelsLike,
                windKph = wind,
                humidityPct = humidity,
                isDay = isDay,
                uvIndexMax = uvMax,
                utcOffsetSeconds = utcOffset,
            )
        } else {
            null
        }
        return WeatherCacheData(snapshot = snapshot, manualCity = manualCity, places = places)
    }

    /**
     * One `forecastN=`/`loc=`-embedded day field → a [DailyForecast]; null when
     * malformed. Accepts the original 4-field form (`label|high|low|cond`),
     * the 6-field form with `isoDate`/`precip`, and the current 8-field form with
     * `sunrise`/`sunset` epoch millis appended too, so a cache
     * file written before those fields existed still decodes.
     */
    private fun parseDailyForecastField(value: String): DailyForecast? {
        val parts = value.split('|')
        if (parts.size < 4) return null
        val dHigh = parts[1].toIntOrNull() ?: return null
        val dLow = parts[2].toIntOrNull() ?: return null
        return DailyForecast(
            dayLabel = parts[0],
            highC = dHigh,
            lowC = dLow,
            condition = parts[3],
            isoDate = parts.getOrNull(4).orEmpty(),
            precipProbabilityMax = parts.getOrNull(5)?.toIntOrNull(),
            sunriseMillis = parts.getOrNull(6)?.toLongOrNull(),
            sunsetMillis = parts.getOrNull(7)?.toLongOrNull(),
        )
    }

    /** One `hourlyN=`/`loc=`-embedded hour field → an [HourlyForecast]; null when malformed. */
    private fun parseHourlyForecastField(value: String): HourlyForecast? {
        val parts = value.split('|')
        // 3 fields in a file written before the day/night flag existed, 4 since.
        if (parts.size !in 3..4) return null
        val temp = parts[1].toIntOrNull() ?: return null
        return HourlyForecast(
            hourLabel = parts[0],
            tempC = temp,
            condition = parts[2],
            isDay = decodeFlag(parts.getOrNull(3)),
        )
    }

    /** One `loc=` line → its cache key + snapshot; null when malformed. */
    private fun decodePlaceLine(value: String): Pair<String, WeatherSnapshot>? {
        val f = value.split('~')
        if (f.size < 8) return null
        val key = f[0].trim().ifEmpty { return null }
        val temp = f[1].trim().toIntOrNull() ?: return null
        val high = f[2].trim().toIntOrNull() ?: return null
        val low = f[3].trim().toIntOrNull() ?: return null
        val condition = f[5].ifEmpty { return null }
        val forecast = f.getOrNull(8).orEmpty()
            .split(';')
            .mapNotNull { parseDailyForecastField(it) }
        // Fields 9-12 are absent in a file written before the weather hub
        // existed — getOrNull/toIntOrNull default them to null/empty, exactly
        // like a snapshot that genuinely has no hourly/detail data.
        val feelsLike = f.getOrNull(9)?.toIntOrNull()
        val wind = f.getOrNull(10)?.toIntOrNull()
        val humidity = f.getOrNull(11)?.toIntOrNull()
        val hourly = f.getOrNull(12).orEmpty()
            .split(';')
            .mapNotNull { parseHourlyForecastField(it) }
        return key to WeatherSnapshot(
            tempC = temp,
            condition = condition,
            highC = high,
            lowC = low,
            detail = f[7],
            place = f[6],
            fetchedAtMillis = f[4].trim().toLongOrNull() ?: 0L,
            forecast = forecast,
            hourly = hourly,
            feelsLikeC = feelsLike,
            windKph = wind,
            humidityPct = humidity,
            isDay = decodeFlag(f.getOrNull(13)),
            uvIndexMax = f.getOrNull(14)?.trim()?.toIntOrNull(),
            utcOffsetSeconds = f.getOrNull(15)?.trim()?.toIntOrNull(),
        )
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

    /** Stores the forecast for one user-picked fixed place (see [WeatherCacheData.places]). */
    suspend fun putPlaceSnapshot(key: String, snapshot: WeatherSnapshot) {
        store.updateData { it.copy(places = it.places + (key to snapshot)) }
    }

    /**
     * Drops cached places no longer wanted by any tile/widget — called by the
     * refresh worker with the set it just recomputed from the live layout, so
     * removing a weather tile stops its city being fetched and stops its
     * snapshot sitting in the file forever.
     */
    suspend fun retainPlaces(keys: Set<String>) {
        store.updateData { current ->
            val kept = current.places.filterKeys { it in keys }
            if (kept.size == current.places.size) current else current.copy(places = kept)
        }
    }

    companion object {
        fun create(context: Context): WeatherCache =
            WeatherCache(context.applicationContext.weatherDataStore)
    }
}
