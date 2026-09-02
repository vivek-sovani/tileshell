package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Home-screen stock widget — same shell shape as [WeatherAppWidgetProvider].
 * Has genuine required per-instance config (which symbol — see
 * [WidgetConfigureActivity]), same as [CalendarSystemAppWidgetProvider].
 */
class StockAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        StockWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        StockWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        StockWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        StockWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetColorStore.clear(context, it); WidgetConfigStore.clear(context, it) }
    }
}
