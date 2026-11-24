package com.tileshell.feature.livetiles.health

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The optional bedtime reminder (off by default; the time is chosen in the health hub's settings). One alarm for the
 * next bedtime, exact when the "alarms & reminders" access is granted, else inexact; the receiver posts the
 * notification and sets the next day's. Re-armed after a restart and whenever the hub or its sampler runs.
 */
object SleepReminder {
    private const val CHANNEL = "bedtime"
    private const val ID = 7422

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ID, Intent(context, SleepReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets or cancels the alarm to match the setting. */
    fun sync(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val minute = HealthPrefs.current(app).bedtimeMinute
        if (minute < 0) {
            am.cancel(pending(app))
            return
        }
        val at = nextOccurrence(minute, System.currentTimeMillis())
        runCatching {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(app))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(app))
        }
    }

    internal fun post(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "bedtime reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "a nudge at the bedtime you chose in the health hub"
        })
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openPi = open?.let { PendingIntent.getActivity(context, ID, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("bedtime")
            .setContentText("time to wind down")
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .build()
        runCatching { nm.notify(ID, n) }
    }
}

class SleepReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (HealthPrefs.current(context).bedtimeMinute >= 0) SleepReminder.post(context)
        SleepReminder.sync(context)
    }
}

class SleepReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SleepReminder.sync(context)
    }
}
