package com.tileshell.core.data

import android.content.Context

/**
 * Remembers when the glance page's news feed was last actually looked at, so the
 * background refresh can stop re-downloading every subscribed RSS feed on a
 * 30-minute cadence for a page nobody has opened in hours.
 *
 * Measured motivation (battery diagnosis on a real device, 7h41m on battery):
 * TileShell's own drain was 145 mAh, 65.8 mAh of it `mobile_radio` — 63 MB
 * received, a large share of it 15 subscribed feeds × 48 refresh cycles a day,
 * fetched whether or not the feed page was ever shown. Same plain-
 * `SharedPreferences` shape (and file) as [StepsPrefs]/[CachedScreenshotPrefs]:
 * one long doesn't need DataStore.
 */
object FeedUsagePrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_FEED_LAST_OPENED = "feed_last_opened_at"

    /** Called when the feed page actually becomes the visible page, not merely composed. */
    fun markOpened(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_FEED_LAST_OPENED, nowMillis).apply()
    }

    /** 0 when the feed page has never been opened on this install. */
    fun lastOpenedAtMillis(context: Context): Long =
        prefs(context).getLong(KEY_FEED_LAST_OPENED, 0L)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** How long the feed page can go unopened before its periodic refresh stops fetching. */
const val FEED_IDLE_AFTER_MS: Long = 6 * 60 * 60 * 1000L

/**
 * Whether a *periodic* feed refresh should skip its network work entirely because
 * the feed page hasn't been opened recently. Pure, so the precedence is
 * unit-testable.
 *
 * A [lastOpenedAtMillis] of 0 (never opened) skips too — the very first
 * `ensureScheduled` one-off has already populated the cache once by then, and a
 * page that has never been looked at doesn't earn a 48-times-a-day download. This
 * only ever gates the periodic tick: every one-off path (placement, the manual
 * "refresh" action, and the refresh the feed page fires when it's opened stale)
 * passes force and is never skipped, so opening the feed always shows current
 * content within seconds of the open.
 *
 * A clock that has moved backwards (manual time change, timezone/NTP correction)
 * reads as "opened in the future" — treated as recent rather than idle, so a
 * backwards jump can never silently freeze the feed until the clock catches up.
 */
fun shouldSkipIdleFeedRefresh(
    nowMillis: Long,
    lastOpenedAtMillis: Long,
    idleAfterMillis: Long = FEED_IDLE_AFTER_MS,
): Boolean {
    if (lastOpenedAtMillis > nowMillis) return false
    return nowMillis - lastOpenedAtMillis >= idleAfterMillis
}
