package com.tileshell.core.data

import android.content.Context

/**
 * One-shot flag for the "hide the real Android Settings app from the App
 * List" migration (it's superseded by the Quick Panel's own "android
 * settings" tile). A plain currently-hidden check isn't enough here — the
 * user might deliberately un-hide it again later from Personalize's hidden
 * apps sheet, and re-hiding it on every subsequent launch would undo that
 * choice. This flag ensures the auto-hide happens exactly once, ever.
 */
object SettingsAppMigration {
    private const val PREFS = "tileshell.prefs"
    private const val KEY = "settings_app_hidden_migrated"

    fun hasRun(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markRun(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}
