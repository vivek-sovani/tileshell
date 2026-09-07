package com.tileshell.core.data

import org.json.JSONObject
import java.net.URLEncoder

private const val OPEN_METEO_GEOCODE_BASE = "https://geocoding-api.open-meteo.com/v1/search"

/** One geocoding match: coordinates plus a human label built from name/admin1/country. */
data class WeatherPlaceResult(val lat: Double, val lon: Double, val displayName: String)

/**
 * Search-as-you-type place lookup for the weather tile/widget picker (see
 * [WeatherTile]) — same shape as [fetchStockSearch]: a handful of results for
 * a partial name, via the free, no-API-key Open-Meteo geocoding endpoint
 * ([feature:livetiles]'s `OpenMeteoWeather.kt` already uses the same service
 * for its own single-best-match lookup; this is the multi-result sibling,
 * placed here rather than there so `:feature:personalize`'s picker sheet can
 * call it without a new cross-feature dependency — the same reasoning
 * [formatStockPrice]'s doc comment gives for living in `:core:data`).
 */
suspend fun fetchWeatherPlaceSearch(query: String): List<WeatherPlaceResult> {
    if (query.isBlank()) return emptyList()
    val encoded = URLEncoder.encode(query.trim(), "UTF-8")
    val body = httpGetText("$OPEN_METEO_GEOCODE_BASE?name=$encoded&count=8") ?: return emptyList()
    return parseWeatherPlaceResults(body)
}

/** Pure parse of an Open-Meteo geocoding response's `results` array. */
internal fun parseWeatherPlaceResults(json: String): List<WeatherPlaceResult> = runCatching {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
    (0 until results.length()).mapNotNull { i ->
        val r = results.optJSONObject(i) ?: return@mapNotNull null
        val lat = r.optDouble("latitude", Double.NaN)
        val lon = r.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
        val name = r.optString("name", "").ifEmpty { return@mapNotNull null }
        WeatherPlaceResult(lat = lat, lon = lon, displayName = weatherPlaceLabel(name, r))
    }
}.getOrDefault(emptyList())

/** `name, admin1, country` — as many parts as the response actually has. Pure. */
internal fun weatherPlaceLabel(name: String, r: JSONObject): String {
    val admin1 = r.optString("admin1", "")
    val country = r.optString("country", "")
    return listOf(name, admin1, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}
