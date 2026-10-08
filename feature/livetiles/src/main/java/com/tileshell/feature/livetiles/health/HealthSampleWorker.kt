package com.tileshell.feature.livetiles.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.LayoutRepository
import com.tileshell.feature.livetiles.widget.StepsAppWidgetProvider
import kotlinx.coroutines.flow.first
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.StepsPrefs
import com.tileshell.feature.livetiles.resolveSteps
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Notes the step counter every half hour while a health tile or hub exists, so the week and streak have days to read
 * even when the hub is not open. One sensor reading, no foreground service. The counter resets on reboot and on a
 * new day the baseline moves, exactly as the steps tile already handles ([resolveSteps]).
 */
class HealthSampleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        // The tile and widget it serves are gone: stop for good rather than waking the phone for nothing.
        if (!hasHealthSurface(context)) {
            cancel(context)
            return Result.success()
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return Result.success()
        val counter = withTimeoutOrNull(5_000L) { readCounterOnce(context) } ?: return Result.success()
        val baseline = StepsPrefs.readBaseline(context)
        val today = LocalDate.now().toEpochDay()
        val resolution = resolveSteps(counter, baseline, today)
        if (resolution.newBaseline != baseline) StepsPrefs.saveBaseline(context, resolution.newBaseline)
        HealthStore.record(context, today, resolution.stepsToday)
        return Result.success()
    }

    private suspend fun readCounterOnce(context: Context): Float? = suspendCancellableCoroutine { cont ->
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

    companion object {
        private const val UNIQUE = "health_sample"

        /** Keeps the hourly reading going (idempotent); called when a health tile or the hub is on screen. */
        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE,
                // UPDATE so an install that had the old half-hourly job moves to the hourly one.
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<HealthSampleWorker>(60, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE)
        }

        /** Whether anything still reads steps: a health or steps tile on Start (in a folder too), or a steps widget. */
        private suspend fun hasHealthSurface(context: Context): Boolean {
            val onStart = runCatching {
                LayoutRepository.create(context).tiles.first().any { tile ->
                    when (tile) {
                        is TileModel.App -> tile.iconKey == "healthhub" || tile.iconKey == "steps"
                        is TileModel.Folder -> tile.children.any { it.iconKey == "healthhub" || it.iconKey == "steps" }
                    }
                }
            }.getOrDefault(true)
            if (onStart) return true
            return runCatching {
                AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, StepsAppWidgetProvider::class.java)).isNotEmpty()
            }.getOrDefault(true)
        }
    }
}
