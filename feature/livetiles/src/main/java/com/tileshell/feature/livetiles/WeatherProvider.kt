package com.tileshell.feature.livetiles

/**
 * Where a forecast is wanted (FR-2 weather tile). Coarse [Coords] when the user
 * has granted location; [City] is the manual fallback used when location is
 * denied or unavailable.
 */
sealed interface WeatherQuery {
    data class Coords(val lat: Double, val lon: Double) : WeatherQuery
    data class City(val name: String) : WeatherQuery
}

/**
 * One resolved forecast, in Celsius. Deliberately small and framework-free so it
 * round-trips through [WeatherCacheCodec] and feeds the pure face formatters.
 *
 * @property tempC current temperature
 * @property condition short condition phrase, lowercase (prototype "partly cloudy")
 * @property highC / [lowC] today's range (back face "26° / 17°")
 * @property detail the prototype's back-face line ("rain by 6pm · 40%"); may be ""
 * @property place resolved place label (for diagnostics / future header use)
 * @property fetchedAtMillis when this snapshot was produced (staleness checks)
 * @property feelsLikeC / [windKph] / [humidityPct] the weather hub's detail line;
 *   null when the response doesn't carry that field. Not shown on the tile faces.
 * @property isDay Open-Meteo's `current.is_day` at fetch time; null for a
 *   cache file written before it existed. Only a fallback for [isNightAt] —
 *   the day's own sunrise/sunset (on [forecast]) is preferred, since a cached
 *   flag goes stale the moment the sun sets between two refreshes.
 * @property uvIndexMax today's max UV index (weather hub); null when absent.
 * @property utcOffsetSeconds the forecast place's own UTC offset, so the hub
 *   can show sunrise/sunset in that place's local time (a fixed city can be in
 *   another zone than the device); null for an old cache file.
 * @property hourly the next ~24h outlook (weather hub only); empty on the tile
 *   faces' own fetches would be wasteful, but the provider always requests it
 *   now, so this is populated whenever [forecast] is.
 */
data class WeatherSnapshot(
    val tempC: Int,
    val condition: String,
    val highC: Int,
    val lowC: Int,
    val detail: String = "",
    val place: String = "",
    val fetchedAtMillis: Long = 0L,
    val forecast: List<DailyForecast> = emptyList(),
    val hourly: List<HourlyForecast> = emptyList(),
    val feelsLikeC: Int? = null,
    val windKph: Int? = null,
    val humidityPct: Int? = null,
    val isDay: Boolean? = null,
    val uvIndexMax: Int? = null,
    val utcOffsetSeconds: Int? = null,
)

/**
 * One day of the multi-day outlook (user-requested: "show next 7 days
 * prediction"). [dayLabel] is "today"/"tomorrow"/a lowercase weekday name,
 * matching the same today/tomorrow/weekday convention [AlarmFace] already
 * uses for its own date labelling. Defaults to an empty list on
 * [WeatherSnapshot] so every existing caller/cache-file compiles/decodes
 * unchanged — only call sites that actually want the outlook need to read it.
 *
 * @property isoDate the day's own `YYYY-MM-DD` (weather hub header use); ""
 *   for a snapshot decoded from a cache file written before this field existed.
 * @property precipProbabilityMax that day's max chance of rain, 0-100; null when
 *   the response didn't carry it (or an old cache file predates this field).
 * @property sunriseMillis / [sunsetMillis] that day's sunrise/sunset as epoch
 *   millis; null when absent (old cache file). Drive [isNightAt].
 */
data class DailyForecast(
    val dayLabel: String,
    val highC: Int,
    val lowC: Int,
    val condition: String,
    val isoDate: String = "",
    val precipProbabilityMax: Int? = null,
    val sunriseMillis: Long? = null,
    val sunsetMillis: Long? = null,
)

/**
 * One hour of the near-term outlook (weather hub only — the tile faces never
 * read this). [hourLabel] is "now" for the first entry (the one nearest
 * `current.time`), else a 12-hour clock label like "3pm".
 */
data class HourlyForecast(
    val hourLabel: String,
    val tempC: Int,
    val condition: String,
    val isDay: Boolean? = null,
)

/**
 * Whether it is night at [nowMillis] for this snapshot's place (user-requested:
 * a moon instead of the sun at night). Pure. Prefers the forecast's own
 * sunrise/sunset, so the answer changes at sunset even between two refreshes;
 * falls back to the fetch-time [WeatherSnapshot.isDay] when the sun times don't
 * cover [nowMillis] (old cache file, or a snapshot more than a week stale), and
 * reads as day when neither is known.
 */
fun WeatherSnapshot.isNightAt(nowMillis: Long): Boolean {
    val sun = forecast.mapNotNull { d ->
        val rise = d.sunriseMillis ?: return@mapNotNull null
        val set = d.sunsetMillis ?: return@mapNotNull null
        rise to set
    }.sortedBy { it.first }
    if (sun.isNotEmpty()) {
        if (sun.any { (rise, set) -> nowMillis in rise until set }) return false
        // Before the first sunrise (the night leading into it) or between a
        // sunset and the next sunrise within the covered span → night.
        if (nowMillis >= sun.first().first - DAY_MILLIS && nowMillis < sun.last().second + DAY_MILLIS / 2) {
            return true
        }
    }
    return isDay == false
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/**
 * The pluggable seam (FR-2 "via a pluggable provider interface"). The live build
 * uses the network-backed [OpenMeteoWeatherProvider]; this interface keeps the
 * worker/cache/tile decoupled from the source. Returns `null` when the lookup
 * fails so the tile keeps its last cache (or degrades to static).
 */
fun interface WeatherProvider {
    suspend fun fetch(query: WeatherQuery): WeatherSnapshot?
}

/**
 * Offline stand-in (the prototype's fixed forecast, tiles.js `liveFace('weather')`).
 * Superseded by [OpenMeteoWeatherProvider] for the live tile; retained for previews
 * and offline manual testing. See DECISIONS S21.
 */
object SampleWeatherProvider : WeatherProvider {
    override suspend fun fetch(query: WeatherQuery): WeatherSnapshot {
        val place = when (query) {
            is WeatherQuery.City -> query.name
            is WeatherQuery.Coords -> "current location"
        }
        return WeatherSnapshot(
            tempC = 23,
            condition = "partly cloudy",
            highC = 26,
            lowC = 17,
            detail = "rain by 6pm · 40%",
            place = place,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }
}

/** Formats a Celsius value as the WP tile shows it: integer + degree sign. */
fun tempLabel(tempC: Int): String = "$tempC°"

/** The back-face high/low line, e.g. `26° / 17°` (prototype). */
fun highLowLabel(highC: Int, lowC: Int): String = "${tempLabel(highC)} / ${tempLabel(lowC)}"
