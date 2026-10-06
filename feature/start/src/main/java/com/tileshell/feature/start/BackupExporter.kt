package com.tileshell.feature.start

import android.content.Context
import com.tileshell.core.data.BackupFeedSource
import com.tileshell.core.data.BackupManager
import com.tileshell.core.data.BackupWidget
import com.tileshell.core.data.HiddenApps
import com.tileshell.core.data.LayoutRepository
import com.tileshell.core.data.UserContentBackup
import com.tileshell.core.data.settings.SettingsRepository
import com.tileshell.feature.livetiles.BackupExtras
import com.tileshell.feature.livetiles.FeedStore
import com.tileshell.feature.livetiles.PhotosStore
import com.tileshell.feature.livetiles.WallpaperSlideshowStore
import com.tileshell.feature.start.feed.WidgetStore
import kotlinx.coroutines.flow.first

/**
 * Builds the full manual-export backup file (layout, settings, hidden apps,
 * feed, glance widgets, notes, tasks, music data, small preferences). Shared
 * by the "export layout" button and the scheduled auto-export, so both
 * produce exactly the same file.
 */
object BackupExporter {
    suspend fun buildJson(context: Context): String {
        val app = context.applicationContext
        val (tiles, folders, children, sections) = LayoutRepository.create(app).tilesForBackup()
        val settings = SettingsRepository.create(app).settings.first()
        val feed = FeedStore.create(app).read()
        val widgets = WidgetStore.create(app).read().widgets
        val content = UserContentBackup.read(app)
        return BackupManager.buildBackupJson(
            tiles, folders, children, settings,
            hiddenApps = HiddenApps.hidden(app).first(),
            feedSources = feed.sources.map { BackupFeedSource(it.url, it.name, it.category, it.enabled) },
            feedRegions = feed.regions,
            widgets = widgets.map { BackupWidget(it.widgetId, it.heightDp, it.widthDp, it.halfWidth, it.stackId) },
            photoUris = PhotosStore.create(app).read().uris,
            wallpaperSlideshowUris = WallpaperSlideshowStore.create(app).read().uris,
            sections = sections,
            notes = content.notes,
            taskLists = content.taskLists,
            tasks = content.tasks,
            extras = BackupExtras.export(app),
        )
    }
}
