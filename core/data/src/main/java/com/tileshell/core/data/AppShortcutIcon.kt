package com.tileshell.core.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process

/**
 * The icon an app shortcut publishes for itself — a camera's "selfie" or
 * "video" glyph rather than the plain camera icon.
 *
 * A pinned shortcut stores `"shortcut:<id>"` in its tile's `activityName`
 * ([AppShortcutTile]), and `':'` is not a legal character in a Java class name,
 * so `ComponentName(pkg, "shortcut:x")` can never resolve. Every icon loader in
 * the app therefore threw `NameNotFoundException` on `getActivityIcon` for
 * these and silently fell through to `getApplicationIcon` — a recovery path
 * written for dead seasonal activity-aliases — which is why every shortcut,
 * in the App List submenu and once pinned to Start, showed its parent app's
 * icon instead of its own.
 *
 * Lives here rather than in a feature module because all three icon-loading
 * sites need it ([com.tileshell.feature.applist]'s `AppListIcon`, and
 * `:feature:start`'s `IconCellView` and tile icon loader), and unlike the
 * Compose masking code around them this part is plain Android with nothing to
 * duplicate.
 *
 * Returns null for anything that isn't a shortcut sentinel, when shortcut-host
 * permission is absent (TileShell is not the default Home app), or when the
 * shortcut has since been removed — every caller already has an icon fallback
 * for those cases.
 */
fun shortcutIconDrawable(context: Context, packageName: String, activityName: String): Drawable? {
    val shortcutId = AppShortcutTile.decode(activityName) ?: return null
    if (packageName.isBlank()) return null
    val launcherApps = runCatching {
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    }.getOrNull() ?: return null
    if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return null

    val query = LauncherApps.ShortcutQuery()
        .setPackage(packageName)
        .setShortcutIds(listOf(shortcutId))
        .setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
    val shortcut = runCatching { launcherApps.getShortcuts(query, Process.myUserHandle()) }
        .getOrNull()
        ?.firstOrNull()
        ?: return null
    return runCatching {
        launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
    }.getOrNull()
}
