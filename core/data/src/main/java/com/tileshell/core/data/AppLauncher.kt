package com.tileshell.core.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process

/** Launches installed apps via [LauncherApps] (the launcher-blessed path). */
object AppLauncher {

    /**
     * Start an app's main activity, or — when [activityName] encodes a pinned
     * app shortcut ([AppShortcutTile]) — that shortcut instead, via
     * [LauncherApps.startShortcut]. Returns false if it can't be started
     * (uninstalled mid-flight, disabled, the shortcut itself removed, etc.) so
     * callers can show a fallback; a dead shortcut deliberately does *not*
     * fall back to the app's main activity, since that would silently launch
     * the wrong thing for a tile the user specifically pinned as a shortcut.
     *
     * Some apps (Flipkart, Myntra, etc.) launch via a seasonal activity-alias
     * that they disable once the sale/event ends — the exact component a tile
     * was pinned with can go dead while the app itself is still installed and
     * launchable. When the stored component fails, fall back to whichever
     * activity currently resolves as the package's own launcher entry point,
     * rather than leaving the tile permanently non-functional.
     */
    fun launch(context: Context, packageName: String, activityName: String): Boolean {
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        AppShortcutTile.decode(activityName)?.let { shortcutId ->
            val ok = try {
                launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
                true
            } catch (e: Exception) {
                false
            }
            if (ok) RecentApps.record(context, packageName, activityName)
            return ok
        }
        val started = try {
            launcherApps.startMainActivity(
                ComponentName(packageName, activityName),
                Process.myUserHandle(),
                null,
                null,
            )
            true
        } catch (e: Exception) {
            false
        }
        if (!started) {
            val fallback = try {
                launcherApps.getActivityList(packageName, Process.myUserHandle()).firstOrNull()
            } catch (e: Exception) {
                null
            }
            if (fallback == null) return false
            try {
                launcherApps.startMainActivity(fallback.componentName, Process.myUserHandle(), null, null)
            } catch (e: Exception) {
                return false
            }
        }
        // Track the launch for the app list's "recent" section (fire-and-forget).
        RecentApps.record(context, packageName, activityName)
        return true
    }
}
