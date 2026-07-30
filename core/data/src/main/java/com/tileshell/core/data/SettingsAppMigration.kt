package com.tileshell.core.data

import android.content.Context

/**
 * One-shot flag for the "un-hide the real Android Settings app from the App
 * List" migration. An earlier version of this migration hid it there (once
 * the Quick Panel got its own "android settings" tile), which was reversed
 * per later explicit request — it should stay discoverable/pinnable. A plain
 * "is it currently hidden" check isn't the right gate for the reversal: the
 * user might deliberately hide it again themselves later from the App List,
 * and un-hiding it on every subsequent launch would silently undo that
 * choice. This flag ensures the one-time un-hide happens exactly once, ever.
 */
object SettingsAppMigration {
    private const val PREFS = "tileshell.prefs"
    private const val UNHIDE_KEY = "settings_app_unhidden_migrated"

    fun hasUnhideRun(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(UNHIDE_KEY, false)

    fun markUnhideRun(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(UNHIDE_KEY, true).apply()
    }
}
