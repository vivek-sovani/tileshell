package com.tileshell.feature.livetiles.widget

import android.content.Context

/**
 * Per-widget-instance note text, keyed by the real
 * [android.appwidget.AppWidgetManager] id — a sticky note is genuinely one
 * text field per pinned instance, never shared (unlike Notes' single global
 * notepad). Plain `SharedPreferences`, same shape as [WidgetColorStore] — the
 * existing in-app pin path for a sticky note (a Start tile reusing
 * `TileEntity.activityName`) doesn't fit a real, independently-lifecycled
 * widget instance, so this is its own, widget-only store rather than reusing it.
 */
object WidgetStickyNoteStore {
    private const val PREFS = "tileshell_widget_stickynotes"

    fun text(context: Context, appWidgetId: Int): String =
        prefs(context).getString(key(appWidgetId), null) ?: ""

    fun setText(context: Context, appWidgetId: Int, text: String) {
        prefs(context).edit().putString(key(appWidgetId), text).apply()
    }

    /** Called from the provider's `onDeleted` so a removed widget's text doesn't linger forever. */
    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int) = "text_$appWidgetId"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
