package com.tileshell.core.data

import android.content.Context

/** Minimum time since first launch before the rating prompt is eligible at all. */
const val RATING_PROMPT_MIN_AGE_MS = 3L * 24 * 60 * 60 * 1000

/**
 * Minimum time between one "should we ask?" check window and the next,
 * whether or not that window's roll actually shows the dialog.
 */
const val RATING_PROMPT_INTERVAL_MS = 5L * 24 * 60 * 60 * 1000

/** Chance a check window actually shows the prompt, so it reads as occasional, not routine. */
const val RATING_PROMPT_CHANCE = 0.3f

/**
 * True once enough time has passed to open a new "should we ask?" check
 * window: the user hasn't already answered, first launch was long enough
 * ago, and the last window was at least [RATING_PROMPT_INTERVAL_MS] ago. Pure
 * so the interval math is unit-testable without the real clock.
 *
 * There is no "app open count" here — TileShell is the launcher itself, so
 * unlike a normal app it has no discrete launch events; it's simply resumed
 * whenever the user returns to Start. Callers should evaluate this on every
 * resume and, the moment a window opens, immediately record the attempt
 * (advance the "last asked" clock) regardless of what [rollShowsPrompt] then
 * decides — a launcher resumes many times a day, and re-rolling on every one
 * of them until a roll finally hits would collapse the interval down to
 * "first resume after the window opens," not a real multi-day gap.
 */
fun isRatingPromptCheckWindowOpen(
    nowMs: Long,
    firstLaunchMs: Long,
    hasResponded: Boolean,
    lastAskedMs: Long,
): Boolean {
    if (hasResponded) return false
    if (nowMs - firstLaunchMs < RATING_PROMPT_MIN_AGE_MS) return false
    return nowMs - lastAskedMs >= RATING_PROMPT_INTERVAL_MS
}

/** Whether this check window's random draw actually shows the prompt. [roll] is 0f..1f. */
fun rollShowsPrompt(roll: Float): Boolean = roll < RATING_PROMPT_CHANCE

/**
 * Tracks state for the occasional "enjoying tileshell?" prompt shown on
 * Start: whether the user has already responded (asked at most once ever,
 * whichever way they answer), when the app was first launched, and when the
 * last check window was opened (so resuming the launcher many times a day
 * can't turn one random roll into several).
 */
object RatingPromptPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_FIRST_LAUNCH_MS = "rating_first_launch_ms"
    private const val KEY_RESPONDED = "rating_responded"
    private const val KEY_LAST_ASKED_MS = "rating_last_asked_ms"

    /** Seeds the first-launch timestamp once; a no-op on every later call. */
    fun ensureFirstLaunchSeeded(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_FIRST_LAUNCH_MS)) {
            p.edit().putLong(KEY_FIRST_LAUNCH_MS, System.currentTimeMillis()).apply()
        }
    }

    fun firstLaunchMs(context: Context): Long = prefs(context).getLong(KEY_FIRST_LAUNCH_MS, 0L)

    fun hasResponded(context: Context): Boolean = prefs(context).getBoolean(KEY_RESPONDED, false)

    fun markResponded(context: Context) {
        prefs(context).edit().putBoolean(KEY_RESPONDED, true).apply()
    }

    fun lastAskedMs(context: Context): Long = prefs(context).getLong(KEY_LAST_ASKED_MS, 0L)

    fun markAsked(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_ASKED_MS, System.currentTimeMillis()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
