package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Home-screen moon-phase widget (S33) — same shell shape as
 * [WeatherAppWidgetProvider]. [onReceive] adds a push-driven refresh on
 * `DATE_CHANGED`/`TIME_CHANGED`/`TIMEZONE_CHANGED` on top of the once-a-day
 * midnight job — see [CalendarSystemAppWidgetProvider]'s doc comment for why
 * (a deferred once-daily WorkManager run leaves this stale a full day, not
 * one short interval; this trio is a documented exception to Android 8+'s
 * implicit-broadcast restrictions).
 */
class MoonPhaseAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in DATE_ROLLOVER_ACTIONS) {
            MoonPhaseWidgetRefreshWorker.refreshNow(context)
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
        MoonPhaseWidgetRefreshWorker.ensureScheduled(context)
        MoonPhaseWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        MoonPhaseWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        MoonPhaseWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        MoonPhaseWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetColorStore.clear(context, it) }
    }

    companion object {
        private val DATE_ROLLOVER_ACTIONS = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
