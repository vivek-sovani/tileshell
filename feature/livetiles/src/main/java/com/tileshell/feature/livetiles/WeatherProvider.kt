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
 */
data class WeatherSnapshot(
    val tempC: Int,
    val condition: String,
    val highC: Int,
    val lowC: Int,
    val detail: String = "",
    val place: String = "",
    val fetchedAtMillis: Long = 0L,
)

/**
 * The pluggable seam (FR-2 "via a pluggable provider interface"): a real build
 * swaps in a network-backed implementation behind this interface — the worker,
 * cache and tile never change. Returns `null` when the lookup fails so the tile
 * keeps its last cache (or degrades to static).
 */
fun interface WeatherProvider {
    suspend fun fetch(query: WeatherQuery): WeatherSnapshot?
}

/**
 * Offline stand-in used until a network provider is wired in. Returns the
 * prototype's sample forecast (tiles.js `liveFace('weather')`) so the tile is
 * demonstrable on a device without an API key; it still honours opt-in because
 * the worker only calls a provider once it has a [WeatherQuery]. See DECISIONS
 * S21.
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
