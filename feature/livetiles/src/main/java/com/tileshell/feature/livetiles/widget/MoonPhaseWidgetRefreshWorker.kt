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
import com.tileshell.feature.livetiles.MoonPhaseFace
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.currentMoonPhaseFace
import java.util.concurrent.TimeUnit

/**
 * Builds + pushes the moon-phase widget's [RemoteViews] from
 * [currentMoonPhaseFace] — pure local date math, same as the in-app tile, no
 * permission/cache/network at all.
 *
 * Refreshes **once a day, just after midnight** ([WidgetWork.millisUntilNextMidnight]).
 * This used to run every 30 minutes "for consistency with the rest of the
 * batch", which meant 48 full rebuilds a day — each one regenerating a
 * `Canvas`-drawn phase disc and pushing a fresh `RemoteViews` across a Binder
 * — to render a value that changes once. The date is the only input, so the
 * only moment worth waking for is when the date changes.
 */
class MoonPhaseWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_moonphase_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_moonphase_widget_refresh_now"

        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                // UPDATE, not KEEP: an install already scheduled at the old
                // 30-minute cadence would otherwise keep it forever.
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MoonPhaseWidgetRefreshWorker>(1, TimeUnit.DAYS)
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
                OneTimeWorkRequestBuilder<MoonPhaseWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MoonPhaseAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val face = currentMoonPhaseFace()
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
            face: MoonPhaseFace,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_moonphase_compact else R.layout.widget_moonphase
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_root, moonPhaseAppPendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            val illumination = "${face.illuminationPercent}% lit"

            if (compact) {
                views.setTextColor(R.id.widget_name, onAccent)
                views.setTextColor(R.id.widget_illumination, onAccent)
                // Same real phase shape as the full layout (user-reported: a
                // generic glyph wasn't good enough) — see WidgetMoonPhaseVisual.kt.
                views.setImageViewBitmap(R.id.widget_icon, moonPhaseBitmap(face.fraction, onAccent))
                views.setTextViewText(R.id.widget_name, face.name)
                views.setTextViewText(R.id.widget_illumination, illumination)
            } else {
                views.setTextColor(R.id.widget_name, onAccent)
                views.setTextColor(R.id.widget_illumination, onAccent)
                views.setTextColor(R.id.widget_back_label, onAccent)
                views.setTextColor(R.id.widget_back_next, onAccent)
                // The real phase shape (user-reported: a generic glyph wasn't
                // good enough), not a static tinted icon — see
                // WidgetMoonPhaseVisual.kt.
                views.setImageViewBitmap(R.id.widget_icon, moonPhaseBitmap(face.fraction, onAccent))
                views.setTextViewText(R.id.widget_name, face.name)
                views.setTextViewText(R.id.widget_illumination, illumination)
                views.setTextViewText(R.id.widget_back_next, face.nextEventLabel)
            }
            return views
        }
    }
}
