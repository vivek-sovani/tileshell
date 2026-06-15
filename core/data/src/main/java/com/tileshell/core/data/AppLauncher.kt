package com.tileshell.core.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process

/** Launches installed apps via [LauncherApps] (the launcher-blessed path). */
object AppLauncher {

    /**
     * Start an app's main activity. Returns false if it can't be started
     * (uninstalled mid-flight, disabled, etc.) so callers can show a fallback.
     */
    fun launch(context: Context, packageName: String, activityName: String): Boolean = try {
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.startMainActivity(
            ComponentName(packageName, activityName),
            Process.myUserHandle(),
            null,
            null,
        )
        // Track the launch for the app list's "recent" section (fire-and-forget).
        RecentApps.record(context, packageName, activityName)
        true
    } catch (e: Exception) {
        false
    }
}
