package com.tileshell.feature.livetiles.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Home-screen sticky-note widget — one text field per pinned instance (see
 * [WidgetStickyNoteStore]). Unlike Tasks/Notes there's no per-row/whole-body
 * RemoteViews interaction beyond reopening [WidgetConfigureActivity] (both
 * the body and the gear route there — see `StickyNoteWidgetRefreshWorker`):
 * writing the note text *is* this widget's own "required first step" before
 * the shared colour picker, and the same step is how you edit it again later.
 */
class StickyNoteAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        StickyNoteWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        StickyNoteWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        StickyNoteWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        StickyNoteWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            WidgetColorStore.clear(context, id)
            WidgetStickyNoteStore.clear(context, id)
        }
    }
}
