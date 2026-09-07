package com.tileshell.feature.livetiles.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
        // Deliberately no "backfill a missing location to current" step here
        // — a first attempt tried that, reasoning onUpdate only fires for a
        // widget that predates several-locations support (a genuinely new one
        // would go through its configure step, which writes a real location,
        // before the OS ever calls onUpdate for it). On-device testing proved
        // that reasoning wrong: `WidgetSlot.kt`'s `addProvider` binds via
        // `bindAppWidgetIdIfAllowed` — a same-app bind that the OS apparently
        // treats as immediately active, pushing this exact onUpdate call
        // *before* `afterBind`'s follow-up `ACTION_APPWIDGET_CONFIGURE` intent
        // ever launches. A backfill here would race that intent and always
        // win, silently pre-answering "current location" for every brand-new
        // widget and skipping `WidgetConfigureActivity`'s location step
        // outright — confirmed via `tileshell_widget_config.xml` showing a
        // freshly-bound id already holding `weather:current` the instant its
        // configure Activity opened, landing straight on the colour step.
        // No backfill needed anyway: `WeatherWidgetRefreshWorker.pushAll`
        // already treats a missing stored location as "current" at render
        // time, so a widget from before this feature keeps behaving exactly
        // as it always did with zero code here. The only cost of removing
        // this is that manually reopening an old widget's configure (its gear
        // icon) shows the location step once, the first time — a one-off,
        // not a nag: answering either way stores a real location and it never
        // asks again for that instance.
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
        appWidgetIds.forEach { WidgetColorStore.clear(context, it); WidgetConfigStore.clear(context, it) }
    }

    companion object {
        /** The widget's own manual "refresh now" tap target — see [WeatherWidgetActionReceiver]. */
        fun refreshPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WeatherWidgetActionReceiver::class.java)
                .setAction(WeatherWidgetActionReceiver.ACTION_REFRESH_WEATHER)
            return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
