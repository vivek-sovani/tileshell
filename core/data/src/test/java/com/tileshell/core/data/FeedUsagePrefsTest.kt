package com.tileshell.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedUsagePrefsTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun `never opened (zero) is idle`() {
        assertTrue(shouldSkipIdleFeedRefresh(nowMillis = 10 * hour, lastOpenedAtMillis = 0L))
    }

    @Test
    fun `opened just now is not idle`() {
        val now = 10 * hour
        assertFalse(shouldSkipIdleFeedRefresh(nowMillis = now, lastOpenedAtMillis = now))
    }

    @Test
    fun `opened just under the idle window is not idle`() {
        val now = 10 * hour
        assertFalse(
            shouldSkipIdleFeedRefresh(nowMillis = now, lastOpenedAtMillis = now - FEED_IDLE_AFTER_MS + 1),
        )
    }

    @Test
    fun `opened exactly at the idle window boundary is idle`() {
        val now = 10 * hour
        assertTrue(
            shouldSkipIdleFeedRefresh(nowMillis = now, lastOpenedAtMillis = now - FEED_IDLE_AFTER_MS),
        )
    }

    @Test
    fun `well past the idle window is idle`() {
        val now = 10 * hour
        assertTrue(
            shouldSkipIdleFeedRefresh(nowMillis = now, lastOpenedAtMillis = now - FEED_IDLE_AFTER_MS - hour),
        )
    }

    @Test
    fun `a clock that jumped backwards reads as recent, not idle`() {
        // lastOpenedAtMillis in the future relative to nowMillis — a manual clock
        // change or timezone/NTP correction — must never freeze the feed until
        // the clock catches back up.
        val now = 10 * hour
        assertFalse(shouldSkipIdleFeedRefresh(nowMillis = now, lastOpenedAtMillis = now + hour))
    }

    @Test
    fun `a custom idle window is honoured`() {
        val now = 10 * hour
        assertFalse(shouldSkipIdleFeedRefresh(now, lastOpenedAtMillis = now - hour, idleAfterMillis = 2 * hour))
        assertTrue(shouldSkipIdleFeedRefresh(now, lastOpenedAtMillis = now - 2 * hour, idleAfterMillis = 2 * hour))
    }

    // ---- shouldRefreshFeedOnOpen (fetch on open when the cache is stale) ----

    @Test
    fun `opening with a cache older than the window refetches`() {
        val now = 10 * hour
        assertTrue(shouldRefreshFeedOnOpen(nowMillis = now, lastRefreshedAtMillis = now - hour))
    }

    @Test
    fun `opening again right after a refresh does not refetch`() {
        // Flicking back and forth to the page must not re-download every feed.
        val now = 10 * hour
        assertFalse(shouldRefreshFeedOnOpen(nowMillis = now, lastRefreshedAtMillis = now - 60_000L))
    }

    @Test
    fun `a never-fetched install refetches on first open`() {
        assertTrue(shouldRefreshFeedOnOpen(nowMillis = 10 * hour, lastRefreshedAtMillis = 0L))
    }

    @Test
    fun `exactly at the staleness boundary refetches`() {
        val now = 10 * hour
        assertTrue(
            shouldRefreshFeedOnOpen(now, lastRefreshedAtMillis = now - FEED_STALE_AFTER_MS),
        )
        assertFalse(
            shouldRefreshFeedOnOpen(now, lastRefreshedAtMillis = now - FEED_STALE_AFTER_MS + 1),
        )
    }

    @Test
    fun `a clock that jumped backwards reads as fresh, not stale`() {
        val now = 10 * hour
        assertFalse(shouldRefreshFeedOnOpen(nowMillis = now, lastRefreshedAtMillis = now + hour))
    }

    // ---- shouldSkipPeriodicFeedRefresh (screen-off gate on top of the idle window) ----

    @Test
    fun `a screen-off tick skips even when the page was just opened`() {
        // The case that motivated this: glance at the feed, lock the phone, and
        // the idle window alone would still fund 30-minute downloads all night.
        val now = 10 * hour
        assertTrue(
            shouldSkipPeriodicFeedRefresh(
                nowMillis = now,
                lastOpenedAtMillis = now - 60_000L,
                screenInteractive = false,
            ),
        )
    }

    @Test
    fun `a screen-on tick still refreshes when the page was opened recently`() {
        val now = 10 * hour
        assertFalse(
            shouldSkipPeriodicFeedRefresh(
                nowMillis = now,
                lastOpenedAtMillis = now - hour,
                screenInteractive = true,
            ),
        )
    }

    @Test
    fun `screen on does not override a page left unopened past the idle window`() {
        val now = 10 * hour
        assertTrue(
            shouldSkipPeriodicFeedRefresh(
                nowMillis = now,
                lastOpenedAtMillis = now - FEED_IDLE_AFTER_MS - hour,
                screenInteractive = true,
            ),
        )
    }
}
