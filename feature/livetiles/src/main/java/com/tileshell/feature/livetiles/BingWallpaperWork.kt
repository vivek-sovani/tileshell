package com.tileshell.feature.livetiles

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.tileshell.core.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val BING_HOST = "https://www.bing.com"

/**
 * Builds the absolute URL of Microsoft's Bing "image of the day" from the
 * `HPImageArchive.aspx?format=js` JSON. The feed returns a relative `images[0].url`
 * (e.g. `/th?id=OHR.Foo_EN-US123_1920x1080.jpg&rf=...`) which is prefixed with the
 * Bing host. Pure so it can be unit-tested without a network call; returns null when
 * the JSON is missing the image array or url.
 */
fun parseBingImageUrl(json: String): String? = runCatching {
    val images = JSONObject(json).optJSONArray("images") ?: return@runCatching null
    if (images.length() == 0) return@runCatching null
    val first = images.getJSONObject(0)
    val path = first.optString("url").ifBlank { return@runCatching null }
    if (path.startsWith("http")) path else BING_HOST + path
}.getOrNull()

/**
 * Bing market (`mkt`) string for the current locale, e.g. `en-IN`, falling back to
 * `en-US` when the country is unknown. The market only selects which regional image
 * Bing serves; the parser is agnostic to it.
 */
internal fun bingMarket(locale: Locale = Locale.getDefault()): String {
    val lang = locale.language.ifBlank { "en" }
    val country = locale.country.ifBlank { "US" }
    return "$lang-$country"
}

/**
 * Daily refresh of the Microsoft Bing wallpaper (opt-in via personalize). Fetches
 * the image-of-the-day metadata, downloads the JPEG into app storage, and writes its
 * file URI into [SettingsRepository.setBingImage] so the existing custom-wallpaper
 * render path picks it up. No-ops (success) once the user turns Bing off, and retries
 * on any network failure so the tile keeps its last good image. Mirrors
 * [WeatherRefreshWorker]'s scheduling shape.
 */
class BingWallpaperWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsRepository.create(applicationContext)
        // Respect a since-toggled-off state before spending the network.
        if (!settings.settings.first().bingWallpaper) return@withContext Result.success()

        val metaUrl = "$BING_HOST/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=${bingMarket()}"
        val json = runCatching { fetchText(metaUrl) }.getOrNull() ?: return@withContext Result.retry()
        val imageUrl = parseBingImageUrl(json) ?: return@withContext Result.retry()

        val file = File(applicationContext.filesDir, "bing_wallpaper.jpg")
        val ok = runCatching { download(imageUrl, file) }.getOrDefault(false)
        if (!ok) return@withContext Result.retry()

        // Cache-busting query so the Compose loader (keyed on the URI string) reloads
        // the new image even though the file path is stable across days.
        val uri = Uri.fromFile(file).buildUpon()
            .appendQueryParameter("v", System.currentTimeMillis().toString())
            .build()
            .toString()
        settings.setBingImage(uri)
        Result.success()
    }

    private fun fetchText(url: String): String =
        openConnection(url).run {
            try {
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }

    private fun download(url: String, dest: File): Boolean {
        val conn = openConnection(url)
        return try {
            if (conn.responseCode !in 200..299) return false
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (tmp.length() <= 0L) {
                tmp.delete()
                false
            } else {
                // Atomic swap into place; fall back to copy if rename across the same
                // dir is refused (some FS/ROMs reject renaming over an existing file).
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                true
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile",
            )
        }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_bing_wallpaper"
        private const val UNIQUE_NOW = "tileshell_bing_wallpaper_now"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Ensure the daily periodic refresh is enqueued (idempotent, KEEP) and run an
         * immediate one-off so a freshly enabled Bing wallpaper appears without waiting
         * a day. Safe to call on every app start while Bing is on.
         */
        fun ensureScheduled(context: Context) {
            val wm = androidx.work.WorkManager.getInstance(context.applicationContext)
            wm.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BingWallpaperWorker>(24, TimeUnit.HOURS)
                    .setConstraints(networkConstraint)
                    .build(),
            )
            wm.enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<BingWallpaperWorker>()
                    .setConstraints(networkConstraint)
                    .build(),
            )
        }

        /** Force an immediate download now (e.g. just after the user enables Bing). */
        fun refreshNow(context: Context) {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_NOW,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<BingWallpaperWorker>()
                        .setConstraints(networkConstraint)
                        .build(),
                )
        }

        /** Cancel all Bing work (when the user turns the wallpaper off). */
        fun cancel(context: Context) {
            val wm = androidx.work.WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(UNIQUE_PERIODIC)
            wm.cancelUniqueWork(UNIQUE_NOW)
        }
    }
}
