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
import androidx.work.workDataOf
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

/** One Bing image-of-the-day entry, resolved to absolute URLs ready to load. */
data class BingImage(
    /** Short human label for the day, e.g. "jun 26" (lowercase to match the UI). */
    val date: String,
    /** Title or copyright caption (may be empty). */
    val title: String,
    /** Full-resolution image URL (used as the wallpaper when picked). */
    val fullUrl: String,
    /** Small preview URL for the history grid. */
    val thumbUrl: String,
)

/**
 * Parses the `images` array of a Bing `HPImageArchive.aspx?format=js` response into
 * [BingImage]s, resolving each relative `url`/`urlbase` against the Bing host. Pure
 * (no network) so it is unit-testable; tolerant — a malformed blob yields an empty list.
 */
fun parseBingImages(json: String): List<BingImage> = runCatching {
    val arr = JSONObject(json).optJSONArray("images") ?: return@runCatching emptyList()
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val urlPath = o.optString("url").ifBlank { null } ?: return@mapNotNull null
        val full = if (urlPath.startsWith("http")) urlPath else BING_HOST + urlPath
        val base = o.optString("urlbase").ifBlank { null }
        val thumb = if (base != null) "$BING_HOST${base}_400x240.jpg" else full
        val title = o.optString("title").ifBlank { o.optString("copyright") }
        BingImage(
            date = bingDateLabel(o.optString("startdate")),
            title = title,
            fullUrl = full,
            thumbUrl = thumb,
        )
    }
}.getOrDefault(emptyList())

/**
 * Builds the absolute URL of the current Bing image of the day. Kept for the daily
 * worker and its existing tests; delegates to [parseBingImages]. Null when the JSON
 * has no usable image.
 */
fun parseBingImageUrl(json: String): String? = parseBingImages(json).firstOrNull()?.fullUrl

/** Formats a `yyyymmdd` Bing `startdate` as "mon d" (e.g. "jun 26"); echoes the input on any error. */
internal fun bingDateLabel(startdate: String): String = runCatching {
    if (startdate.length != 8) return startdate
    val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    val month = startdate.substring(4, 6).toInt()
    val day = startdate.substring(6, 8).toInt()
    "${months[month - 1]} $day"
}.getOrDefault(startdate)

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
 * Fetches the most recent Bing images (newest first, de-duplicated, capped at 10).
 * Bing serves at most 8 per request, so two pages (idx 0 and 8) are merged; the
 * archive realistically holds ~8 days, so fewer may come back. Returns an empty list
 * on a total network failure. Used by the in-app history viewer.
 */
suspend fun fetchBingImages(market: String = bingMarket()): List<BingImage> = withContext(Dispatchers.IO) {
    val out = LinkedHashMap<String, BingImage>()
    for (idx in intArrayOf(0, 8)) {
        val url = "$BING_HOST/HPImageArchive.aspx?format=js&idx=$idx&n=8&mkt=$market"
        val json = runCatching { bingFetchText(url) }.getOrNull() ?: continue
        parseBingImages(json).forEach { img -> out.putIfAbsent(img.fullUrl, img) }
        if (out.size >= 10) break
    }
    out.values.take(10)
}

/**
 * Daily refresh of the Microsoft Bing wallpaper (opt-in via personalize). With no
 * input it fetches the image of the day, downloads the JPEG into app storage, and
 * writes its file URI into [SettingsRepository.setBingImage] so the existing
 * custom-wallpaper render path picks it up — preserving the user's framing across
 * days. Given an [KEY_IMAGE_URL] input it instead pins that specific (historical)
 * image via [SettingsRepository.setPinnedBingImage], staying in Bing mode (the
 * history viewer is only reachable from within it) rather than switching to a
 * plain fixed photo. Retries on any network failure so the tile keeps its last
 * good image.
 */
class BingWallpaperWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsRepository.create(applicationContext)
        val file = File(applicationContext.filesDir, "bing_wallpaper.jpg")

        // "Pin this day" path: download the requested image and show it now, staying
        // in Bing mode (setPinnedBingImage) rather than switching to a plain fixed
        // photo — the history viewer is only reachable from within Bing mode.
        inputData.getString(KEY_IMAGE_URL)?.let { pinned ->
            if (!runCatching { bingDownload(pinned, file) }.getOrDefault(false)) {
                return@withContext Result.retry()
            }
            settings.setPinnedBingImage(versionedUri(file))
            return@withContext Result.success()
        }

        // Daily path: respect a since-toggled-off state before spending the network.
        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced && !settings.settings.first().bingWallpaper) {
            android.util.Log.i(TAG, "daily: bing is off, skipped")
            return@withContext Result.success(workDataOf(KEY_OUTCOME to OUTCOME_OFF))
        }
        val metaUrl = "$BING_HOST/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=${bingMarket()}"
        val json = runCatching { bingFetchText(metaUrl) }.getOrNull()
        val imageUrl = json?.let { parseBingImageUrl(it) }
        if (imageUrl == null) {
            android.util.Log.i(TAG, "daily: couldn't read today's image")
            return@withContext if (forced) Result.failure(workDataOf(KEY_OUTCOME to OUTCOME_FAILED)) else Result.retry()
        }
        val prefs = applicationContext.getSharedPreferences("tileshell.prefs", Context.MODE_PRIVATE)
        val same = prefs.getString(PREF_LAST_URL, null) == imageUrl && file.exists()
        prefs.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()
        // Today's picture is already on the phone and in use: no download, no settings write, no wallpaper reload.
        if (same && !forced) {
            android.util.Log.i(TAG, "daily: same image as before, nothing to do")
            return@withContext Result.success(workDataOf(KEY_OUTCOME to OUTCOME_SAME))
        }
        if (!runCatching { bingDownload(imageUrl, file) }.getOrDefault(false)) {
            android.util.Log.i(TAG, "daily: download failed")
            return@withContext if (forced) Result.failure(workDataOf(KEY_OUTCOME to OUTCOME_FAILED)) else Result.retry()
        }
        prefs.edit().putString(PREF_LAST_URL, imageUrl).apply()
        // A refresh turns daily mode on, so it also works after an earlier day was picked.
        if (forced) settings.setBingWallpaper(true)
        settings.setBingImage(versionedUri(file))
        android.util.Log.i(TAG, "daily: set ${if (same) "same image as before" else "new image"}")
        Result.success(workDataOf(KEY_OUTCOME to if (same) OUTCOME_SAME else OUTCOME_NEW))
    }

    companion object {
        const val KEY_IMAGE_URL = "image_url"
        private const val KEY_FORCE = "force"
        const val KEY_OUTCOME = "outcome"
        const val OUTCOME_NEW = "new"
        const val OUTCOME_SAME = "same"
        const val OUTCOME_OFF = "off"
        const val OUTCOME_FAILED = "failed"
        private const val PREF_LAST_URL = "bing_last_image_url"
        private const val PREF_LAST_CHECK = "bing_last_check"
        private const val RECHECK_MS = 6 * 60 * 60 * 1000L
        private const val TAG = "TileShellBing"

        private const val UNIQUE_PERIODIC = "tileshell_bing_wallpaper"
        private const val UNIQUE_NOW = "tileshell_bing_wallpaper_now"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** A `file://` URI for [file] with a cache-busting `?v=` so the Compose loader reloads it. */
        private fun versionedUri(file: File): String =
            Uri.fromFile(file).buildUpon()
                .appendQueryParameter("v", System.currentTimeMillis().toString())
                .build()
                .toString()

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
            // Called on every launch while Bing is on: only look again if the last check is old, or there never
            // was one (just switched on). The daily job covers the rest.
            val prefs = context.applicationContext.getSharedPreferences("tileshell.prefs", Context.MODE_PRIVATE)
            if (System.currentTimeMillis() - prefs.getLong(PREF_LAST_CHECK, 0L) < RECHECK_MS) return
            wm.enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<BingWallpaperWorker>()
                    .setConstraints(networkConstraint)
                    .build(),
            )
        }

        /**
         * "refresh daily wallpaper": fetches today's image now, turning daily mode
         * on if needed, and calls [onDone] with an [OUTCOME_NEW]/[OUTCOME_SAME]/
         * [OUTCOME_FAILED] once it finishes (on the main thread).
         */
        fun refreshFromUser(context: Context, onDone: (String) -> Unit) {
            val request = OneTimeWorkRequestBuilder<BingWallpaperWorker>()
                .setInputData(workDataOf(KEY_FORCE to true))
                .build()
            val wm = androidx.work.WorkManager.getInstance(context.applicationContext)
            wm.enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request)
            val live = wm.getWorkInfoByIdLiveData(request.id)
            val observer = object : androidx.lifecycle.Observer<androidx.work.WorkInfo?> {
                override fun onChanged(value: androidx.work.WorkInfo?) {
                    if (value == null || !value.state.isFinished) return
                    live.removeObserver(this)
                    onDone(value.outputData.getString(KEY_OUTCOME) ?: OUTCOME_FAILED)
                }
            }
            live.observeForever(observer)
        }

        /** Force an immediate download of today's image (e.g. just after the user enables Bing). */
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

        /** Pin a specific (e.g. historical) Bing image as the wallpaper, staying in Bing mode. */
        fun applyImage(context: Context, imageUrl: String) {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_NOW,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<BingWallpaperWorker>()
                        .setConstraints(networkConstraint)
                        .setInputData(workDataOf(KEY_IMAGE_URL to imageUrl))
                        .build(),
                )
        }

        /** Cancel all Bing work (when the user turns the wallpaper off). */
        fun cancel(context: Context) {
            val wm = androidx.work.WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(UNIQUE_PERIODIC)
            wm.cancelUniqueWork(UNIQUE_NOW)
            // So switching Bing on again looks at once instead of waiting out the recheck window.
            context.applicationContext.getSharedPreferences("tileshell.prefs", Context.MODE_PRIVATE).edit().remove(PREF_LAST_CHECK).apply()
        }
    }
}

