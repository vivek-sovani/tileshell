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

/**
 * The full gate for a *periodic* feed tick: skip unless the screen is actually on
 * **and** the page has been opened recently ([shouldSkipIdleFeedRefresh]).
 *
 * The screen term exists because the idle window alone still let a single evening
 * glance at the feed fund six more hours of downloads through the night. Measured
 * live against this install's own 10 enabled feeds: one refresh cycle is 777 KB
 * (Gadgets 360 186 KB, Google News 167 KB, NDTV Movies 129 KB, ESPNcricinfo 89 KB,
 * the rest 26–47 KB each), so the 30-minute cadence costs 35.5 MB/day while the
 * window is open, and 8.8 MB of that lands between "looked at the feed at 11pm"
 * and "woke up" — news nobody can read while asleep.
 *
 * Conditional GET does not rescue this in practice, which is why the cadence
 * itself had to change: Google News serves `cache-control: no-store` with neither
 * `ETag` nor `Last-Modified`, so it can never answer 304 at all; and the feeds
 * that *do* supply validators (TOI, The Hindu, NDTV) were verified to still answer
 * `200` with a full body when replayed with their stored validator, because a news
 * feed genuinely has new items 30 minutes later. Revalidation only wins for a
 * source that is quiet between ticks, which a news feed is not.
 *
 * Screen-off is deliberately the signal rather than "device idle"/Doze: it needs
 * no permission, flips instantly, and matches the actual question — is there a
 * person who could be about to open this page. Nothing here delays a refresh the
 * user asked for: every one-off path (placement, the manual "refresh" action, and
 * the open-while-stale refresh) passes force and bypasses this entirely, so
 * opening the feed still fetches immediately, screen-off gate or not.
 */
fun shouldSkipPeriodicFeedRefresh(
    nowMillis: Long,
    lastOpenedAtMillis: Long,
    screenInteractive: Boolean,
    idleAfterMillis: Long = FEED_IDLE_AFTER_MS,
): Boolean =
    !screenInteractive ||
        shouldSkipIdleFeedRefresh(nowMillis, lastOpenedAtMillis, idleAfterMillis)
