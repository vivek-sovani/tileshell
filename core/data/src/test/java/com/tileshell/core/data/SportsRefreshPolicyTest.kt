package com.tileshell.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsRefreshPolicyTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `never fetched before means fetch`() {
        assertTrue(shouldFetchSports(null, null, null, now))
    }

    @Test
    fun `a live match always refreshes`() {
        // Even one second after the last fetch — scores change minute to minute.
        assertTrue(shouldFetchSports(SPORTS_STATE_LIVE, null, now - 1_000, now))
    }

    @Test
    fun `a finished match does not refresh again immediately`() {
        assertFalse(shouldFetchSports(SPORTS_STATE_FINAL, null, now - 60_000, now))
    }

    @Test
    fun `a finished match refreshes once the idle interval has passed`() {
        assertTrue(shouldFetchSports(SPORTS_STATE_FINAL, null, now - SPORTS_IDLE_REFRESH_MS, now))
        assertFalse(shouldFetchSports(SPORTS_STATE_FINAL, null, now - SPORTS_IDLE_REFRESH_MS + 1, now))
    }

    @Test
    fun `an upcoming match stays quiet while kickoff is far away`() {
        val kickoff = now + 5L * 60 * 60 * 1000
        assertFalse(shouldFetchSports("pre", kickoff, now - 60_000, now))
    }

    @Test
    fun `an upcoming match wakes up shortly before kickoff`() {
        val kickoff = now + SPORTS_PREGAME_WAKE_MS - 1
        assertTrue(shouldFetchSports("pre", kickoff, now - 60_000, now))
    }

    @Test
    fun `a kickoff already in the past means fetch — the match may be live now`() {
        assertTrue(shouldFetchSports("pre", now - 60_000, now - 60_000, now))
    }

    @Test
    fun `unknown state with no kickoff falls back to the idle interval`() {
        assertFalse(shouldFetchSports(null, null, now - 60_000, now))
        assertTrue(shouldFetchSports(null, null, now - SPORTS_IDLE_REFRESH_MS, now))
    }

    @Test
    fun `a clock that moved backwards forces a fetch rather than sleeping forever`() {
        // Timezone change, manual clock set, or a restored backup.
        assertTrue(shouldFetchSports(SPORTS_STATE_FINAL, null, now + 60_000, now))
    }
}
