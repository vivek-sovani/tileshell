package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.feature.livetiles.DailyForecast
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.WeatherCache
import com.tileshell.feature.livetiles.WeatherSnapshot
import com.tileshell.feature.livetiles.highLowLabel
import com.tileshell.feature.livetiles.tempLabel
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Builds + pushes the weather widget's [RemoteViews] from the same
 * [WeatherCache] the in-app card already reads — this worker never fetches
 * weather itself, it only renders whatever
 * [com.tileshell.feature.livetiles.WeatherRefreshWorker] last cached. Two
 * triggers: a ~30-min periodic push (matches Android's own floor for a
 * periodic widget update) and an immediate one-off, called from the provider
 * (add/resize/OS update) and from `WeatherRefreshWorker` itself right after a
 * fresh snapshot lands, so a placed widget doesn't wait a full period to
 * reflect new data.
 */
class WeatherWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_weather_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_weather_widget_refresh_now"

        /** Idempotent (KEEP) — safe to call from every provider lifecycle callback. */
        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WeatherWidgetRefreshWorker>(30, TimeUnit.MINUTES).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WeatherWidgetRefreshWorker>().build(),
            )
        }

        /** Renders + pushes every currently-placed weather widget instance. */
        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val snapshot = WeatherCache.create(context).read().snapshot

            ids.forEach { id ->
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val views = buildRemoteViews(
                    context = context,
                    appWidgetId = id,
                    snapshot = snapshot,
                    accent = accent,
                    onAccent = onAccent,
                    compact = isCompactWidget(minWidthDp),
                )
                manager.updateAppWidget(id, views)
            }
        }

        /**
         * Tapping the card opens weather's "respective app" — see
         * [weatherAppPendingIntent] — not TileShell itself: an earlier S32
         * follow-up removed the tap entirely because it launched TileShell's
         * own MainActivity, jarring on a *different* launcher; the actual
         * fix is opening the right external target, not no target at all.
         * The small settings gear is a separate, smaller tap target on top —
         * it reopens [WidgetConfigureActivity] (see [reconfigurePendingIntent])
         * since the OS only auto-launches that once, at add time.
         *
         * The flip (S32 follow-up, user-reported gap): the in-app
         * [com.tileshell.feature.livetiles.WeatherTileFace]'s 3D `FlipTile` has
         * no RemoteViews equivalent, so both layouts wrap a front/back pair in a
         * `ViewFlipper` instead — a widget-native fade auto-advance declared
         * entirely in XML (`autoStart`/`flipInterval`), so it runs on its own
         * inside the host launcher's process with no Worker involved. Front
         * mirrors `WeatherFront` (place/temp/condition), back mirrors
         * `WeatherBack` (place/high-low/detail) — same [tempLabel]/
         * [highLowLabel] formatters the in-app tile itself uses.
         */
        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            snapshot: WeatherSnapshot?,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_weather_compact else R.layout.widget_weather
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_root, weatherAppPendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            views.setTextColor(R.id.widget_temp, onAccent)
            views.setTextColor(R.id.widget_condition, onAccent)
            views.setTextColor(R.id.widget_back_highlow, onAccent)
            if (!compact) {
                views.setTextColor(R.id.widget_place, onAccent)
                views.setTextColor(R.id.widget_back_detail, onAccent)
                views.setTextColor(R.id.widget_forecast_title, onAccent)
            }

            if (snapshot == null) {
                views.setTextViewText(R.id.widget_temp, "—")
                views.setTextViewText(R.id.widget_condition, "weather unavailable")
                views.setTextViewText(R.id.widget_back_highlow, "")
                if (!compact) {
                    views.setTextViewText(R.id.widget_place, "")
                    views.setTextViewText(R.id.widget_back_detail, "set a location in tileshell")
                    views.setImageViewBitmap(R.id.widget_icon, weatherConditionBitmap("clear", onAccent))
                }
                setForecastRows(views, onAccent, emptyList())
            } else {
                if (!compact) {
                    views.setImageViewBitmap(
                        R.id.widget_icon,
                        weatherConditionBitmap(snapshot.condition, onAccent),
                    )
                }
                views.setTextViewText(R.id.widget_temp, tempLabel(snapshot.tempC))
                views.setTextViewText(R.id.widget_condition, snapshot.condition)
                views.setTextViewText(
                    R.id.widget_back_highlow,
                    highLowLabel(snapshot.highC, snapshot.lowC),
                )
                if (!compact) {
                    views.setTextViewText(R.id.widget_place, snapshot.place.ifBlank { "weather" })
                    views.setTextViewText(R.id.widget_back_detail, snapshot.detail)
                }
                setForecastRows(views, onAccent, snapshot.forecast)
            }
            return views
        }

        /**
         * Populates the fixed 7 forecast rows (widget_forecast_row0..6, see
         * widget_weather.xml/widget_weather_compact.xml) — a plain fixed
         * layout, not a `RemoteViewsService` collection adapter, since 7 is a
         * known constant. Rows beyond [forecast]'s actual size are hidden
         * rather than left showing stale/blank text (a short response — e.g.
         * a geocoded city with fewer days available — shouldn't show empty
         * rows). The per-row condition TextView only exists in the full
         * layout; setting it on the compact layout is a harmless no-op
         * (RemoteViews silently ignores an action whose target id isn't in
         * the currently-inflated layout).
         */
        private fun setForecastRows(views: RemoteViews, onAccent: Int, forecast: List<DailyForecast>) {
            FORECAST_ROW_IDS.forEachIndexed { index, ids ->
                val day = forecast.getOrNull(index)
                if (day == null) {
                    views.setViewVisibility(ids.row, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(ids.row, View.VISIBLE)
                views.setTextColor(ids.day, onAccent)
                views.setTextColor(ids.hl, onAccent)
                views.setTextViewText(ids.day, day.dayLabel)
                views.setTextViewText(ids.hl, highLowLabel(day.highC, day.lowC))
                if (ids.condition != null) {
                    views.setTextColor(ids.condition, onAccent)
                    views.setTextViewText(ids.condition, day.condition)
                }
            }
        }

        private data class ForecastRowIds(val row: Int, val day: Int, val condition: Int?, val hl: Int)

        private val FORECAST_ROW_IDS = listOf(
            ForecastRowIds(R.id.widget_forecast_row0, R.id.widget_forecast_day0, R.id.widget_forecast_cond0, R.id.widget_forecast_hl0),
            ForecastRowIds(R.id.widget_forecast_row1, R.id.widget_forecast_day1, R.id.widget_forecast_cond1, R.id.widget_forecast_hl1),
            ForecastRowIds(R.id.widget_forecast_row2, R.id.widget_forecast_day2, R.id.widget_forecast_cond2, R.id.widget_forecast_hl2),
            ForecastRowIds(R.id.widget_forecast_row3, R.id.widget_forecast_day3, R.id.widget_forecast_cond3, R.id.widget_forecast_hl3),
            ForecastRowIds(R.id.widget_forecast_row4, R.id.widget_forecast_day4, R.id.widget_forecast_cond4, R.id.widget_forecast_hl4),
            ForecastRowIds(R.id.widget_forecast_row5, R.id.widget_forecast_day5, R.id.widget_forecast_cond5, R.id.widget_forecast_hl5),
            ForecastRowIds(R.id.widget_forecast_row6, R.id.widget_forecast_day6, R.id.widget_forecast_cond6, R.id.widget_forecast_hl6),
        )
    }
}
