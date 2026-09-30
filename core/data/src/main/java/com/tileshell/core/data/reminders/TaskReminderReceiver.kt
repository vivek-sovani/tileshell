package com.tileshell.core.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tileshell.core.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Private receiver for task reminders: the alarm going off ([ACTION_FIRE]) and
 * the notification's done / snooze buttons. Only reached through TileShell's
 * own immutable PendingIntents (exported=false).
 */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> TaskReminders.fire(app, taskId)
                    ACTION_DONE -> {
                        TaskReminders.cancelNotification(app, taskId)
                        TaskRepository.create(app).setDone(taskId, true)
                    }
                    ACTION_SNOOZE -> TaskReminders.snooze(app, taskId)
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.tileshell.action.TASK_REMINDER_FIRE"
        const val ACTION_DONE = "com.tileshell.action.TASK_REMINDER_DONE"
        const val ACTION_SNOOZE = "com.tileshell.action.TASK_REMINDER_SNOOZE"
        internal val ACTIONS = listOf(ACTION_FIRE, ACTION_DONE, ACTION_SNOOZE)
        const val EXTRA_TASK_ID = "taskId"
    }
}

/**
 * Alarms don't survive a reboot or an app update, so both re-run
 * [TaskReminders.sync]; an overdue reminder that never alerted goes off then.
 * Both broadcasts are protected (only the system can send them).
 */
class TaskReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TaskReminders.sync(app)
            } finally {
                result.finish()
            }
        }
    }
}
