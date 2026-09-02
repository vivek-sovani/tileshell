package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Home-screen countdown widget — same shell shape as
 * [CalendarSystemAppWidgetProvider]: genuine required per-instance config
 * (a target date + optional label, via [WidgetConfigureActivity]'s
 * `STICKY_NOTE_TEXT`-style date/label step), pure local date math on every
 * refresh, no fetch of its own to force.
 */
class CountdownAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
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
}
