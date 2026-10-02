package com.tileshell.feature.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Haptic strength (keyboard settings), per the build spec: light / medium / strong. */
enum class HapticStrength(val label: String, val millis: Long, val amplitude: Int) {
    LIGHT("light", 8, 70),
    MEDIUM("medium", 12, 150),
    STRONG("strong", 18, 255),
}

/** The build spec's three feedbacks, plus a faint tick for each cursor step. */
enum class HapticKind { KEY_TAP, LONG_PRESS, CONFIRM, TICK }

/**
 * Key feedback at the chosen strength. The view's own haptic constants can't
 * be made stronger or lighter, so this uses the vibrator directly, marked as
 * touch feedback so Android's own touch-vibration setting still applies.
 */
class KeyHaptics(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    fun play(kind: HapticKind, strength: HapticStrength) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val ms = strength.millis
        val amp = strength.amplitude
        val effect = when (kind) {
            HapticKind.KEY_TAP -> VibrationEffect.createOneShot(ms, amp)
            HapticKind.LONG_PRESS -> VibrationEffect.createOneShot(ms * 2, (amp * 1.3f).toInt().coerceAtMost(255))
            // A soft double tap when a swiped word is placed.
            HapticKind.CONFIRM -> VibrationEffect.createWaveform(longArrayOf(0, ms, 60, ms), intArrayOf(0, amp, 0, amp), -1)
            HapticKind.TICK -> VibrationEffect.createOneShot(4, (amp / 2).coerceAtLeast(1))
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(
                    effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build(),
                )
            }
        }
    }
}
