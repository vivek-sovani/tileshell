package com.tileshell.feature.livetiles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * A geocoded place: coordinates plus the canonical [name] the API returns (used as
 * the weather tile's location label when the user typed a city).
 */
data class GeoPlace(val lat: Double, val lon: Double, val name: String)

/**
 * Maps a WMO weather-interpretation code (Open-Meteo `weather_code`) to the
 * lowercase condition phrase the tile shows (prototype style, e.g. "partly
 * cloudy"). Pure and unit-tested. Unknown codes read as a neutral dash.
 */
fun weatherCodeToCondition(code: Int): String = when (code) {
    0 -> "clear"
    1 -> "mostly clear"
    2 -> "partly cloudy"
    3 -> "overcast"
    45, 48 -> "fog"
    51, 53, 55 -> "drizzle"
    56, 57 -> "freezing drizzle"
    61, 63, 65 -> "rain"
    66, 67 -> "freezing rain"
    71, 73, 75, 77 -> "snow"
    80, 81, 82 -> "rain showers"
    85, 86 -> "snow showers"
    95, 96, 99 -> "thunderstorm"
    else -> "—"
}

/**
 * The tile's back-face detail line from the day's max precipitation probability
 * (e.g. `chance of rain · 40%`). Empty when there is no meaningful chance. Pure.
 */
fun weatherDetail(precipProbabilityMax: Int?): String =
    if (precipProbabilityMax != null && precipProbabilityMax > 0) {
        "chance of rain · $precipProbabilityMax%"
    } else {
        ""
    }

/**
 * Parses an Open-Meteo `/v1/forecast` response into a [WeatherSnapshot] for
 * [place]. Returns null when the payload lacks a usable current temperature. Pure
 * (no network) so the field extraction / rounding is unit-testable.
 */
fun parseOpenMeteoForecast(json: String, place: String, nowMillis: Long): WeatherSnapshot? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val current = root.optJSONObject("current") ?: return null
    val temp = current.optDouble("temperature_2m", Double.NaN)
    if (temp.isNaN()) return null
    val code = current.optInt("weather_code", -1)

    val daily = root.optJSONObject("daily")
    val high = daily?.optJSONArray("temperature_2m_max")?.optDoubleOrNull(0)?.roundToInt()
        ?: temp.roundToInt()
    val low = daily?.optJSONArray("temperature_2m_min")?.optDoubleOrNull(0)?.roundToInt()
        ?: temp.roundToInt()
    val precip = daily?.optJSONArray("precipitation_probability_max")?.optIntOrNull(0)

    return WeatherSnapshot(
        tempC = temp.roundToInt(),
        condition = weatherCodeToCondition(code),
        highC = high,
        lowC = low,
        detail = weatherDetail(precip),
        place = place,
        fetchedAtMillis = nowMillis,
    )
}

/**
 * Parses an Open-Meteo geocoding response, taking the first result. Returns null
 * when there is no match or it lacks coordinates. Pure.
 */
fun parseOpenMeteoGeocode(json: String): GeoPlace? {
    val results = runCatching { JSONObject(json) }.getOrNull()
        ?.optJSONArray("results") ?: return null
    if (results.length() == 0) return null
    val first = results.optJSONObject(0) ?: return null
    val lat = first.optDouble("latitude", Double.NaN)
    val lon = first.optDouble("longitude", Double.NaN)
    if (lat.isNaN() || lon.isNaN()) return null
    return GeoPlace(lat = lat, lon = lon, name = first.optString("name", ""))
}

private fun org.json.JSONArray.optDoubleOrNull(index: Int): Double? =
    optDouble(index, Double.NaN).takeIf { !it.isNaN() }

private fun org.json.JSONArray.optIntOrNull(index: Int): Int? =
    if (isNull(index)) null else optInt(index, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

/** Open-Meteo forecast endpoint for [lat]/[lon] (current + today's range). */
fun openMeteoForecastUrl(lat: Double, lon: Double): String =
    "https://api.open-meteo.com/v1/forecast" +
        "?latitude=$lat&longitude=$lon" +
        "&current=temperature_2m,weather_code" +
        "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
        "&timezone=auto&forecast_days=1"

/** Open-Meteo geocoding endpoint for a typed city [name]. */
fun openMeteoGeocodeUrl(name: String): String =
    "https://geocoding-api.open-meteo.com/v1/search" +
        "?name=${URLEncoder.encode(name, "UTF-8")}&count=1"

/**
 * Network-backed [WeatherProvider] using the free, no-API-key Open-Meteo service.
 * For [WeatherQuery.Coords] it fetches the forecast directly and labels it via
 * [reverseGeocode] (Android `Geocoder`, supplied by the worker); for
 * [WeatherQuery.City] it first geocodes the name. [httpGet] is injected so the
 * pure parsing path can be exercised without real network in tests. Any failure
 * returns null → the worker retries and the tile keeps its last good snapshot.
 */
class OpenMeteoWeatherProvider(
    private val reverseGeocode: suspend (Double, Double) -> String?,
    private val httpGet: suspend (String) -> String? = ::httpGetText,
) : WeatherProvider {

    override suspend fun fetch(query: WeatherQuery): WeatherSnapshot? {
        val place: GeoPlace = when (query) {
            is WeatherQuery.Coords -> GeoPlace(
                lat = query.lat,
                lon = query.lon,
                name = reverseGeocode(query.lat, query.lon).orEmpty(),
            )
            is WeatherQuery.City -> {
                val geo = parseOpenMeteoGeocode(httpGet(openMeteoGeocodeUrl(query.name)) ?: return null)
                    ?: return null
                geo.copy(name = geo.name.ifBlank { query.name })
            }
        }
        val body = httpGet(openMeteoForecastUrl(place.lat, place.lon)) ?: return null
        val label = place.name.ifBlank { "current location" }
        return parseOpenMeteoForecast(body, label, System.currentTimeMillis())
    }
}

/** Blocking-IO HTTP GET returning the body text, or null on any failure. */
private suspend fun httpGetText(url: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
