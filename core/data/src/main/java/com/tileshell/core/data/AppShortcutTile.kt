package com.tileshell.core.data

/**
 * A pinned app shortcut (e.g. a camera app's "selfie"/"video"/"portrait" quick
 * actions, or Amazon's launcher-activity-alias sub-apps' own shortcuts) renders
 * as a plain [TileModel.App] with the shortcut's *owning* [TileModel.App.packageName]
 * — real, unlike [ContactTile]'s blank one — but its
 * [TileModel.App.activityName] encodes the shortcut id instead of a real
 * launcher activity class name, the same "encode identity into activityName"
 * trick [ContactTile] uses to avoid a schema change: the tile gets merge/
 * resize/drag/accent-override for free by reusing the App tile machinery, and
 * [AppLauncher.launch] branches on [decode] to start the shortcut instead of
 * a main activity.
 */
object AppShortcutTile {
    private const val PREFIX = "shortcut:"

    /** Encodes [shortcutId] into an `activityName`-shaped string. */
    fun encode(shortcutId: String): String = "$PREFIX$shortcutId"

    /** Decodes an `activityName` back to a shortcut id, or null if it isn't one. */
    fun decode(activityName: String): String? =
        if (activityName.startsWith(PREFIX)) activityName.removePrefix(PREFIX).takeIf { it.isNotEmpty() } else null
}
