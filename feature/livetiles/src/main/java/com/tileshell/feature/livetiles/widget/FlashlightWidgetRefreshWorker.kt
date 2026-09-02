package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.flashlightStatusText

/**
 * Builds + pushes the flashlight widget's [RemoteViews] from
 * [WidgetFlashlightState] — no periodic refresh at all (see this widget's
 * `updatePeriodMillis="0"`): nothing external changes this widget's state on
 * its own, only [FlashlightAppWidgetProvider]'s own toggle broadcast or the
 * in-app [com.tileshell.feature.livetiles.rememberTorchOn] cascade ever call
 * [refreshNow], so there is nothing for a periodic tick to catch that those
 * two don't already push immediately.
 */
class FlashlightWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NOW = "tileshell_flashlight_widget_refresh_now"

        /** No periodic cadence for this widget (see class doc) — this only exists so
         * [WidgetConfigureActivity]'s per-kind dispatch has a consistent shape. */
        fun ensureScheduled(context: Context) {
            refreshNow(context)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NOW)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FlashlightWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FlashlightAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val on = WidgetFlashlightState.isOn(context)
            ids.forEach { id ->
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val views = buildRemoteViews(context, id, on, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            on: Boolean,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_flashlight_compact else R.layout.widget_flashlight
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            views.setOnClickPendingIntent(R.id.widget_root, FlashlightAppWidgetProvider.togglePendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            views.setTextColor(R.id.widget_status, onAccent)
            views.setTextViewText(R.id.widget_status, flashlightStatusText(on))
            if (!compact) {
                val iconAlpha = if (on) 255 else 153 // 0.6 alpha, matching the in-app tile's dim-when-off
                views.setInt(R.id.widget_icon, "setColorFilter", (iconAlpha shl 24) or (onAccent and 0x00FFFFFF))
            }
            return views
        }
    }
}
