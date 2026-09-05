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
        // Also re-assert the schedule here, not only in onEnabled. The OS
        // broadcasts APPWIDGET_UPDATE to every provider when the app is
        // updated, and this is the only hook that runs for a widget that was
        // already placed — without it, an existing install would keep whatever
        // schedule it was first given (interval, constraints and all) forever,
        // since onEnabled fires only for the very first instance. Paired with
        // ExistingPeriodicWorkPolicy.UPDATE in ensureScheduled, this is what
        // lets a changed cadence or constraint actually reach existing users.
        WeatherWidgetRefreshWorker.ensureScheduled(context)
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
        // onEnabled also started the *network* forecast poll, which this used to
        // leave running forever: place a weather widget, remove it, and
        // WeatherRefreshWorker kept fetching every 30 minutes for the life of
        // the install with nothing consuming it. Cancel it here too — the in-app
        // weather tile re-schedules it from its own LaunchedEffect the next time
        // it renders, so an over-eager cancel self-heals rather than leaving the
        // tile stale.
        com.tileshell.feature.livetiles.WeatherRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetColorStore.clear(context, it) }
    }
}
