package com.tileshell.feature.livetiles

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Maximum number of articles kept in the cache after a merge. */
const val FEED_ARTICLE_CAP = 40

/**
 * Per-feed ceiling applied before the global merge (FR — multi-region selection):
 * without this, a handful of very-frequently-posting sources (e.g. India's 10 default
 * feeds) can supply more than [FEED_ARTICLE_CAP] recent articles on their own, crowding
 * out every other enabled source/region entirely before the global cap is even
 * reached — confirmed live: selecting India + UK + US left the cache 39/40 Indian
 * articles, 1 US, 0 UK, even though the UK feed fetched fine. Capping each feed's own
 * contribution first guarantees every enabled source gets a chance to place.
 */
const val FEED_PER_SOURCE_CAP = 8

/**
 * Merges per-feed article lists into one feed: each feed's own list is first sorted
 * newest-first and truncated to [perSourceCap] (see [FEED_PER_SOURCE_CAP]) so no
 * single prolific source can crowd out the others, then the combined list is
 * de-duplicated by link (falling back to title when a link is missing), sorted
 * newest-first again, and capped at [cap]. Pure so the ordering/dedup/fairness is
 * unit-testable.
 */
fun mergeFeedArticles(
    perFeed: List<List<FeedArticle>>,
    cap: Int = FEED_ARTICLE_CAP,
    perSourceCap: Int = FEED_PER_SOURCE_CAP,
): List<FeedArticle> {
    val seen = HashSet<String>()
    val merged = ArrayList<FeedArticle>()
    perFeed.forEach { feedArticles ->
        feedArticles.sortedByDescending { it.publishedAtMillis }.take(perSourceCap).forEach { a ->
            val key = a.link.ifBlank { a.title }
            if (seen.add(key)) merged.add(a)
        }
    }
    return merged.sortedByDescending { it.publishedAtMillis }.take(cap)
}

/**
 * Periodic background refresh for the left feed's discover section: fetches every
 * enabled [FeedSource], parses each (RSS or Atom), merges/sorts/caps the articles,
 * and writes them to [FeedStore]. A dead or malformed feed contributes nothing and
 * is skipped (the rest still update). Succeeds with no enabled feeds (cache stays).
 * Retries only when every fetch failed, so the feed keeps its last good articles.
 */
class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = FeedStore.create(applicationContext)
        val sources = store.read().sources.filter { it.enabled }
        // No enabled feeds → clear the cache so disabled content stops showing.
        if (sources.isEmpty()) {
            store.setArticles(emptyList())
            return Result.success()
        }

        var anySucceeded = false
        val perFeed = sources.map { source ->
            val body = httpGetText(source.url)
            if (body != null) {
                anySucceeded = true
                parseFeed(body, source.name)
            } else {
                emptyList()
            }
        }
        if (!anySucceeded) return Result.retry()
        store.setArticles(mergeFeedArticles(perFeed))
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_feed_refresh"
        private const val UNIQUE_NOW = "tileshell_feed_refresh_now"

        // Require a network connection for the background periodic refresh so the
        // worker is not woken up on airplane mode / offline to fail and retry.
        private val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Ensures the ≥30-min periodic refresh is enqueued and kicks an immediate
         * one-off so a freshly shown feed page does not wait a full period.
         * Idempotent (KEEP) — safe to call every time the feed page appears.
         */
        fun ensureScheduled(context: Context) {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FeedRefreshWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(periodicConstraints)
                    .build(),
            )
            wm.enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FeedRefreshWorker>().build(),
            )
        }

        /** Forces a one-off refresh now (e.g. just after the feed list is edited). */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FeedRefreshWorker>().build(),
            )
        }
    }
}

/** Best-effort GET returning the body text, or null on any failure/non-200. */
private suspend fun httpGetText(url: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TileShell/1.0")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
