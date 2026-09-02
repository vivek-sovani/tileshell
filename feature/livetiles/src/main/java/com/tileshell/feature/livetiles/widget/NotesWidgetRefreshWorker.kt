package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.NoteRepository
import com.tileshell.core.data.notePreview
import com.tileshell.feature.livetiles.R
import kotlinx.coroutines.flow.first

/**
 * Builds + pushes the notes widget's [RemoteViews]. No periodic cadence (see
 * `updatePeriodMillis="0"`) — the shared notepad only ever changes via an
 * explicit add/edit/delete inside [NotesWidgetActivity] (or the in-app Notes
 * sheet, not cascaded to this widget yet — see the "deliberately deferred"
 * note in this feature's own plan), each of which already calls [refreshNow]
 * itself.
 */
class NotesWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NOW = "tileshell_notes_widget_refresh_now"

        // Matches the number of widget_note_row_N slots actually declared in
        // widget_notes.xml — the ceiling listWidgetRowsForHeight can return.
        private const val MAX_ROW_SLOTS = 6

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
                OneTimeWorkRequestBuilder<NotesWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NotesAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val notes = NoteRepository.create(context).notes.first()
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val views = buildRemoteViews(context, id, notes, accent, onAccent, isCompactWidget(minWidthDp), listWidgetRowsForHeight(minHeightDp))
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            notes: List<com.tileshell.core.data.NoteItem>,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
            maxRows: Int,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_notes_compact else R.layout.widget_notes
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            views.setOnClickPendingIntent(R.id.widget_root, NotesAppWidgetProvider.managePendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))

            val backStatus = if (notes.isEmpty()) "no notes yet — tap to add one" else "${notes.size} note${if (notes.size == 1) "" else "s"} total"
            views.setTextColor(R.id.widget_back_status, onAccent)
            views.setTextViewText(R.id.widget_back_status, backStatus)

            if (compact) {
                views.setTextColor(R.id.widget_count, onAccent)
                views.setTextViewText(R.id.widget_count, if (notes.isEmpty()) "＋" else notes.size.toString())
                return views
            }

            // A persistent, clearly-labeled "+ add note" row, same as the
            // tasks widget's own add row — a discoverable affordance
            // alongside the whole-body tap, instead of relying on that
            // alone once a few notes already fill the widget.
            views.setOnClickPendingIntent(R.id.widget_add_row, NotesAppWidgetProvider.managePendingIntent(context, appWidgetId))
            views.setTextColor(R.id.widget_add_label, onAccent)
            views.setInt(R.id.widget_add_icon, "setColorFilter", onAccent)

            val titleIds = intArrayOf(R.id.widget_note_title_0, R.id.widget_note_title_1, R.id.widget_note_title_2, R.id.widget_note_title_3, R.id.widget_note_title_4, R.id.widget_note_title_5)
            val snippetIds = intArrayOf(R.id.widget_note_snippet_0, R.id.widget_note_snippet_1, R.id.widget_note_snippet_2, R.id.widget_note_snippet_3, R.id.widget_note_snippet_4, R.id.widget_note_snippet_5)
            val rowIds = intArrayOf(R.id.widget_note_row_0, R.id.widget_note_row_1, R.id.widget_note_row_2, R.id.widget_note_row_3, R.id.widget_note_row_4, R.id.widget_note_row_5)

            if (notes.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextColor(R.id.widget_empty, onAccent)
                for (i in 0 until MAX_ROW_SLOTS) views.setViewVisibility(rowIds[i], View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                // Beyond MAX_ROW_SLOTS every row past `maxRows` (this resize's
                // own row budget) stays hidden — a widget shrunk back down
                // after once being taller must not leave stale rows showing.
                for (i in 0 until MAX_ROW_SLOTS) {
                    val note = if (i < maxRows) notes.getOrNull(i) else null
                    if (note == null) {
                        views.setViewVisibility(rowIds[i], View.GONE)
                        continue
                    }
                    views.setViewVisibility(rowIds[i], View.VISIBLE)
                    val preview = notePreview(note.text)
                    views.setTextColor(titleIds[i], onAccent)
                    views.setTextViewText(titleIds[i], preview.title)
                    views.setTextColor(snippetIds[i], onAccent)
                    views.setViewVisibility(snippetIds[i], if (preview.snippet.isBlank()) View.GONE else View.VISIBLE)
                    views.setTextViewText(snippetIds[i], preview.snippet)
                }
            }
            return views
        }
    }
}
