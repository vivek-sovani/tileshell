package com.tileshell.core.data.clock

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** How hard the buzz at the end of a step is. */
enum class BuzzStrength(val label: String) { GENTLE("gentle"), SHORT("short"), STRONG("strong") }

/** A vibration: pauses and pulses in milliseconds (pause first) with each pulse's strength (0 for a pause). */
data class BuzzPattern(val timings: LongArray, val amplitudes: IntArray)

/**
 * The buzz for the end of a step, or (with [finish]) of the whole set: a
 * single pulse for a step, a longer double pulse at the very end so the last
 * one is unmistakable. Pure.
 */
fun buzzPattern(strength: BuzzStrength, finish: Boolean): BuzzPattern {
    val (ms, amplitude) = when (strength) {
        BuzzStrength.GENTLE -> 150L to 80
        BuzzStrength.SHORT -> 250L to 160
        BuzzStrength.STRONG -> 500L to 255
    }
    return if (finish) {
        BuzzPattern(longArrayOf(0, ms, 150, ms, 150, ms * 2), intArrayOf(0, amplitude, 0, amplitude, 0, amplitude))
    } else {
        BuzzPattern(longArrayOf(0, ms), intArrayOf(0, amplitude))
    }
}

/**
 * Vibrates for the end of a step. Marked as an alarm vibration so it is felt
 * with the screen off and from a background receiver, and follows the phone's
 * own vibration and do-not-disturb settings. Needs only the normal VIBRATE
 * permission. Never throws: a phone without a vibrator just stays quiet.
 */
object ClockBuzz {
    @Suppress("DEPRECATION")
    fun buzz(context: Context, strength: BuzzStrength, finish: Boolean) {
        runCatching {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            } ?: return
            if (!vibrator.hasVibrator()) return
            val p = buzzPattern(strength, finish)
            val effect = if (vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(p.timings, p.amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(p.timings, -1)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }
    }
}
