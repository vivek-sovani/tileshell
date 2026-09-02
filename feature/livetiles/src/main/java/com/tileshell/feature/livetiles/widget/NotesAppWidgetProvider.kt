package com.tileshell.feature.livetiles.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Home-screen notes widget — one shared global notepad (see
 * [com.tileshell.core.data.NoteRepository]'s own doc comment: notes are never
 * per-instance, unlike Tasks), so unlike [TasksAppWidgetProvider]'s
 * [onDeleted] this never deletes any data, only the placed instance's own
 * colour override. There's no per-row RemoteViews interaction here (that
 * would need the heavier `RemoteViewsService` collection-widget API) — the
 * whole body opens [NotesWidgetActivity] for add/edit/delete.
 */
class NotesAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        NotesWidgetRefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        NotesWidgetRefreshWorker.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        NotesWidgetRefreshWorker.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        NotesWidgetRefreshWorker.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetColorStore.clear(context, it) }
    }

    companion object {
        fun managePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, NotesWidgetActivity::class.java)
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
