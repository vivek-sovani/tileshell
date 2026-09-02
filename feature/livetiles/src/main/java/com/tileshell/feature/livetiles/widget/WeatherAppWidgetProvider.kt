package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Home-screen weather widget (S32 pilot) — a thin shell over
 * [WeatherWidgetRefreshWorker], which owns the actual render/push logic so
 * both the OS-driven callbacks here and the periodic refresh share one code
 * path. It renders whatever [com.tileshell.feature.livetiles.WeatherRefreshWorker]
 * already cached, but (user-reported: "readded weather tile but forecast not
 * shown", twice) also forces that worker to actually fetch here.
 *
 * The first fix attempt called that worker's `ensureScheduled` and still
 * didn't work — root cause: `ensureScheduled`'s one-off fetch uses
 * `ExistingWorkPolicy.KEEP`, so once a "weather refresh now" job had ever
 * completed once on the device (e.g. from earlier in-app use, before this
 * session's 7-day fetch existed), it never ran again — `ensureScheduled`
 * only *starts* the periodic cadence, it doesn't *force* a fresh attempt.
 * [com.tileshell.feature.livetiles.WeatherRefreshWorker.refreshNow] is the
 * one that uses `REPLACE`, guaranteeing a real new fetch every time the
 * widget is placed or updated — that's the one this needs, not
 * `ensureScheduled`.
 */
class WeatherAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        com.tileshell.feature.livetiles.WeatherRefreshWorker.refreshNow(context)
        WeatherWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // A resize needs a different RemoteViews layout (icon/high-low line
        // shown or not) — re-render rather than let the OS stretch the old one.
        WeatherWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        // First instance placed — start the periodic cadence, and force one
        // real fetch right now rather than waiting on it (see class doc).
        com.tileshell.feature.livetiles.WeatherRefreshWorker.ensureScheduled(context)
        com.tileshell.feature.livetiles.WeatherRefreshWorker.refreshNow(context)
        WeatherWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        // Last instance removed — the OS only calls this when none remain.
        WeatherWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetColorStore.clear(context, it) }
    }
}
