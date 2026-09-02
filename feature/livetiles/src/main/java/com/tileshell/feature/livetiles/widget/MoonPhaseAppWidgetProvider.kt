package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Home-screen moon-phase widget (S33) — same shell shape as [WeatherAppWidgetProvider]. */
class MoonPhaseAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
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
}
