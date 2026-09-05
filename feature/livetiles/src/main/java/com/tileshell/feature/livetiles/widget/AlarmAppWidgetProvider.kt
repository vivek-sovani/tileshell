package com.tileshell.feature.livetiles.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Home-screen alarm widget (S33) — same shell shape as [WeatherAppWidgetProvider],
 * plus the push-driven refresh path [BatteryAppWidgetProvider] uses.
 *
 * This widget renders exactly one thing: the device's next scheduled alarm
 * ([AlarmManager.getNextAlarmClock]). That value changes only when the user
 * sets, edits, or cancels an alarm, or when one fires — and Android announces
 * every one of those with [AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED],
 * which (unlike `ACTION_BATTERY_CHANGED`) *is* deliverable to a manifest-
 * registered receiver. So the widget can be genuinely event-driven: the OS
 * wakes this receiver only when the answer actually changed, and the periodic
 * schedule drops to a slow backstop rather than a 30-minute poll that was
 * re-rendering an unchanged alarm time 48 times a day.
 */
class AlarmAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) {
            AlarmWidgetRefreshWorker.refreshNow(context)
            return
        }
        super.onReceive(context, intent)
    }

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
