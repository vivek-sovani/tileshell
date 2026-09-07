package com.tileshell.feature.livetiles.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.StepsPrefs
import com.tileshell.feature.livetiles.DEFAULT_STEPS_GOAL
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.StepsWidgetState
import com.tileshell.feature.livetiles.resolveSteps
import com.tileshell.feature.livetiles.stepsGoalProgress
import com.tileshell.feature.livetiles.stepsWidgetState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Builds + pushes the steps widget's [RemoteViews]. Never flips — matches
 * [com.tileshell.feature.livetiles.StepsTileFace], which doesn't either.
 * Unlike the in-app tile (a `SensorEventListener` that stays registered the
 * whole time the tile is composed), a Worker has no such lifetime — it
 * registers the raw step-counter sensor, waits for exactly one reading (or
 * gives up after 5s if the sensor is slow to report), resolves it against
 * the same persisted [StepsPrefs.Baseline] the in-app tile reads/writes, and
 * unregisters immediately.
 *
 * A `RemoteViews` tree can't host a permission dialog, so when
 * `ACTIVITY_RECOGNITION` isn't granted the widget instead labels itself "tap
 * to allow" and points its whole body at [reconfigurePendingIntent] — its own
 * configure activity, which does the asking (user-reported: adding the steps
 * widget never asked for the permission at all, so it sat on "--" with no way
 * to fix it from the home screen). No step sensor at all, or a timed-out
 * one-shot read, has nothing the user could act on and stays a plain dash —
 * see [stepsWidgetState].
 */
class StepsWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_steps_widget_refresh"
        private const val UNIQUE_NOW = "tileshell_steps_widget_refresh_now"

        fun ensureScheduled(context: Context) {
            // 15 min, not 30 — WorkManager's own periodic floor, same cadence
            // Battery/Stock/Commodity already use. A step-counter sensor has
            // no manifest-deliverable "changed" broadcast the way battery
            // partly does (see BatteryAppWidgetProvider), so a push-driven
            // widget refresh genuinely isn't available here — each periodic
            // run is already a fresh one-shot sensor read (see
            // readStepCounterOnce), not a stale cached value, so tightening
            // this interval is the whole fix (user-reported: 30 min felt
            // stale next to the in-app tile's own live sensor listener).
            // UPDATE, not KEEP: an install that already had this widget
            // scheduled at the old 30-min interval would otherwise never
            // pick up the new one without a reinstall — UPDATE reconciles
            // an existing schedule to match a changed request in place.
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<StepsWidgetRefreshWorker>(15, TimeUnit.MINUTES)
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
                OneTimeWorkRequestBuilder<StepsWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StepsAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
            val steps = if (granted) stepsTodayOrNull(context) else null
            val state = stepsWidgetState(granted, steps)
            ids.forEach { id ->
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val views = buildRemoteViews(context, id, steps, state, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        /** Caller checks the permission first (see [pushAll]) — null here means no sensor / no reading. */
        private suspend fun stepsTodayOrNull(context: Context): Int? {
            val counter = withTimeoutOrNull(5_000L) { readStepCounterOnce(context) } ?: return null
            val baseline = StepsPrefs.readBaseline(context)
            val resolution = resolveSteps(counter, baseline, LocalDate.now().toEpochDay())
            if (resolution.newBaseline != baseline) {
                StepsPrefs.saveBaseline(context, resolution.newBaseline)
            }
            return resolution.stepsToday
        }

        private suspend fun readStepCounterOnce(context: Context): Float? =
            suspendCancellableCoroutine { cont ->
                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                if (sensorManager == null || sensor == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values.firstOrNull())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                runCatching { sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
            }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            steps: Int?,
            state: StepsWidgetState,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_steps_compact else R.layout.widget_steps
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            // Rounds the widget's own corners (see widget_rounded_background.xml) —
            // the background drawable provides an Outline; clipToOutline clips
            // widget_bg's full-bleed gradient (and everything else) to it.
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            // No body tap while the count is actually showing, unlike
            // weather/alarm/battery/moon phase (see WidgetAppLaunch.kt): the
            // step count reads a bare device sensor, not a specific app's
            // data, and there's no OS-standard "steps app" intent the way
            // AlarmClock.ACTION_SHOW_ALARMS or ACTION_POWER_USAGE_SUMMARY
            // exist for the others. A real candidate (Health Connect) would
            // need its package declared in this app's manifest <queries>
            // first — deliberately not added without asking, since that's an
            // app-wide manifest change. The one exception is the
            // needs-permission state, where the body tap is the *only* route
            // to the ask (the gear is a 24dp target in a corner, and nothing
            // else on the widget explains why it reads "--").
            if (state == StepsWidgetState.NEEDS_PERMISSION) {
                views.setOnClickPendingIntent(R.id.widget_root, reconfigurePendingIntent(context, appWidgetId))
            }
            views.setTextColor(R.id.widget_count, onAccent)
            views.setTextViewText(R.id.widget_count, steps?.toString() ?: "--")
            views.setInt(R.id.widget_icon, "setColorFilter", onAccent)

            views.setTextColor(R.id.widget_label, onAccent)
            if (compact) {
                // The narrow layout's label is gone by default — it only ever
                // carries the needs-permission hint (there's no room for a
                // permanent caption next to a 26sp count).
                views.setViewVisibility(
                    R.id.widget_label,
                    if (state == StepsWidgetState.NEEDS_PERMISSION) View.VISIBLE else View.GONE,
                )
                views.setTextViewText(R.id.widget_label, "tap to allow")
            } else {
                views.setTextViewText(
                    R.id.widget_label,
                    if (state == StepsWidgetState.NEEDS_PERMISSION) "tap to allow" else "steps",
                )
            }

            if (!compact) {
                val progress = if (steps != null) {
                    (stepsGoalProgress(steps, DEFAULT_STEPS_GOAL) * 100).toInt()
                } else {
                    0
                }
                views.setProgressBar(R.id.widget_progress, 100, progress, false)
            }
            return views
        }
    }
}
