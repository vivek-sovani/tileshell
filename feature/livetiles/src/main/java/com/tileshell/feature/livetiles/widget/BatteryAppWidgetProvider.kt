package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Home-screen battery widget (S33) — same shell shape as
 * [WeatherAppWidgetProvider], plus an extra push-driven refresh path
 * (user-reported: 15-min periodic-only felt stale next to the in-app
 * tile's own instant `ACTION_BATTERY_CHANGED` receiver). A real
 * `ACTION_BATTERY_CHANGED` can only ever reach a *dynamically* registered
 * receiver (Android refuses to deliver it to a manifest one, at any API
 * level — this is why the in-app tile's own `BroadcastReceiver` only works
 * while it's actually composed, not from here), so this widget instead
 * listens for the handful of battery-related broadcasts that ARE
 * manifest-deliverable: [Intent.ACTION_POWER_CONNECTED]/
 * [Intent.ACTION_POWER_DISCONNECTED] (the two moments a user actually
 * glances at a battery widget for) and [Intent.ACTION_BATTERY_LOW]/
 * [Intent.ACTION_BATTERY_OKAY]. None of these fire on every 1% drop while
 * just discharging normally, so the existing 15-min periodic schedule
 * stays as a backstop for that gradual drift — this is additive, not a
 * replacement. Each of these is itself push-driven (the OS invokes this
 * receiver only when the real event happens), so unlike periodic
 * WorkManager it costs nothing between actual events — a net battery win,
 * not a cost, versus waking up unconditionally every 15 minutes.
 */
class BatteryAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in BATTERY_EVENT_ACTIONS) {
            BatteryWidgetRefreshWorker.refreshNow(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Also re-assert the schedule here, not only in onEnabled. The OS
        // broadcasts APPWIDGET_UPDATE to every provider when the app is
        // updated, and this is the only hook that runs for a widget that was
        // already placed — without it, an existing install would keep whatever
        // schedule it was first given (interval, constraints and all) forever,
        // since onEnabled fires only for the very first instance. Paired with
        // ExistingPeriodicWorkPolicy.UPDATE in ensureScheduled, this is what
        // lets a changed cadence or constraint actually reach existing users.
        BatteryWidgetRefreshWorker.ensureScheduled(context)
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

    companion object {
        private val BATTERY_EVENT_ACTIONS = setOf(
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BATTERY_LOW,
            Intent.ACTION_BATTERY_OKAY,
        )
    }
}
