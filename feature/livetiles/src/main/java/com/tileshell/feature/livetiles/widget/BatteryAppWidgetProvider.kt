package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Home-screen battery widget (S33) — same shell shape as [WeatherAppWidgetProvider]. */
class BatteryAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        BatteryWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        BatteryWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        BatteryWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        BatteryWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetColorStore.clear(context, it) }
    }
}
