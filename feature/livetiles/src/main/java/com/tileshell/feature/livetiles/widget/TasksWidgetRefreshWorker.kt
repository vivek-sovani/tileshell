package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.TaskRepository
import com.tileshell.feature.livetiles.R
import com.tileshell.feature.livetiles.tasksSummary
import kotlinx.coroutines.flow.first

/**
 * Builds + pushes the tasks widget's [RemoteViews]. No periodic cadence (see
 * `updatePeriodMillis="0"`) — nothing external ever changes a checklist,
 * only an explicit user action does, and every one of those (a checkbox tap
 * here, or an add/remove/check inside [TaskListWidgetActivity]) already
 * calls [refreshNow] itself.
 */
class TasksWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NOW = "tileshell_tasks_widget_refresh_now"
        private const val MAX_ROWS = 4

        /** No periodic cadence for this widget (see class doc) — this only exists so
         * [WidgetConfigureActivity]'s per-kind dispatch has a consistent shape. */
        fun ensureScheduled(context: Context) {
            refreshNow(context)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NOW)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TasksWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TasksAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val repository = TaskRepository.create(context)
            ids.forEach { id ->
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val tasks = repository.tasks(TasksAppWidgetProvider.listIdFor(id)).first()
                val views = buildRemoteViews(context, id, tasks, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            tasks: List<com.tileshell.core.data.TaskItem>,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_tasks_compact else R.layout.widget_tasks
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))

            val summary = tasksSummary(tasks, maxPreview = MAX_ROWS)
            val headerText = if (summary.totalCount == 0) "no tasks yet" else "${summary.doneCount} of ${summary.totalCount} done"

            if (compact) {
                views.setTextColor(R.id.widget_count, onAccent)
                views.setTextViewText(R.id.widget_count, if (summary.totalCount == 0) "＋" else "${summary.doneCount}/${summary.totalCount}")
                views.setOnClickPendingIntent(R.id.widget_root, TasksAppWidgetProvider.managePendingIntent(context, appWidgetId))
                return views
            }

            views.setTextColor(R.id.widget_header, onAccent)
            views.setTextViewText(R.id.widget_header, headerText)
            views.setProgressBar(
                R.id.widget_progress,
                100,
                if (summary.totalCount == 0) 0 else (summary.doneCount * 100 / summary.totalCount),
                false,
            )
            views.setOnClickPendingIntent(R.id.widget_add, TasksAppWidgetProvider.managePendingIntent(context, appWidgetId))
            views.setInt(R.id.widget_add, "setColorFilter", onAccent)

            val rowIds = intArrayOf(R.id.widget_task_row_0, R.id.widget_task_row_1, R.id.widget_task_row_2, R.id.widget_task_row_3)
            val checkIds = intArrayOf(R.id.widget_task_check_0, R.id.widget_task_check_1, R.id.widget_task_check_2, R.id.widget_task_check_3)
            val textIds = intArrayOf(R.id.widget_task_text_0, R.id.widget_task_text_1, R.id.widget_task_text_2, R.id.widget_task_text_3)

            for (i in 0 until MAX_ROWS) {
                val item = summary.preview.getOrNull(i)
                if (item == null) {
                    views.setViewVisibility(rowIds[i], View.GONE)
                    continue
                }
                views.setViewVisibility(rowIds[i], View.VISIBLE)
                views.setInt(checkIds[i], "setColorFilter", onAccent)
                views.setImageViewResource(
                    checkIds[i],
                    if (item.done) R.drawable.ic_widget_checkbox_checked else R.drawable.ic_widget_checkbox_unchecked,
                )
                views.setTextColor(textIds[i], onAccent)
                val label: CharSequence = if (item.done) {
                    SpannableString(item.text).apply { setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                } else {
                    item.text
                }
                views.setTextViewText(textIds[i], label)
                views.setOnClickPendingIntent(
                    checkIds[i],
                    TasksAppWidgetProvider.togglePendingIntent(context, appWidgetId, item.id, !item.done),
                )
            }
            if (summary.totalCount == 0) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextColor(R.id.widget_empty, onAccent)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
            }
            return views
        }
    }
}
