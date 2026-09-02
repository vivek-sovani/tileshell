package com.tileshell.feature.livetiles.widget

import android.content.Context

/**
 * Per-widget-instance colour override, keyed by the real
 * [android.appwidget.AppWidgetManager] id — the "colour picker just like the
 * launcher tile" ask: a widget-only user (never opens TileShell's Personalize
 * sheet) can still give each placed widget its own accent instead of only
 * ever inheriting the single global one. Plain `SharedPreferences`, same
 * shape as [com.tileshell.core.data.StepsPrefs] — no DataStore needed for a
 * handful of int-keyed string values. Absent id/entry means "no override" —
 * every widget's own push falls back to the global accent in that case.
 */
object WidgetColorStore {
    private const val PREFS = "tileshell_widget_colors"

    fun colorId(context: Context, appWidgetId: Int): String? =
        prefs(context).getString(key(appWidgetId), null)

    fun setColorId(context: Context, appWidgetId: Int, colorId: String?) {
        prefs(context).edit().apply {
            if (colorId == null) remove(key(appWidgetId)) else putString(key(appWidgetId), colorId)
        }.apply()
    }

    /** Called from a provider's `onDeleted` so a removed widget's override doesn't linger forever. */
    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int) = "color_$appWidgetId"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
