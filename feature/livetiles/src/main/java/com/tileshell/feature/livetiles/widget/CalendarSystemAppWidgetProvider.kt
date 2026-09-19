package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Home-screen calendar-system widget (S33) — same shell shape as
 * [WeatherAppWidgetProvider]. Unlike the other S33 widgets, this one has
 * genuine required per-instance config (which of the 8 systems — see
 * [WidgetConfigureActivity]), but no fetch of its own to force: like moon
 * phase, it's pure local date math, so there's nothing analogous to
 * weather's "force a real fetch" concern.
 *
 * [onReceive] adds a push-driven refresh on top of the once-a-day midnight
 * job ([CalendarSystemWidgetRefreshWorker.ensureScheduled]) — same idea as
 * [BatteryAppWidgetProvider]'s plug/unplug listening. A periodic WorkManager
 * job alone can be deferred by Doze well past its scheduled time, and unlike
 * the other widgets' 15/30-minute cadences papering over that, this one only
 * runs once a day — a deferred run leaves the date stale for a further full
 * day rather than one short interval (user-reported: the widget stayed on
 * yesterday's weekday well after the date had rolled over). `DATE_CHANGED`/
 * `TIME_CHANGED`/`TIMEZONE_CHANGED` are a documented exception to Android
 * 8+'s implicit-broadcast restrictions — the same three AOSP's own Calendar
 * app widget listens for to solve this identical problem — so they reach
 * this manifest-registered receiver reliably even with the app not running.
 */
class CalendarSystemAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in DATE_ROLLOVER_ACTIONS) {
            CalendarSystemWidgetRefreshWorker.refreshNow(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Also re-assert the schedule here, not only in onEnabled. The OS
        // broadcasts APPWIDGET_UPDATE to every provider when the app is
        // updated, and this is the only hook that runs for a widget that was
        // already placed — without it, an existing install would keep whatever
        // schedule it was first given (interval, constraints and all) forever,
        // since onEnabled fires only for the very first instance. Paired with
        // ExistingPeriodicWorkPolicy.UPDATE in ensureScheduled, this is what
        // lets a changed cadence or constraint actually reach existing users.
        CalendarSystemWidgetRefreshWorker.ensureScheduled(context)
        CalendarSystemWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        CalendarSystemWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        CalendarSystemWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        CalendarSystemWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            WidgetColorStore.clear(context, it)
            WidgetConfigStore.clear(context, it)
        }
    }

    companion object {
        private val DATE_ROLLOVER_ACTIONS = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
