package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Home-screen countdown widget — same shell shape as
 * [CalendarSystemAppWidgetProvider]: genuine required per-instance config
 * (a target date + optional label, via [WidgetConfigureActivity]'s
 * `STICKY_NOTE_TEXT`-style date/label step), pure local date math on every
 * refresh, no fetch of its own to force. [onReceive] adds the same
 * `DATE_CHANGED`/`TIME_CHANGED`/`TIMEZONE_CHANGED` push-driven refresh as
 * [CalendarSystemAppWidgetProvider] — see its doc comment for why the
 * once-a-day midnight job alone isn't enough.
 */
class CountdownAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in DATE_ROLLOVER_ACTIONS) {
            CountdownWidgetRefreshWorker.refreshNow(context)
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
        CountdownWidgetRefreshWorker.ensureScheduled(context)
        CountdownWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        CountdownWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        CountdownWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        CountdownWidgetRefreshWorker.cancel(context)
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
