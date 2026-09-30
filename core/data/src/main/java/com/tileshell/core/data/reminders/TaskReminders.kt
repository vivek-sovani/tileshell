package com.tileshell.core.data.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.tileshell.core.data.BuildConfig
import com.tileshell.core.data.R
import com.tileshell.core.data.db.TaskEntity
import com.tileshell.core.data.db.TileShellDatabase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A reminder that just went off, for TileShell's own on-screen toast. */
data class DueReminder(val taskId: Long, val text: String, val listId: String, val listName: String, val whenText: String)

/**
 * Task reminders: one exact alarm per open task with a pending reminder,
 * delivered to [TaskReminderReceiver]. [sync] is the only scheduling entry
 * point — it's called after every task write (see `TaskRepository`), at boot
 * and after an app update — and reconciles the alarms with the database.
 *
 * Where an alert shows: Android's heads-up notification when another app or
 * the lock screen is on screen, or TileShell's own Windows Phone–style toast
 * ([toasts]) while Start is showing ([startVisible]); the tasks tiles list
 * due reminders first either way.
 *
 * Exact timing needs the user-granted "Alarms & reminders" access
 * (SCHEDULE_EXACT_ALARM — no Play declaration; USE_EXACT_ALARM is deliberately
 * not used). Without it an alarm is still set inexactly, and the task list
 * says the reminder is off rather than letting it arrive late unannounced.
 */
object TaskReminders {

    /**
     * Kill switch, from gradle.properties → `tileshell.taskReminders`. Off hides
     * the reminder UI, schedules nothing (and cancels anything set earlier), and
     * the app manifest drops SCHEDULE_EXACT_ALARM.
     */
    val ENABLED: Boolean = BuildConfig.TASK_REMINDERS

    /** Extra on TileShell's launch intent: open this task list's sheet. */
    const val EXTRA_OPEN_TASK_LIST = "com.tileshell.extra.OPEN_TASK_LIST"

    const val SNOOZE_MS = 10 * 60_000L

    private const val CHANNEL_ID = "task_reminders"
    // While TileShell is open the toast is the visual alert, so the notification
    // goes to a channel that sounds (the user's own notification sound, volume
    // and Do Not Disturb, resolved by the system) but never pops a heads-up.
    // Playing a sound ourselves failed on Samsung, whose default-sound setting
    // isn't where RingtoneManager looks.
    private const val CHANNEL_IN_APP_ID = "task_reminders_in_app"
    private const val PREFS = "task_reminders"
    private const val KEY_SCHEDULED = "scheduled"

    /** True while Start is resumed — the toast replaces the heads-up then. */
    @Volatile
    var startVisible: Boolean = false

    private val _toasts = MutableSharedFlow<DueReminder>(extraBufferCapacity = 4)
    val toasts: SharedFlow<DueReminder> = _toasts.asSharedFlow()

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** The system "Alarms & reminders" page for TileShell (Android 12+). */
    fun exactAccessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Reconciles the scheduled alarms with the tasks table. Safe to call often. */
    suspend fun sync(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val pending = if (ENABLED) TileShellDatabase.get(app).taskDao().pendingReminders() else emptyList()
        val exact = canScheduleExact(app)
        val now = System.currentTimeMillis()
        pending.forEach { task ->
            // Overdue and never alerted (phone was off, or the time passed while
            // access was missing) → go off right away.
            val at = maxOf(task.remindSnoozeAt ?: task.remindAt ?: return@forEach, now + 1_000L)
            val pi = firePendingIntent(app, task.id)
            runCatching {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        val current = pending.map { it.id.toString() }.toSet()
        (previous - current).forEach { id ->
            id.toLongOrNull()?.let { am.cancel(firePendingIntent(app, it)) }
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, current).apply()
    }

    /** Called by [TaskReminderReceiver] when a reminder's time arrives. */
    internal suspend fun fire(context: Context, taskId: Long) {
        val db = TileShellDatabase.get(context)
        val dao = db.taskDao()
        val task = dao.get(taskId)
        if (task == null || task.done || task.remindAt == null || !ENABLED) {
            sync(context)
            return
        }
        dao.markReminderFired(taskId)
        val listName = db.taskListDao().name(task.listId) ?: "tasks"
        val whenText = reminderClock(java.time.Instant.ofEpochMilli(task.remindAt).atZone(java.time.ZoneId.systemDefault()))
        val onStart = startVisible
        if (onStart) {
            _toasts.tryEmit(DueReminder(task.id, task.text, task.listId, listName, whenText))
        }
        // On Start the toast is the visual alert and the notification only sounds
        // (it still waits in the shade); anywhere else it's a heads-up.
        post(context, task, listName, inApp = onStart)
        sync(context)
    }

    suspend fun snooze(context: Context, taskId: Long) {
        TileShellDatabase.get(context).taskDao().snoozeReminder(taskId, System.currentTimeMillis() + SNOOZE_MS)
        cancelNotification(context, taskId)
        sync(context)
    }

    fun cancelNotification(context: Context, taskId: Long) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(taskId))
    }

    private fun post(context: Context, task: TaskEntity, listName: String, inApp: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!notificationsAllowed(context)) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "alerts when a task's reminder time arrives"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_IN_APP_ID, "task reminders while tileshell is open", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "the sound for a reminder shown as a toast on start"
            },
        )
        val repeat = TaskRepeat.decode(task.remindRepeat)
        val subtitle = if (repeat.label.isEmpty()) listName.lowercase() else "${listName.lowercase()} · repeats ${repeat.label}"
        val notification = Notification.Builder(context, if (inApp) CHANNEL_IN_APP_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_task)
            .setContentTitle(task.text)
            .setContentText(subtitle)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent(context, task))
            .addAction(Notification.Action.Builder(null, "done", actionPendingIntent(context, task.id, TaskReminderReceiver.ACTION_DONE)).build())
            .addAction(Notification.Action.Builder(null, "snooze 10 min", actionPendingIntent(context, task.id, TaskReminderReceiver.ACTION_SNOOZE)).build())
            .addAction(Notification.Action.Builder(null, "open", openPendingIntent(context, task)).build())
            .build()
        runCatching { nm.notify(notificationId(task.id), notification) }
    }

    /** The launch intent that opens [listId]'s task sheet on Start. */
    fun openIntent(context: Context, listId: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_OPEN_TASK_LIST, listId)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun openPendingIntent(context: Context, task: TaskEntity): PendingIntent? {
        val intent = openIntent(context, task.listId) ?: return null
        return PendingIntent.getActivity(
            context, notificationId(task.id), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun firePendingIntent(context: Context, taskId: Long): PendingIntent =
        actionPendingIntent(context, taskId, TaskReminderReceiver.ACTION_FIRE)

    private fun actionPendingIntent(context: Context, taskId: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // One request code per (task, action) so the three never overwrite each other.
            (taskId * 3 + TaskReminderReceiver.ACTIONS.indexOf(action)).toInt(),
            Intent(context, TaskReminderReceiver::class.java)
                .setAction(action)
                .putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationId(taskId: Long): Int = (0x7A5C0000 + (taskId and 0xFFFF)).toInt()
}
