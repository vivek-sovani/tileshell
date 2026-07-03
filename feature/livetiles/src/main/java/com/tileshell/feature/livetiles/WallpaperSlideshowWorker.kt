package com.tileshell.feature.livetiles

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Rotates the wallpaper through the user-picked slideshow photos (opt-in via
 * personalize). Each run advances to the next photo in [WallpaperSlideshowStore]
 * (wrapping around) and writes it into the existing custom-wallpaper render path
 * via [SettingsRepository.setWallpaperSlide] — no new rendering code needed.
 * Bails out cleanly if the slideshow has since been turned off or no photos are
 * picked, so a stale scheduled run is a harmless no-op (mirrors `BingWallpaperWorker`'s
 * own since-toggled-off guard).
 */
class WallpaperSlideshowWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsRepository.create(applicationContext)
        val current = settings.settings.first()
        if (!current.wallpaperSlideshowEnabled) return@withContext Result.success()
        val uris = WallpaperSlideshowStore.create(applicationContext).read().uris
        if (uris.isEmpty()) return@withContext Result.success()
        val nextIndex = (current.wallpaperSlideshowIndex + 1).mod(uris.size)
        settings.setWallpaperSlide(uris[nextIndex], nextIndex)
        Result.success()
    }

    companion object {
        private const val UNIQUE_PERIODIC = "tileshell_wallpaper_slideshow"

        /**
         * (Re)schedules the periodic rotation at [intervalMin] (clamped to
         * WorkManager's 15-minute periodic floor). [ExistingPeriodicWorkPolicy.UPDATE]
         * lets a changed interval take effect in place, without a cancel/re-enqueue
         * race; safe to call on every toggle-on or interval change.
         */
        fun ensureScheduled(context: Context, intervalMin: Int) {
            val minutes = intervalMin.toLong().coerceAtLeast(15L)
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WallpaperSlideshowWorker>(minutes, TimeUnit.MINUTES).build(),
            )
        }

        /** Cancel the rotation (slideshow turned off). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        }
    }
}
