package com.tileshell.core.data

import android.content.Context

/**
 * Persists the steps-tile's day baseline: the device's raw step-counter sensor
 * reading (`Sensor.TYPE_STEP_COUNTER`, a running total since last boot) plus
 * the epoch day it was captured on. "Steps today" is always `current sensor
 * reading - baseline`, recomputed by [com.tileshell.feature.livetiles.
 * resolveSteps] — this store only remembers where that subtraction should
 * start from, the same "small SharedPreferences fact, not worth a DataStore"
 * shape as [CachedScreenshotPrefs].
 */
object StepsPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_BASELINE_COUNTER = "steps_baseline_counter"
    private const val KEY_BASELINE_EPOCH_DAY = "steps_baseline_epoch_day"

    data class Baseline(val counter: Float, val epochDay: Long)

    /** Null the very first time this device's steps tile has ever read the sensor. */
    fun readBaseline(context: Context): Baseline? {
        val p = prefs(context)
        if (!p.contains(KEY_BASELINE_COUNTER)) return null
        return Baseline(
            counter = p.getFloat(KEY_BASELINE_COUNTER, 0f),
            epochDay = p.getLong(KEY_BASELINE_EPOCH_DAY, 0L),
        )
    }

    fun saveBaseline(context: Context, baseline: Baseline) {
        prefs(context).edit()
            .putFloat(KEY_BASELINE_COUNTER, baseline.counter)
            .putLong(KEY_BASELINE_EPOCH_DAY, baseline.epochDay)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
