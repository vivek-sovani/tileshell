package com.tileshell.feature.livetiles

import android.Manifest
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
import kotlinx.coroutines.Dispatchers
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
 * Periodic background refresh for the weather tile (FR-2): resolves a query from
 * the granted coarse location (or the manual-city fallback), asks the
 * [WeatherProvider] for a snapshot, and writes it to [WeatherCache]. When no
 * query can be resolved (location denied and no city set) it succeeds without
 * touching the cache, so the tile stays static. Network failures retry.
 *
 * Forecasts come from the live, no-API-key [OpenMeteoWeatherProvider]; the place
 * label for a coarse fix is reverse-geocoded with Android's [Geocoder]. A failed
 * fetch retries and the tile keeps its last good snapshot (DECISIONS S21).
 */
class WeatherRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val provider: WeatherProvider = OpenMeteoWeatherProvider(
        reverseGeocode = { lat, lon -> reverseGeocodePlace(applicationContext, lat, lon) },
    )

    override suspend fun doWork(): Result {
        val cache = WeatherCache.create(applicationContext)
        val query = resolveWeatherQuery(
            location = lastCoarseLocation(applicationContext),
            manualCity = cache.read().manualCity,
        ) ?: return Result.success()

        val snapshot = runCatching { provider.fetch(query) }.getOrNull()
            ?: return Result.retry()
        cache.putSnapshot(snapshot)
        return Result.success()
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
                OneTimeWorkRequestBuilder<WeatherRefreshWorker>().build(),
            )
        }

        /** Forces a one-off refresh now (e.g. just after location is granted). */
        fun refreshNow(context: Context) {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_NOW,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WeatherRefreshWorker>().build(),
                )
        }
    }
}
