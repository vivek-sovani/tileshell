package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Home-screen sports widget — same shell shape as [WeatherAppWidgetProvider].
 * Has genuine required per-instance config (which league + team — see
 * [WidgetConfigureActivity]), same as [CalendarSystemAppWidgetProvider]/
 * [StockAppWidgetProvider].
 */
class SportsAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        SportsWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        SportsWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        SportsWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        SportsWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            WidgetColorStore.clear(context, it)
            WidgetConfigStore.clear(context, it)
            WidgetSportsStateStore.clear(context, it)
        }
    }
}
