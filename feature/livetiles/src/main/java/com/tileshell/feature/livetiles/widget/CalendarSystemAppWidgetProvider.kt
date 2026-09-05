package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Home-screen calendar-system widget (S33) — same shell shape as
 * [WeatherAppWidgetProvider]. Unlike the other S33 widgets, this one has
 * genuine required per-instance config (which of the 8 systems — see
 * [WidgetConfigureActivity]), but no fetch of its own to force: like moon
 * phase, it's pure local date math, so there's nothing analogous to
 * weather's "force a real fetch" concern.
 */
class CalendarSystemAppWidgetProvider : AppWidgetProvider() {

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
}
