package com.tileshell.core.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo

/**
 * One launchable app activity, as surfaced to the app list and Start screen.
 *
 * @property packageName owning package, e.g. "com.android.chrome"
 * @property activityName fully-qualified launcher activity class name
 * @property label user-visible app name
 * @property letter alphabetical section key — uppercase A–Z, or "#" for
 *   anything that does not start with a letter (digits, symbols)
 * @property firstInstallTime epoch-millis the app was first installed (0 when
 *   unknown); drives the "newly installed" top section of the app list.
 * @property category the app's declared [ApplicationInfo] category
 *   (`CATEGORY_SOCIAL`, `CATEGORY_GAME`, …) or `CATEGORY_UNDEFINED` when the app
 *   declares none. Drives the category-folder matcher ([AppCategories]).
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val letter: String = AppCatalog.letterFor(label),
    val firstInstallTime: Long = 0L,
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
) {
    /** Stable key for this launchable component (matches [RecentApps] keys). */
    val key: String get() = "$packageName/$activityName"

    /** ComponentName used to launch this activity via LauncherApps. */
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)
}
