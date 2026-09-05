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
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.countdownFace
import com.tileshell.feature.livetiles.countdownStatusText
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Builds + pushes the countdown widget's [RemoteViews] from [countdownFace] —
 * pure local date math, same as the in-app tile, no permission/cache/network
 * at all.
 *
 * Refreshes **once a day, just after midnight** ([WidgetWork.millisUntilNextMidnight]),
 * matching the moon-phase widget. The day count is a pure function of the
 * date, so a 30-minute cadence was 48 wakeups a day to re-render an
 * unchanged number.
 */
class CountdownWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_countdown_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_countdown_widget_refresh_now"

        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                // UPDATE, not KEEP: an install already scheduled at the old
                // 30-minute cadence would otherwise keep it forever.
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CountdownWidgetRefreshWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(WidgetWork.millisUntilNextMidnight(), TimeUnit.MILLISECONDS)
                    .setConstraints(WidgetWork.localConstraints())
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CountdownWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CountdownAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val today = LocalDate.now()
            ids.forEach { id ->
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val (isoDate, label) = WidgetConfigStore.countdown(context, id) ?: ("" to "")
                val views = buildRemoteViews(context, id, isoDate, label, today, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            targetIsoDate: String,
            label: String,
            today: LocalDate,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_countdown_compact else R.layout.widget_countdown
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_root, reconfigurePendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))

            val target = runCatching { LocalDate.parse(targetIsoDate) }.getOrNull()
            val face = target?.let { countdownFace(label, it, today) }
            val statusText = face?.let { countdownStatusText(it.daysRemaining) } ?: "tap to set a date"
            val labelText = face?.label ?: "countdown"

            views.setTextColor(R.id.widget_status, onAccent)
            views.setTextViewText(R.id.widget_status, statusText)
            views.setTextColor(R.id.widget_label, onAccent)
            views.setTextViewText(R.id.widget_label, labelText)
            views.setInt(R.id.widget_icon, "setColorFilter", onAccent)

            if (!compact) {
                views.setTextColor(R.id.widget_back_label, onAccent)
                views.setTextColor(R.id.widget_back_date, onAccent)
                views.setTextViewText(R.id.widget_back_date, face?.dateText ?: "tap to edit")
            }
            return views
        }
    }
}
