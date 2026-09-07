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
import androidx.work.workDataOf
import com.tileshell.core.data.FeedUsagePrefs
import com.tileshell.core.data.shouldSkipIdleFeedRefresh
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
 * Retries only when every fetch genuinely failed, so the feed keeps its last good
 * articles.
 *
 * Two battery measures, both added after a real on-device diagnosis found this
 * worker to be the largest single contributor to TileShell's own drain (145 mAh
 * over 7h41m, 65.8 mAh of it `mobile_radio`, 63 MB received — 15 subscribed feeds
 * re-downloaded in full 48 times a day whether or not the feed page was ever
 * opened):
 *  - **Conditional GET.** Each feed's `ETag`/`Last-Modified` from the previous
 *    cycle is sent back ([FeedData.validators]); a feed with nothing new answers
 *    `304 Not Modified` and its already-cached articles are reused verbatim
 *    (matched by [FeedArticle.feedUrl]) instead of re-downloading its body.
 *  - **Idle skip.** A *periodic* tick does nothing at all once the feed page has
 *    gone [com.tileshell.core.data.FEED_IDLE_AFTER_MS] unopened (see
 *    [shouldSkipIdleFeedRefresh]). Every one-off path passes [KEY_FORCE] and is
 *    never skipped, so opening the page still refreshes it immediately.
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
        val forced = inputData.getBoolean(KEY_FORCE, false)
        // A periodic tick for a page nobody has opened in hours does nothing —
        // no requests, no store write, so the cache it would have replaced stays
        // exactly as it is until the page is next opened (which forces a refresh
        // of its own). See shouldSkipIdleFeedRefresh.
        if (!forced &&
            shouldSkipIdleFeedRefresh(
                nowMillis = System.currentTimeMillis(),
                lastOpenedAtMillis = FeedUsagePrefs.lastOpenedAtMillis(applicationContext),
            )
        ) {
            return Result.success()
        }

        val store = FeedStore.create(applicationContext)
        val current = store.read()
        val sources = current.sources.filter { it.enabled }
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
        val cachedByFeed = current.articles.groupBy { it.feedUrl }
        val results = coroutineScope {
            val gate = Semaphore(FETCH_CONCURRENCY)
            sources.map { source ->
                async {
                    // Only send a validator when this feed's own cached articles
                    // are actually attributable to it — otherwise a 304 would
                    // leave nothing to reuse and silently drop the feed for a
                    // cycle (the case for a cache written before FeedArticle
                    // recorded its feedUrl).
                    val reusable = cachedByFeed[source.url].orEmpty()
                    val validator = current.validators[source.url]?.takeIf { reusable.isNotEmpty() }
                    val fetched = gate.withPermit { conditionalGet(source.url, validator) }
                    source to when (fetched) {
                        is FeedFetch.NotModified -> FeedOutcome(articles = reusable, validator = validator)
                        is FeedFetch.Body -> FeedOutcome(
                            articles = parseFeed(fetched.text, source.name, source.url),
                            validator = fetched.validator.takeIf { !it.isEmpty },
                        )
                        FeedFetch.Failed -> null
                    }
                }
            }.awaitAll()
        }
        // A null outcome is a failed fetch, distinct from a feed that parsed to
        // zero articles — only a total failure should retry, so the feed keeps
        // its last good articles rather than being emptied by one bad network
        // moment. A 304 counts as a success, not a failure: nothing was wrong,
        // there was simply nothing new.
        if (results.none { (_, outcome) -> outcome != null }) return Result.retry()
        store.setArticles(
            articles = mergeFeedArticles(results.map { (_, outcome) -> outcome?.articles ?: emptyList() }),
            validators = results.mapNotNull { (source, outcome) ->
                outcome?.validator?.let { source.url to it }
            }.toMap(),
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_feed_refresh"
        private const val UNIQUE_NOW = "tileshell_feed_refresh_now"

        /**
         * Set on every one-off request — placement, a feed-list edit, the manual
         * "refresh" action, and the refresh the feed page fires when it's opened
         * with a stale cache. Only the periodic tick leaves it false, so only the
         * periodic tick can be skipped as idle (see [shouldSkipIdleFeedRefresh]).
         * Same shape as `StockWidgetRefreshWorker`'s own force flag.
         */
        private const val KEY_FORCE = "force"

        private fun forcedOneOff() = OneTimeWorkRequestBuilder<FeedRefreshWorker>()
            .setInputData(workDataOf(KEY_FORCE to true))
            .build()

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
                forcedOneOff(),
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
                forcedOneOff(),
            )
        }
    }
}

/** What one feed's own refresh produced this cycle — null for a failure. */
private data class FeedOutcome(val articles: List<FeedArticle>, val validator: FeedValidator?)

/** The three ways a conditional feed GET can end. */
private sealed interface FeedFetch {
    /** 200 — a fresh body, plus whatever validators to send back next time. */
    data class Body(val text: String, val validator: FeedValidator) : FeedFetch

    /** 304 — nothing new; the caller reuses its cached articles for this feed. */
    data object NotModified : FeedFetch

    /** Any failure, timeout, or other status. */
    data object Failed : FeedFetch
}

/**
 * Best-effort conditional GET of one feed. When [validator] carries an `ETag` or
 * `Last-Modified` from the last successful fetch, they're sent as
 * `If-None-Match`/`If-Modified-Since` so an unchanged feed answers 304 with no
 * body at all — the whole point, since a 30-minute cadence over 15 feeds
 * otherwise re-downloads every article list 48 times a day (see
 * [FeedRefreshWorker]'s own doc comment for the measured cost).
 */
private suspend fun conditionalGet(url: String, validator: FeedValidator?): FeedFetch =
    withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "TileShell/1.0")
                validator?.etag?.takeIf { it.isNotBlank() }?.let { setRequestProperty("If-None-Match", it) }
                validator?.lastModified?.takeIf { it.isNotBlank() }
                    ?.let { setRequestProperty("If-Modified-Since", it) }
            }
            try {
                when (conn.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> FeedFetch.NotModified
                    HttpURLConnection.HTTP_OK -> FeedFetch.Body(
                        text = conn.inputStream.use { it.readBytes().decodeToString() },
                        validator = FeedValidator(
                            etag = conn.getHeaderField("ETag").orEmpty(),
                            lastModified = conn.getHeaderField("Last-Modified").orEmpty(),
                        ),
                    )
                    else -> FeedFetch.Failed
                }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(FeedFetch.Failed)
    }
