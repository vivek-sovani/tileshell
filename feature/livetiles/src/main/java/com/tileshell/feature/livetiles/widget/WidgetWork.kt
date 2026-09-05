package com.tileshell.feature.livetiles.widget

import androidx.work.Constraints
import androidx.work.NetworkType
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Shared scheduling policy for the home-screen widgets' periodic refresh
 * workers. Every one of these refreshes is cosmetic — a widget showing a
 * slightly stale temperature or step count for one extra interval costs the
 * user nothing, so none of them justifies waking a device that is already
 * short on power.
 *
 * Before this existed each `ensureScheduled` built a bare
 * `PeriodicWorkRequestBuilder<T>(...).build()` with **no constraints at all** —
 * a regression against the older, pre-widget workers
 * ([com.tileshell.feature.livetiles.WeatherRefreshWorker],
 * `FeedRefreshWorker`, `BingWallpaperWorker`), which have always set
 * [NetworkType.CONNECTED] precisely so they don't burn a wakeup failing
 * offline. These helpers put the widget workers back on that footing.
 */
object WidgetWork {

    /**
     * For a widget refresh that reads only local state (sensors, settings,
     * date math, a cache someone else fills). No network requirement — one
     * would never be satisfied and the work would never run.
     *
     * [requiresBatteryNotLow] defaults true; the battery widget itself passes
     * false, since "your battery is low" is exactly when its reading matters
     * most and suppressing it would be perverse.
     */
    fun localConstraints(requiresBatteryNotLow: Boolean = true): Constraints =
        Constraints.Builder()
            .setRequiresBatteryNotLow(requiresBatteryNotLow)
            .build()

    /**
     * For a widget refresh that makes real network requests (stock, commodity,
     * sports). Without [NetworkType.CONNECTED] these woke on schedule with no
     * connectivity, opened a socket, failed, and — since every one of them
     * returns `Result.success()` unconditionally rather than `retry()` —
     * silently painted "no data" and burned the next wakeup identically.
     */
    fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

    /**
     * Milliseconds from now until just after the next local midnight, for the
     * widgets whose content is a pure function of the calendar date (moon
     * phase, countdown, calendar systems).
     *
     * Those three were running a full rebuild every 30 minutes — 48 times a
     * day — to render a value that changes exactly once a day. Pairing this
     * initial delay with a 24-hour period drops that to a single daily run
     * that lands when the date actually rolls over, rather than at whatever
     * arbitrary phase the widget happened to be placed at. The one-minute
     * cushion past midnight keeps a slightly-early firing from computing
     * yesterday's date.
     *
     * WorkManager will not fire this to the second (Doze can defer it, and
     * periodic work has its own flex window), which is fine: the providers
     * also refresh on placement, resize, reboot and app update, so a deferred
     * run self-corrects as soon as the device is in use again.
     */
    fun millisUntilNextMidnight(zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = System.currentTimeMillis()
        val nextMidnight = LocalDate.now(zone)
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return (nextMidnight - now + TimeUnit.MINUTES.toMillis(1)).coerceAtLeast(0L)
    }
}
