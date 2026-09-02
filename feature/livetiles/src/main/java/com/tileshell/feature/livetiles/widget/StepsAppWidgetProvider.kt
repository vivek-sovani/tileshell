package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Home-screen steps widget (S33) — same shell shape as [WeatherAppWidgetProvider]. */
class StepsAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        StepsWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        StepsWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        StepsWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        StepsWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetColorStore.clear(context, it) }
    }
}
