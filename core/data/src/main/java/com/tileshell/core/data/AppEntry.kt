package com.tileshell.core.data

import android.content.ComponentName

/**
 * One launchable app activity, as surfaced to the app list and Start screen.
 *
 * @property packageName owning package, e.g. "com.android.chrome"
 * @property activityName fully-qualified launcher activity class name
 * @property label user-visible app name
 * @property letter alphabetical section key — uppercase A–Z, or "#" for
 *   anything that does not start with a letter (digits, symbols)
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val letter: String = AppCatalog.letterFor(label),
) {
    /** ComponentName used to launch this activity via LauncherApps. */
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)
}
