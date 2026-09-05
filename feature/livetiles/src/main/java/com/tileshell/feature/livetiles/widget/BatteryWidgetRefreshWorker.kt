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
import com.tileshell.feature.livetiles.BatteryFace
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.currentBatteryFace
import java.util.concurrent.TimeUnit

/**
 * Builds + pushes the battery widget's [RemoteViews]. Unlike weather, there's
 * no cache to read — [currentBatteryFace] is a direct, cheap
 * [android.os.BatteryManager] read, same one the in-app tile uses.
 * `ACTION_BATTERY_CHANGED` is one of the implicit broadcasts Android blocks
 * from reaching a manifest-declared receiver (this provider) since API 26, so
 * unlike the in-app tile's dynamically-registered receiver, this widget can't
 * react to a plug/unplug event on its own. Two mitigations (user-reported:
 * "removed cable but still shows [charging status]"): a 15-min periodic push
 * — [PeriodicWorkRequestBuilder]'s real floor, not the 30 min used elsewhere
 * in this batch, since staleness here is directly user-visible in a way the
 * other widgets' data isn't — and [com.tileshell.feature.livetiles
 * .BatteryTileFace] itself now calls [refreshNow] from its own already-
 * existing dynamic receiver, so the widget also updates instantly whenever
 * the in-app battery tile/card happens to be on screen when you plug/unplug
 * (same cascade [WeatherRefreshWorker] already does for weather). Neither
 * makes the widget itself instantly reactive while the app is fully
 * backgrounded — that would need a foreground service, out of scope here.
 */
class BatteryWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_battery_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_battery_widget_refresh_now"

        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                // Deliberately NOT requiresBatteryNotLow, unlike every other
                // widget worker: a low battery is exactly when this widget's
                // reading matters most, so suppressing its refresh then would
                // be perverse.
                PeriodicWorkRequestBuilder<BatteryWidgetRefreshWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(WidgetWork.localConstraints(requiresBatteryNotLow = false))
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
                OneTimeWorkRequestBuilder<BatteryWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val face = currentBatteryFace(context)
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
            face: BatteryFace,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_battery_compact else R.layout.widget_battery
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_root, batteryAppPendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))

            val backStatus = if (face.hasData) face.statusLine else "battery status unavailable"
            // A real filled-bar gauge (user-requested), not a static outline
            // glyph — see WidgetBatteryVisual.kt. percentText is e.g. "82%"
            // or "--" when unavailable. Shown at both sizes now — the compact
            // layout used to omit it (user-reported: "battery widget when in
            // half mode doesn't show battery symbol, while battery glance
            // card shows symbol"), smaller only because widget_icon itself is
            // smaller in widget_battery_compact.xml.
            val percent = face.percentText.removeSuffix("%").toIntOrNull() ?: 0
            views.setImageViewBitmap(
                R.id.widget_icon,
                batteryGaugeBitmap(percent, onAccent, isCharging = face.isCharging),
            )
            if (compact) {
                views.setTextColor(R.id.widget_percent, onAccent)
                views.setTextColor(R.id.widget_back_status, onAccent)
                views.setTextViewText(R.id.widget_percent, face.percentText)
                views.setTextViewText(R.id.widget_back_status, backStatus)
            } else {
                views.setTextColor(R.id.widget_percent, onAccent)
                views.setTextColor(R.id.widget_status, onAccent)
                views.setTextColor(R.id.widget_back_label, onAccent)
                views.setTextColor(R.id.widget_back_status, onAccent)
                views.setTextViewText(R.id.widget_percent, face.percentText)
                views.setTextViewText(R.id.widget_status, face.statusLine)
                views.setTextViewText(R.id.widget_back_status, backStatus)
            }
            return views
        }
    }
}
