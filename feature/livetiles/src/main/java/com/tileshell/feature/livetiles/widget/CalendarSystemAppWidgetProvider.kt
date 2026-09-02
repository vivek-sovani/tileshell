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
