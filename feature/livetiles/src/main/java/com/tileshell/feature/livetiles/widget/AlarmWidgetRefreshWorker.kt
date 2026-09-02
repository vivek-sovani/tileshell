package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.feature.livetiles.AlarmFace
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.currentAlarmFace
import java.util.concurrent.TimeUnit

/**
 * Builds + pushes the alarm widget's [RemoteViews] from [currentAlarmFace] —
 * the same direct [android.app.AlarmManager.getNextAlarmClock] read the
 * in-app tile uses, no cache. Periodic ~30-min push + add/resize/configure
 * triggers only: the in-app tile also reacts instantly to `ACTION_NEXT_ALARM
 * _CLOCK_CHANGED` via a dynamically-registered receiver, which a widget
 * provider (a manifest-declared receiver) can't cheaply replicate — same
 * class of staleness tradeoff as the battery widget.
 */
class AlarmWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_alarm_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_alarm_widget_refresh_now"

        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AlarmWidgetRefreshWorker>(30, TimeUnit.MINUTES).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AlarmWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AlarmAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val face = currentAlarmFace(context)
            ids.forEach { id ->
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val views = buildRemoteViews(context, id, face, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            face: AlarmFace,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_alarm_compact else R.layout.widget_alarm
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            views.setOnClickPendingIntent(R.id.widget_root, alarmAppPendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))

            if (compact) {
                views.setTextColor(R.id.widget_time, onAccent)
                views.setTextColor(R.id.widget_day, onAccent)
                views.setTextViewText(R.id.widget_time, if (face.hasAlarm) face.time else "--")
                views.setTextViewText(R.id.widget_day, if (face.hasAlarm) face.dayLabel else "no alarm")
            } else {
                views.setTextColor(R.id.widget_time, onAccent)
                views.setTextColor(R.id.widget_day, onAccent)
                views.setTextColor(R.id.widget_back_label, onAccent)
                views.setTextColor(R.id.widget_back_hint, onAccent)
                views.setInt(R.id.widget_icon, "setColorFilter", onAccent)
                views.setTextViewText(R.id.widget_time, if (face.hasAlarm) face.time else "no alarm set")
                views.setTextViewText(R.id.widget_day, if (face.hasAlarm) face.dayLabel else "")
                // Same wording as the in-app back face — the tap now opens
                // the clock app (alarmAppPendingIntent), so "tap to..." is
                // accurate again.
                views.setTextViewText(
                    R.id.widget_back_hint,
                    if (face.hasAlarm) "tap to open your alarms" else "tap to set one",
                )
            }
            return views
        }
    }
}
