package com.tileshell.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingPromptTest {

    private val day = 24L * 60 * 60 * 1000

    // A realistic epoch-millis anchor (not 0) — lastAskedMs defaults to 0L when
    // never set, and "nowMs - lastAskedMs" must stay a real-world huge number in
    // that case, exactly as it would for an actual System.currentTimeMillis().
    private val firstLaunch = 1_700_000_000_000L
    private val eligibleNow = firstLaunch + RATING_PROMPT_MIN_AGE_MS + day

    @Test
    fun `already responded never opens a window`() {
        assertFalse(
            isRatingPromptCheckWindowOpen(
                nowMs = eligibleNow,
                firstLaunchMs = firstLaunch,
                hasResponded = true,
                lastAskedMs = 0L,
            ),
        )
    }

    @Test
    fun `too soon after first launch does not open a window`() {
        assertFalse(
            isRatingPromptCheckWindowOpen(
                nowMs = firstLaunch + RATING_PROMPT_MIN_AGE_MS - 1,
                firstLaunchMs = firstLaunch,
                hasResponded = false,
                lastAskedMs = 0L,
            ),
        )
    }

    @Test
    fun `still within the interval since the last window does not open a new one`() {
        val lastAsked = eligibleNow - RATING_PROMPT_INTERVAL_MS + day
        assertFalse(
            isRatingPromptCheckWindowOpen(
                nowMs = eligibleNow,
                firstLaunchMs = firstLaunch,
                hasResponded = false,
                lastAskedMs = lastAsked,
            ),
        )
    }

    @Test
    fun `a fresh install with no prior ask ever is eligible once old enough`() {
        assertTrue(
            isRatingPromptCheckWindowOpen(
                nowMs = eligibleNow,
                firstLaunchMs = firstLaunch,
                hasResponded = false,
                lastAskedMs = 0L,
            ),
        )
    }

    @Test
    fun `a window reopens once the full interval has elapsed since the last one`() {
        val lastAsked = eligibleNow - RATING_PROMPT_INTERVAL_MS
        assertTrue(
            isRatingPromptCheckWindowOpen(
                nowMs = eligibleNow,
                firstLaunchMs = firstLaunch,
                hasResponded = false,
                lastAskedMs = lastAsked,
            ),
        )
    }

    @Test
    fun `roll below the chance threshold shows the prompt`() {
        assertTrue(rollShowsPrompt(roll = 0f))
    }

    @Test
    fun `roll at or above the chance threshold does not show the prompt`() {
        assertFalse(rollShowsPrompt(roll = RATING_PROMPT_CHANCE))
        assertFalse(rollShowsPrompt(roll = 1f))
    }
}
