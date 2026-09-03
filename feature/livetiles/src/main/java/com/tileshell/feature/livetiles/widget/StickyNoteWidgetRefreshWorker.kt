package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.feature.livetiles.R

/**
 * Builds + pushes the sticky-note widget's [RemoteViews]. No periodic cadence
 * (see `updatePeriodMillis="0"`) — the text only ever changes via
 * [WidgetConfigureActivity]'s own text-edit step, which already calls
 * [refreshNow] on save.
 */
class StickyNoteWidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        pushAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NOW = "tileshell_stickynote_widget_refresh_now"

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
                OneTimeWorkRequestBuilder<StickyNoteWidgetRefreshWorker>().build(),
            )
        }

        suspend fun pushAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StickyNoteAppWidgetProvider::class.java))
            if (ids.isEmpty()) return

            ids.forEach { id ->
                val minWidthDp = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val (accent, onAccent) = resolveWidgetAccent(context, id)
                val text = WidgetStickyNoteStore.text(context, id)
                val views = buildRemoteViews(context, id, text, accent, onAccent, isCompactWidget(minWidthDp))
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            text: String,
            accent: Int,
            onAccent: Int,
            compact: Boolean,
        ): RemoteViews {
            val layout = if (compact) R.layout.widget_stickynote_compact else R.layout.widget_stickynote
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(R.id.widget_bg, accentGradientBitmap(accent))
            views.setBoolean(R.id.widget_root, "setClipToOutline", true)
            // Both the body and the gear reopen the same "write your note" +
            // colour flow — editing the text *is* this widget's reconfigure.
            views.setOnClickPendingIntent(R.id.widget_root, reconfigurePendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_settings, reconfigurePendingIntent(context, appWidgetId))
            views.setTextColor(R.id.widget_text, onAccent)
            views.setTextViewText(R.id.widget_text, text.ifBlank { "tap to write a note" })
            views.setInt(R.id.widget_icon, "setColorFilter", onAccent)
            return views
        }
    }
}
