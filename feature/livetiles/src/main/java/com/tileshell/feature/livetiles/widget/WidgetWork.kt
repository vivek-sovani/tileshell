package com.tileshell.feature.livetiles.widget

import android.content.Context
import android.os.PowerManager
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

    /**
     * Whether a periodic widget tick should do nothing because the screen is off.
     * Pure, so the precedence is unit-testable; [skipWhileScreenOff] reads the
     * live screen state for it.
     *
     * Re-rendering a widget nobody can currently see is pure waste, and it adds
     * up: measured on a real device, the 15-minute Battery and Steps widgets plus
     * the 30-minute Weather widget account for ~240 wakeups a day between them,
     * the large majority of those overnight. The cost of skipping is bounded at
     * one interval of staleness after the screen comes back on, which is
     * acceptable precisely because [WidgetWork]'s whole premise is that these
     * refreshes are cosmetic — and because the moments that genuinely matter are
     * already event-driven rather than polled (the battery widget has manifest
     * receivers for plug/unplug and low/okay; the alarm widget listens for
     * `NEXT_ALARM_CLOCK_CHANGED`).
     *
     * [force] is the existing "this was explicitly asked for" marker the
     * stock/sports/commodity workers already carry on their one-off requests —
     * a user-triggered refresh, a config change or a provider event must never
     * be skipped, only the unattended periodic cadence.
     *
     * Deliberately *not* applied to the midnight-aligned daily workers (calendar
     * system, moon phase, countdown): their single daily run is scheduled for
     * just after midnight, precisely when the screen is off, so gating them on
     * the screen would skip the one tick that matters and leave the date stale
     * for a further 24 hours.
     */
    fun shouldSkipWidgetRefresh(force: Boolean, screenInteractive: Boolean): Boolean =
        !force && !screenInteractive

    /** [shouldSkipWidgetRefresh] against the device's live screen state. */
    fun skipWhileScreenOff(context: Context, force: Boolean): Boolean =
        shouldSkipWidgetRefresh(
            force = force,
            screenInteractive = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true,
        )
}
