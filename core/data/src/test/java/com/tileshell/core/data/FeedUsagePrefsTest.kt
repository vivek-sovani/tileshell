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
}
