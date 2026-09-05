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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import com.tileshell.core.data.settings.SettingsRepository

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
        // Second guard, independent of the cancel() the setting now triggers: a
        // job already enqueued by an older build (or one that outlived a failed
        // cancel) must not keep fetching after the user turned the feed off.
        if (!SettingsRepository.create(applicationContext).settings.first().feedEnabled) {
            return Result.success()
        }
        val store = FeedStore.create(applicationContext)
        val sources = store.read().sources.filter { it.enabled }
        // No enabled feeds → clear the cache so disabled content stops showing.
        if (sources.isEmpty()) {
            store.setArticles(emptyList())
            return Result.success()
        }

        // Fetched concurrently, not one after another. `sources.map { httpGetText(..) }`
        // over a suspend call is sequential, so with several regions enabled a
        // single cycle could issue 20-30 requests back to back, each with its own
        // 8s connect + 8s read timeout — minutes of continuous radio-on time for
        // work that fits in one short burst. FETCH_CONCURRENCY caps how many are
        // in flight so a large feed list can't open dozens of sockets at once.
        val perFeed = coroutineScope {
            val gate = Semaphore(FETCH_CONCURRENCY)
            sources.map { source ->
                async {
                    val body = gate.withPermit { httpGetText(source.url) }
                    if (body != null) parseFeed(body, source.name) else null
                }
            }.awaitAll()
        }
        // A null entry is a failed fetch, distinct from a feed that parsed to zero
        // articles — only a total failure should retry, so the feed keeps its last
        // good articles rather than being emptied by one bad network moment.
        if (perFeed.none { it != null }) return Result.retry()
        store.setArticles(mergeFeedArticles(perFeed.map { it ?: emptyList() }))
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_feed_refresh"
        private const val UNIQUE_NOW = "tileshell_feed_refresh_now"

        /** Max feeds fetched at once — one short radio burst without opening dozens of sockets. */
        private const val FETCH_CONCURRENCY = 5

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

        /**
         * Stops the periodic refresh. Must be called when the feed is turned off
         * in Personalize.
         *
         * Without this the worker was effectively permanent: [ensureScheduled] is
         * called the first time the feed page is ever opened, enqueues a `KEEP`
         * unique periodic job, and nothing anywhere cancelled it — no `cancel`
         * function even existed. Turning "feed" off afterwards left a 30-minute
         * background RSS fetch running for the life of the install, surviving
         * reboots via WorkManager, with no way for the user to stop it short of
         * clearing app data.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
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
