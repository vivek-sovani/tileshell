package com.tileshell.feature.livetiles

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.TaskRepository
import com.tileshell.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Once a day, clears completed tasks from the Tasks tile's checklist — never
 * active/unfinished ones (that's the separate, manual "clear all" action in
 * `TaskListSheet`). Gated on `LauncherSettings.taskAutoClearDaily` inside
 * [doWork] itself (checked fresh on every run) rather than by
 * enqueueing/cancelling the periodic job when the toggle flips — simpler, and
 * [ensureScheduled] can then just always be called, unconditionally, the same
 * way [WeatherRefreshWorker.ensureScheduled] is.
 */
class TaskDailyResetWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository.create(applicationContext)
        if (!settings.settings.first().taskAutoClearDaily) return Result.success()
        // Across every pinned Tasks tile/gadget's own list — the auto-clear
        // toggle is one global setting, not one per list.
        TaskRepository.create(applicationContext).clearCompletedEverywhere()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_task_daily_reset"

        /** Ensure the daily clear is enqueued (idempotent, KEEP). Safe to call every time the Tasks tile renders. */
        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TaskDailyResetWorker>(24, TimeUnit.HOURS).build(),
            )
        }
    }
}
