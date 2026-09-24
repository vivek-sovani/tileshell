package com.tileshell.feature.livetiles.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tileshell.feature.livetiles.WeatherSnapshot
import java.util.concurrent.TimeUnit

/**
 * The earliest sunrise or sunset strictly after [nowMillis] across every
 * snapshot a placed weather widget shows, or null when none is known (no
 * snapshot, or a cache file from before sun times were stored). Pure.
 */
fun nextSunTransition(nowMillis: Long, snapshots: Collection<WeatherSnapshot>): Long? =
    snapshots.asSequence()
        .flatMap { it.forecast.asSequence() }
        .flatMap { sequenceOf(it.sunriseMillis, it.sunsetMillis) }
        .filterNotNull()
        .filter { it > nowMillis }
        .minOrNull()

/**
 * Repaints the weather widget at sunrise and sunset, so its sun/moon icon
 * switches on time (user-requested). Without this the switch waited for the
 * 30-min periodic push, which [WidgetWork.skipWhileScreenOff] also skips while
 * the screen is off, so after a screen-off evening the widget could keep the
 * sun for up to another interval after the screen came back on.
 *
 * A **non-wakeup** [AlarmManager.RTC] alarm, deliberately: it never wakes the
 * device just to repaint a widget nobody can see, and if the transition passes
 * while the device sleeps (or dozes) the OS delivers it as soon as the device
 * wakes, which is exactly when the widget becomes visible again. [setWindow]
 * rather than [AlarmManager.set], because a plain inexact alarm hours away may
 * be batched far past its time; a 10-min window (the platform minimum on API
 * 31+) needs no exact-alarm permission. Self-perpetuating: every
 * [WeatherWidgetRefreshWorker.pushAll] re-arms it for the next transition, and
 * alarms cleared by a reboot come back via the provider's boot-time onUpdate.
 */
object WeatherSunAlarm {

    private const val WINDOW_MS = 10 * 60 * 1000L

    /** Arms (or replaces) the alarm for the next transition, or cancels it when there is none. */
    fun schedule(context: Context, snapshots: Collection<WeatherSnapshot>) {
        val next = nextSunTransition(System.currentTimeMillis(), snapshots)
            ?: return cancel(context)
        runCatching {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            // A minute past the transition, so the repaint lands on the new side of it.
            am.setWindow(AlarmManager.RTC, next + TimeUnit.MINUTES.toMillis(1), WINDOW_MS, pendingIntent(context))
        }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WeatherSunAlarmReceiver::class.java).setAction(WeatherSunAlarmReceiver.ACTION_SUN_TRANSITION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/**
 * Receives [WeatherSunAlarm]'s alarm and repaints directly inside the
 * broadcast's own wake window ([pushDateRollover]), not via a WorkManager
 * one-off that Doze could defer again — the same lesson as the midnight
 * widgets. `exported="false"`: only our own PendingIntent reaches it.
 */
class WeatherSunAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SUN_TRANSITION) return
        pushDateRollover(context) { WeatherWidgetRefreshWorker.pushAll(it) }
    }

    companion object {
        const val ACTION_SUN_TRANSITION = "com.tileshell.feature.livetiles.widget.ACTION_SUN_TRANSITION"
    }
}
