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
 */
data class DailyForecast(
    val dayLabel: String,
    val highC: Int,
    val lowC: Int,
    val condition: String,
    val isoDate: String = "",
    val precipProbabilityMax: Int? = null,
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
)

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
