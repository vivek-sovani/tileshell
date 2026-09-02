package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Home-screen alarm widget (S33) — same shell shape as [WeatherAppWidgetProvider]. */
class AlarmAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        AlarmWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        AlarmWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        AlarmWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        AlarmWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetColorStore.clear(context, it) }
    }
}
