package com.tileshell.feature.livetiles

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tileshell.core.data.LayoutRepository
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.WeatherTile
import com.tileshell.feature.livetiles.widget.WeatherAppWidgetProvider
import com.tileshell.feature.livetiles.widget.WidgetWork
import com.tileshell.feature.livetiles.widget.WeatherWidgetRefreshWorker
import com.tileshell.feature.livetiles.widget.WidgetConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Decides where to fetch weather for, given what the app is allowed to use. The
 * opt-in order is: a granted coarse location → the manual-city fallback → give
 * up (`null`, tile stays static). Pure so the precedence is unit-testable.
 */
fun resolveWeatherQuery(
    location: Pair<Double, Double>?,
    manualCity: String?,
): WeatherQuery? = when {
    location != null -> WeatherQuery.Coords(location.first, location.second)
    !manualCity.isNullOrBlank() -> WeatherQuery.City(manualCity)
    else -> null
}

/**
 * Periodic background refresh for every weather surface (FR-2): fetches a
 * forecast for each distinct [WeatherTile.Location.Fixed] currently requested
 * by a tile/widget ([requestedFixedPlaces]), plus one more for the device's own
 * "current location" instances (the granted coarse location, or the
 * manual-city fallback) — several tiles/widgets can each follow a different
 * place at once (user-requested), sharing one cached snapshot and one fetch
 * per distinct place. When nothing at all is resolvable (location denied, no
 * city set, no fixed place picked) it succeeds without touching the cache, so
 * every tile just stays static. A snapshot that fails to fetch retries;
 * everything else that succeeded in the same run is still written.
 *
 * Forecasts come from the live, no-API-key [OpenMeteoWeatherProvider]; the place
 * label for a coarse fix is reverse-geocoded with Android's [Geocoder]. See
 * DECISIONS S21 and "Weather tile location: ask, or pick a place".
 */
class WeatherRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val provider: WeatherProvider = OpenMeteoWeatherProvider(
        reverseGeocode = { lat, lon -> reverseGeocodePlace(applicationContext, lat, lon) },
    )

    override suspend fun doWork(): Result {
        // A periodic tick while the screen is off refreshes a forecast nobody can
        // see; the next tick after the screen comes back on picks it up, and every
        // one-off path (placement, a location change, the manual refresh) carries
        // KEY_FORCE and is never skipped. Same reasoning and helper as the widget
        // pollers -- see WidgetWork.shouldSkipWidgetRefresh.
        if (WidgetWork.skipWhileScreenOff(applicationContext, inputData.getBoolean(KEY_FORCE, false))) {
            return Result.success()
        }
        val cache = WeatherCache.create(applicationContext)

        // Every user-picked fixed place currently wanted by a tile or widget,
        // recomputed from the live layout on every run rather than tracked
        // incrementally — so a removed weather tile simply stops appearing
        // here, with no separate bookkeeping to go stale.
        val fixed = requestedFixedPlaces(applicationContext)
        cache.retainPlaces(fixed.keys)

        var anyFailed = false
        fixed.forEach { (key, place) ->
            val snapshot = runCatching {
                provider.fetch(WeatherQuery.Coords(place.lat, place.lon))
            }.getOrNull()
            if (snapshot == null) {
                anyFailed = true
            } else {
                // The picked place's own name wins over whatever the provider
                // reverse-geocodes for those coordinates: the user chose that
                // label from the search results, and a coarse reverse lookup
                // can name a neighbouring locality instead.
                cache.putPlaceSnapshot(key, snapshot.copy(place = place.name.ifBlank { snapshot.place }))
            }
        }

        val query = resolveWeatherQuery(
            location = lastCoarseLocation(applicationContext),
            manualCity = cache.read().manualCity,
        )
        if (query != null) {
            val snapshot = runCatching { provider.fetch(query) }.getOrNull()
            if (snapshot == null) anyFailed = true else cache.putSnapshot(snapshot)
        }

        // Push any placed home-screen weather widgets right away instead of
        // making them wait for their own ~30-min cycle (S32).
        WeatherWidgetRefreshWorker.refreshNow(applicationContext)
        // Retry only if something was actually attempted and failed — a run
        // with nothing resolvable at all (location denied, no city set, no
        // fixed place picked) is a legitimate no-op, not a failure.
        return if (anyFailed) Result.retry() else Result.success()
    }

    private fun lastCoarseLocation(context: Context): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        // Best-effort last-known fix from any enabled provider — no Play Services
        // dependency, no active fix request (coarse + cached is enough here).
        val loc = runCatching {
            lm.getProviders(true).asSequence()
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return null
        return loc.latitude to loc.longitude
    }

    companion object {
        /**
         * Best-effort place label for a coarse fix via Android's [Geocoder]
         * (locality → sub-admin → admin area). Returns null when geocoding is
         * unavailable or yields nothing — the provider then labels it "current
         * location". The deprecated synchronous overload is fine on a worker thread.
         */
        @Suppress("DEPRECATION")
        suspend fun reverseGeocodePlace(context: Context, lat: Double, lon: Double): String? =
            withContext(Dispatchers.IO) {
                if (!Geocoder.isPresent()) return@withContext null
                runCatching {
                    Geocoder(context, Locale.getDefault())
                        .getFromLocation(lat, lon, 1)
                        ?.firstOrNull()
                        ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                }.getOrNull()
            }

        private const val KEY_FORCE = "force"
        private const val UNIQUE_PERIODIC = "tileshell_weather_refresh"
        private const val UNIQUE_NOW = "tileshell_weather_refresh_now"

        // Skip the background refresh when there is no network — avoids a wakeup
        // that would just retry immediately and burn radio time.
        private val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Ensures the ≥30-min periodic refresh is enqueued and kicks an immediate
         * one-off so a freshly shown weather tile does not wait a full period.
         * Idempotent (KEEP) — safe to call every time a weather tile appears.
         */
        fun ensureScheduled(context: Context) {
            val wm = androidx.work.WorkManager.getInstance(context.applicationContext)
            wm.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WeatherRefreshWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(periodicConstraints)
                    .build(),
            )
            wm.enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WeatherRefreshWorker>()
                    .setInputData(workDataOf(KEY_FORCE to true))
                    .build(),
            )
        }

        /**
         * Stops the periodic forecast fetch. Called when the last home-screen
         * weather widget is removed ([com.tileshell.feature.livetiles.widget
         * .WeatherAppWidgetProvider.onDisabled]) — without it, a widget that
         * had ever been placed left this network poll running every 30 minutes
         * forever with nothing consuming it.
         *
         * Safe to over-cancel: [ensureScheduled] is idempotent and is called
         * from the in-app weather tile's own `LaunchedEffect`, so a still-pinned
         * weather tile simply re-arms it the next time it renders.
         */
        fun cancel(context: Context) {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_PERIODIC)
        }

        /** Forces a one-off refresh now (e.g. just after location is granted). */
        fun refreshNow(context: Context) {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_NOW,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WeatherRefreshWorker>()
                        .setInputData(workDataOf(KEY_FORCE to true))
                        .build(),
                )
        }
    }
}

/**
 * Every distinct fixed place a weather surface currently wants a forecast for,
 * keyed by [WeatherTile.key]: the Start grid's own weather tiles (top level and
 * inside folders, read straight from the layout DB) plus every placed
 * home-screen weather widget's stored choice. Instances set to "current
 * location" — and ones never configured — are deliberately absent: those are
 * served by the worker's own device-location path, which writes the single
 * shared [WeatherCacheData.snapshot].
 *
 * Read from the authoritative stores on every refresh rather than kept as a
 * registry, so removing a tile or widget needs no cleanup step of its own.
 */
suspend fun requestedFixedPlaces(context: Context): Map<String, WeatherTile.Location.Fixed> {
    val fromTiles = runCatching {
        LayoutRepository.create(context).tiles.first().flatMap { tile ->
            when (tile) {
                is TileModel.App -> listOf((tile.packageName.isBlank() && tile.iconKey == WeatherTile.ICON_KEY) to tile.activityName)
                is TileModel.Folder -> tile.children.map {
                    (it.packageName.isBlank() && it.iconKey == WeatherTile.ICON_KEY) to it.activityName
                }
            }
        }
    }.getOrDefault(emptyList())
        .filter { (isWeather, _) -> isWeather }
        .map { (_, activityName) -> activityName }

    val fromWidgets = runCatching {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, WeatherAppWidgetProvider::class.java))
            .map { WidgetConfigStore.weatherLocation(context, it) }
    }.getOrDefault(emptyList())

    return WeatherTile.fixedPlaces(fromTiles + fromWidgets)
}