private fun openBingConnection(url: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile",
        )
    }

/** GETs [url] and returns the body as text. Throws on failure (callers wrap in runCatching). */
private fun bingFetchText(url: String): String =
    openBingConnection(url).run {
        try {
            inputStream.bufferedReader().use { it.readText() }
        } finally {
            disconnect()
        }
    }

/** Downloads [url] into [dest] via a temp file. Returns false on a non-2xx response or empty body. */
/**
 * Downloads a picked (earlier day's) Bing image for the reframe step before it
 * is set, as `filesDir/bing_pick_<time>.jpg`, and returns its `file://` URI (null
 * on failure). Older picks are deleted, except [keepUri] (the wallpaper in use).
 */
suspend fun downloadBingPick(context: android.content.Context, imageUrl: String, keepUri: String?): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val dir = context.filesDir
        val keepName = keepUri?.let { runCatching { Uri.parse(it).lastPathSegment }.getOrNull() }
        dir.listFiles { f -> f.name.startsWith("bing_pick_") && f.name != keepName }?.forEach { it.delete() }
        val file = File(dir, "bing_pick_${System.currentTimeMillis()}.jpg")
        if (runCatching { bingDownload(imageUrl, file) }.getOrDefault(false)) Uri.fromFile(file).toString() else null
    }

private fun bingDownload(url: String, dest: File): Boolean {
    val conn = openBingConnection(url)
    return try {
        if (conn.responseCode !in 200..299) return false
        // Unique per download: the daily job's retry and a user refresh can run at
        // once, and sharing one temp file corrupted the image.
        val tmp = File(dest.parentFile, dest.name + ".${System.nanoTime()}.tmp")
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
