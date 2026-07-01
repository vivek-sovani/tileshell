package com.tileshell.feature.livetiles

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.BackupManager
import com.tileshell.core.data.CachedScreenshotPrefs
import com.tileshell.core.data.LayoutHistoryRepository
import com.tileshell.core.data.LayoutRepository
import com.tileshell.core.data.LayoutSnapshot
import com.tileshell.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that appends a layout snapshot to [LayoutHistoryRepository].
 * Only saves when the layout has changed since the last snapshot (hash-based dedup
 * inside [LayoutHistoryRepository.addSnapshot]). Skipped when auto-backup is disabled
 * or interval is changed — the companion re-schedules on every settings write.
 */
class LayoutAutoBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val settings = SettingsRepository.create(applicationContext).settings.first()
        if (!settings.autoBackupEnabled) return Result.success()

        val layoutRepo = LayoutRepository.create(applicationContext)
        val historyRepo = LayoutHistoryRepository(applicationContext)

        val (tiles, folders, children) = layoutRepo.tilesForBackup()
        val json = BackupManager.buildBackupJson(tiles, folders, children, settings)
        val hash = BackupManager.layoutHash(tiles, folders, children)
        val now = System.currentTimeMillis()

        // PixelCopy needs a live, on-screen window, which this headless worker never has —
        // reuse whatever Start last cached while it was actually visible, but only if the
        // layout hasn't changed since (a stale screenshot would misrepresent this snapshot).
        val screenshotPath = CachedScreenshotPrefs.pathFor(applicationContext, hash)

        historyRepo.addSnapshot(
            LayoutSnapshot(
                id = now.toString(),
                timestamp = now,
                label = "auto",
                tileCount = tiles.size,
                folderCount = folders.size,
                contentHash = hash,
                json = json,
                screenshotPath = screenshotPath,
            )
        )
        Result.success()
    }.getOrElse { Result.failure() }

    companion object {
        private const val UNIQUE_NAME = "tileshell_layout_auto_backup"

        fun schedule(context: Context, intervalHours: Int) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LayoutAutoBackupWorker>(
                    intervalHours.toLong(), TimeUnit.HOURS
                ).setConstraints(Constraints.NONE).build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
