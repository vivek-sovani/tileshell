package com.tileshell.feature.livetiles.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.tileshell.core.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen tasks widget. Each placed instance keeps its own independent
 * checklist, keyed by its own real `appWidgetId` — same "each pin gets a
 * fresh list" convention `TaskRepository`'s `listId` already uses for a
 * Start tile (`"live-$appId-<ts>"`) or a glance-page gadget
 * (`hw.widgetId.toString()`, a synthetic negative int); a real widget's own
 * `appWidgetId` is always a distinct positive int, so `appWidgetId.toString()`
 * can never collide with either existing form.
 *
 * A checkbox row's tap toggles that one task directly, with the target *done*
 * state baked into each row's own [PendingIntent] at build time (see
 * `TasksWidgetRefreshWorker.buildRemoteViews`), so the receiver never needs to
 * re-query the task's current state before flipping it. Those intents are
 * handled by [TaskWidgetActionReceiver], a separate non-exported receiver —
 * they used to be intercepted by an `onReceive` override here, which, because a
 * provider must be exported, let any installed app delete or complete the
 * user's tasks by guessing a sequential task id. See that class for details.
 */
class TasksAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        TasksWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        TasksWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        TasksWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        TasksWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { id ->
                WidgetColorStore.clear(context, id)
                runCatching { TaskRepository.create(context).clearAll(id.toString()) }
            }
            pending.finish()
        }
    }

    companion object {
        /** listId for this widget instance's own independent checklist. */
        fun listIdFor(appWidgetId: Int): String = appWidgetId.toString()

        fun togglePendingIntent(context: Context, appWidgetId: Int, taskId: Long, targetDone: Boolean): PendingIntent {
            // Targets the private TaskWidgetActionReceiver, not this exported
            // provider — see that class for why. A PendingIntent is dispatched
            // with this app's own identity, so a non-exported target is fine.
            val intent = Intent(context, TaskWidgetActionReceiver::class.java)
                .setAction(TaskWidgetActionReceiver.ACTION_TOGGLE_TASK)
                .putExtra(TaskWidgetActionReceiver.EXTRA_TASK_ID, taskId)
                .putExtra(TaskWidgetActionReceiver.EXTRA_TARGET_DONE, targetDone)
            // requestCode combines the widget id and task id so two different
            // rows (or the same row on two different widgets) never collide
            // onto the same PendingIntent — PendingIntent equality ignores
            // extras, only action/data/component/categories/request code.
            return PendingIntent.getBroadcast(
                context,
                (appWidgetId.toLong() * 100000 + taskId).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun deletePendingIntent(context: Context, appWidgetId: Int, taskId: Long): PendingIntent {
            val intent = Intent(context, TaskWidgetActionReceiver::class.java)
                .setAction(TaskWidgetActionReceiver.ACTION_DELETE_TASK)
                .putExtra(TaskWidgetActionReceiver.EXTRA_TASK_ID, taskId)
            // Distinct action from togglePendingIntent already keeps these from
            // colliding even where the requestCode arithmetic overlaps — see
            // that function's own doc comment.
            return PendingIntent.getBroadcast(
                context,
                (appWidgetId.toLong() * 100000 + taskId).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun managePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, TaskListWidgetActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
