package com.tileshell.feature.livetiles.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.tileshell.core.data.settings.RefreshRatePrefs
import com.tileshell.core.data.settings.resolveMs
import com.tileshell.feature.livetiles.WeatherCache
import com.tileshell.feature.livetiles.openMeteoGeocodeUrl
import com.tileshell.feature.livetiles.parseOpenMeteoGeocode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AirUi(
    val snapshot: AirSnapshot? = null,
    val loading: Boolean = false,
    /** Why there is nothing to show: no place, or the request failed. */
    val problem: String? = null,
)

/**
 * Air quality for the weather's place. Fetched only when the air page is shown or refreshed (never in the
 * background), cached in `files/air_cache.txt`, and not fetched again before the weather refresh rate has passed
 * unless asked. The coordinates (a coarse location, or the weather's typed city) are the only thing sent.
 */
object AirRepository {
    private const val FILE = "air_cache.txt"
    private val _ui = MutableStateFlow(AirUi())
    val ui: StateFlow<AirUi> = _ui.asStateFlow()

    suspend fun load(context: Context, force: Boolean) {
        val app = context.applicationContext
        val maxAge = RefreshRatePrefs.rate(app, RefreshRatePrefs.WEATHER).resolveMs(30 * 60_000L)
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (_ui.value.snapshot == null) {
                runCatching { decodeAirCache(File(app.filesDir, FILE).readText()) }.getOrNull()?.let { c ->
                    parseAirQuality(c[3], c[2].toLong())?.let { _ui.value = AirUi(snapshot = it) }
                }
            }
            val have = _ui.value.snapshot
            if (!force && have != null && now - have.fetchedAtMs < maxAge) return@withContext
            _ui.value = _ui.value.copy(loading = true, problem = null)
            val place = resolvePlace(app)
            if (place == null) {
                _ui.value = _ui.value.copy(loading = false, problem = "needs a place: allow location or set a city in weather")
                return@withContext
            }
            val raw = httpGet(airQualityUrl(place.first, place.second))
            val snap = raw?.let { parseAirQuality(it, System.currentTimeMillis()) }
            if (snap == null) {
                _ui.value = _ui.value.copy(loading = false, problem = if (have == null) "could not read air quality; check the connection" else "could not refresh; showing the last reading")
                return@withContext
            }
            runCatching { File(app.filesDir, FILE).writeText(encodeAirCache(place.first, place.second, raw, snap.fetchedAtMs)) }
            _ui.value = AirUi(snapshot = snap)
        }
    }

    /** The cached reading (memory, else the file), never fetching: what the tile uses. */
    fun peek(context: Context): AirSnapshot? {
        _ui.value.snapshot?.let { return it }
        val c = runCatching { decodeAirCache(File(context.applicationContext.filesDir, FILE).readText()) }.getOrNull() ?: return null
        return parseAirQuality(c[3], c[2].toLong())
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
        _ui.value = AirUi()
    }

    private suspend fun resolvePlace(context: Context): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val loc = runCatching { lm?.getProviders(true)?.mapNotNull { lm.getLastKnownLocation(it) }?.maxByOrNull { it.time } }.getOrNull()
            if (loc != null) return loc.latitude to loc.longitude
        }
        val city = runCatching { WeatherCache.create(context).read().manualCity }.getOrNull()
        if (!city.isNullOrBlank()) {
            val geo = httpGet(openMeteoGeocodeUrl(city))?.let { parseOpenMeteoGeocode(it) }
            if (geo != null) return geo.lat to geo.lon
        }
        return null
    }

    private fun httpGet(url: String): String? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8_000; readTimeout = 8_000 }
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) null else c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
