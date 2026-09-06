package com.tileshell.core.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * A short-lived, process-wide memo for market data fetches (quotes and
 * sparklines) that de-duplicates identical requests.
 *
 * Three independent code paths ask for the same numbers, and none of them knew
 * about the others: the Start-screen live tile's own poll, the glance card, and
 * the home-screen widget's refresh worker. Worse, a worker's `pushAll` loops
 * widget *ids* and fetches inside the loop keyed only by id — so two stock
 * widgets tracking the same symbol issued two identical HTTP requests in the
 * same tick, and a category basket re-fetched every member per instance.
 *
 * Rather than thread a cache through all three call sites, this sits at the
 * bottom, inside the fetch functions themselves, so every caller benefits
 * without changing shape.
 *
 * Two mechanisms, both needed:
 *  - a **TTL cache**, so a request repeated shortly after a previous one reuses
 *    the result. [TTL_MS] is deliberately shorter than any caller's own poll
 *    interval, so nobody ever sees data older than they would have anyway.
 *  - **in-flight de-duplication** via a per-key [Mutex]. A TTL cache alone
 *    doesn't help when requests are *concurrent* — which is exactly the
 *    widget-worker case — because both would miss the empty cache and both
 *    fetch. Serializing per key means the second caller waits briefly and then
 *    reads the entry the first one just wrote.
 *
 * Values are cached by key including the request kind, so a quote and a
 * sparkline for the same symbol never collide. A null/empty result is cached
 * too: a symbol Yahoo has no data for shouldn't be retried on every instance
 * within the same tick.
 */
object QuoteCache {

    /**
     * Deliberately well under the fastest caller's cadence — the in-app tile
     * polls at 60s and widgets at 15 minutes — so this only ever collapses
     * duplicates, never delays a refresh anyone actually asked for.
     */
    const val TTL_MS = 45_000L

    private class Entry(val value: Any?, val atMillis: Long)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Returns the cached value for [key] if it is younger than [TTL_MS],
     * otherwise runs [fetch] (with concurrent callers for the same key
     * serialized) and caches the result.
     */
    suspend fun <T> get(key: String, nowMillis: Long = System.currentTimeMillis(), fetch: suspend () -> T): T {
        fresh<T>(key, nowMillis)?.let { return it.value }
        val lock = locks.computeIfAbsent(key) { Mutex() }
        return lock.withLock {
            // Re-check: another caller may have filled it while we waited.
            // Uses the caller's own [nowMillis], not a fresh clock read — an
            // injected clock has to apply here too, or the re-check silently
            // reverts to wall time and an "expired" lookup finds the entry
            // fresh again.
            fresh<T>(key, nowMillis)?.let { return@withLock it.value }
            val result = fetch()
            entries[key] = Entry(result, nowMillis)
            if (entries.size > MAX_ENTRIES) prune(nowMillis)
            result
        }
    }

    /** Boxed so a legitimately-null cached value is distinguishable from a miss. */
    private class Hit<T>(val value: T)

    @Suppress("UNCHECKED_CAST")
    private fun <T> fresh(key: String, nowMillis: Long): Hit<T>? {
        val e = entries[key] ?: return null
        // A clock moving backwards (timezone change, manual set) must not pin a
        // stale entry as permanently "fresh".
        val age = nowMillis - e.atMillis
        if (age !in 0 until TTL_MS) return null
        return Hit(e.value as T)
    }

    private fun prune(nowMillis: Long) {
        entries.entries.removeAll { nowMillis - it.value.atMillis >= TTL_MS }
        // Still oversized (many distinct symbols inside one TTL window) — drop
        // everything rather than track access order; it all expires in 45s
        // regardless, and this is a bound, not a working set.
        if (entries.size > MAX_ENTRIES) {
            entries.clear()
            locks.clear()
        }
    }

    private const val MAX_ENTRIES = 256

    /** For tests. */
    fun clear() {
        entries.clear()
        locks.clear()
    }
}
