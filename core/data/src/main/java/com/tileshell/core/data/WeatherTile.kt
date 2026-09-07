package com.tileshell.core.data

/**
 * A weather tile's own location, so several weather tiles/widgets can each
 * follow a different place (user-requested: "when weather tile and widget is
 * added, ask for current location or select location … hence multiple weather
 * tile/widgets are allowed").
 *
 * Encoded into a blank-package tile's [TileModel.App.activityName] — the same
 * no-schema-migration trick [StockTile]/[SportsTile]/[CountdownTile]/
 * [ContactTile] already use, and safe for weather specifically because
 * `DefaultLayout.roleFor("weather")` resolves to nothing on any device (no
 * `CATEGORY_APP_WEATHER` role query), so a weather tile's package/activity
 * columns are always blank and free to carry configuration. The home-screen
 * weather widget stores the identical string per `appWidgetId` in
 * `WidgetConfigStore` instead, so both surfaces share one encoding and one
 * cache key.
 *
 * [Location.Current] is not "no choice yet" — it is an explicit pick that
 * follows the device's coarse location wherever it goes, which is why a
 * *missing* encoding (null from [decode]) stays distinguishable: that's an
 * unconfigured tile, and the surfaces use it to know they still have to ask.
 */
object WeatherTile {
    /** [TileModel.App.iconKey] for a weather tile. */
    const val ICON_KEY = "weather"

    private const val PREFIX = "weather:"
    private const val KIND_CURRENT = "current"
    private const val KIND_AT = "at"

    /** The cache key every "follow the device's location" instance shares. */
    const val CURRENT_KEY = "current"

    sealed class Location {
        /** Follow the device's coarse location (needs `ACCESS_COARSE_LOCATION`). */
        data object Current : Location()

        /** A fixed, geocoded place — [name] is what the tile labels itself with. */
        data class Fixed(val lat: Double, val lon: Double, val name: String) : Location()
    }

    fun encode(location: Location): String = when (location) {
        Location.Current -> "$PREFIX$KIND_CURRENT"
        is Location.Fixed -> "$PREFIX$KIND_AT|${location.lat}|${location.lon}|${sanitize(location.name)}"
    }

    /**
     * Decodes an `activityName` (or a stored widget config value) back to a
     * location, or null when this tile has never been configured — a freshly
     * added tile, or one seeded before this feature existed. A malformed or
     * un-parseable coordinate pair also reads as null rather than silently
     * resolving to some other place.
     */
    fun decode(encoded: String?): Location? {
        if (encoded == null || !encoded.startsWith(PREFIX)) return null
        val rest = encoded.removePrefix(PREFIX)
        if (rest == KIND_CURRENT) return Location.Current
        val parts = rest.split("|", limit = 4)
        if (parts.size < 3 || parts[0] != KIND_AT) return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        val lon = parts[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Location.Fixed(lat, lon, parts.getOrNull(3).orEmpty())
    }

    /**
     * The key this location's forecast is cached under. Fixed places round to
     * two decimals (~1 km) so two tiles pointed at the same city — picked from
     * search results that differ in the last decimal place — share one cached
     * snapshot and one network fetch instead of two.
     */
    fun key(location: Location): String = when (location) {
        Location.Current -> CURRENT_KEY
        is Location.Fixed -> "${round2(location.lat)},${round2(location.lon)}"
    }

    /** [key] for a stored encoding; an unconfigured tile falls back to the device location. */
    fun keyFor(encoded: String?): String = key(decode(encoded) ?: Location.Current)

    /**
     * Every distinct fixed place across a set of stored encodings, keyed by
     * [key] — what the refresh worker actually has to fetch, deduped, with
     * "current location" instances (and unconfigured ones) excluded since
     * those are served by the worker's own device-location path.
     */
    fun fixedPlaces(encodings: List<String?>): Map<String, Location.Fixed> {
        val out = LinkedHashMap<String, Location.Fixed>()
        encodings.forEach { encoded ->
            val fixed = decode(encoded) as? Location.Fixed ?: return@forEach
            out.putIfAbsent(key(fixed), fixed)
        }
        return out
    }

    // Locale.ROOT, not the default locale: a comma-decimal locale would key the
    // same place differently from a period-decimal one, and these keys are
    // persisted in the weather cache across settings/locale changes.
    private fun round2(value: Double): String = String.format(java.util.Locale.ROOT, "%.2f", value)

    /** Keeps the encoding's own separators (and newlines) out of a place name. */
    private fun sanitize(name: String): String =
        name.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim()
}
