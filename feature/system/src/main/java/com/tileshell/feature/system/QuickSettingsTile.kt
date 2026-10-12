package com.tileshell.feature.system

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build

/**
 * Asking the system to add TileShell's "personalize" button to the quick settings panel (user-requested).
 * Android never lets an app add a tile by itself: from Android 13 (API 33) it can only ask, and the system shows
 * an "add tile?" dialog the user answers. Earlier versions have no such request, so the user adds it from the
 * panel's edit mode (the tile is declared by `PersonalizeTileService` in `:app`).
 *
 * What is remembered, in the shared launcher prefs: whether the first-run offer was made ([markAsked]; it is made
 * once), and whether the tile is on the panel ([added], kept up to date by the tile service's own add / remove
 * callbacks and by the answer to a request).
 */
object QuickSettingsTile {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_ASKED = "qs_tile_asked"
    private const val KEY_ADDED = "qs_tile_added"

    /** `:app`'s tile service. Named, not referenced, so this module does not depend on `:app`. */
    private const val SERVICE_CLASS = "com.tileshell.PersonalizeTileService"

    /** The system can be asked to add a tile on this Android version. */
    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Offer the tile on first run: only once, only where it can be asked, and not when it is already added. */
    fun shouldOfferFirstRun(supported: Boolean, asked: Boolean, added: Boolean): Boolean = supported && !asked && !added

    fun shouldOfferFirstRun(context: Context): Boolean = shouldOfferFirstRun(supported(), asked(context), added(context))

    /** A row in personalize offers it while it can be asked for and is not on the panel. */
    fun showInPersonalize(context: Context): Boolean = supported() && !added(context)

    fun asked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) = prefs(context).edit().putBoolean(KEY_ASKED, true).apply()

    fun added(context: Context): Boolean = prefs(context).getBoolean(KEY_ADDED, false)

    fun markAdded(context: Context, added: Boolean) = prefs(context).edit().putBoolean(KEY_ADDED, added).apply()

    /**
     * Shows the system's "add tile" dialog. [onResult] gets true when the tile is on the panel afterwards (just
     * added, or already there). Needs TileShell on screen; does nothing below Android 13 or if the system refuses.
     */
    fun request(context: Context, onResult: (added: Boolean) -> Unit = {}) {
        if (!supported()) return
        runCatching {
            val manager = context.getSystemService(StatusBarManager::class.java) ?: return
            val resources = context.resources
            val icon = resources.getIdentifier("ic_qs_personalize", "drawable", context.packageName)
            val label = resources.getIdentifier("qs_personalize_label", "string", context.packageName)
            if (icon == 0 || label == 0) return
            manager.requestAddTileService(
                ComponentName(context.packageName, SERVICE_CLASS),
                context.getString(label),
                Icon.createWithResource(context, icon),
                context.mainExecutor,
            ) { result ->
                val onPanel = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                if (onPanel) markAdded(context, true)
                onResult(onPanel)
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
