package com.tileshell.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow

/**
 * Source of truth for the set of launchable apps on the device.
 *
 * Enumerates launcher activities via [LauncherApps] (no QUERY_ALL_PACKAGES —
 * launcher apps get this visibility for free) and emits an alphabetically
 * sorted [List]<[AppEntry]>. A [LauncherApps.Callback] acts as the
 * package-change receiver: install / uninstall / update events re-query and
 * push a fresh list to collectors, so the flow stays live.
 */
class AppCatalogRepository(context: Context) {

    private val appContext = context.applicationContext
    private val launcherApps =
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    /** Live, alphabetically sorted catalogue. Cold; re-queries per collector. */
    val apps: Flow<List<AppEntry>> = callbackFlow {
        // Callbacks are delivered on the main looper; the actual query work
        // happens on Dispatchers.Default via flowOn below.
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) {
                trySend(query())
            }

            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                trySend(query())
            }

            override fun onPackageChanged(packageName: String?, user: UserHandle?) {
                trySend(query())
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) {
                trySend(query())
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) {
                trySend(query())
            }
        }

        trySend(query())
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    /** One-shot snapshot of the current catalogue. */
    fun query(): List<AppEntry> {
        val roles = resolveRoles()
        // TileShell's own MainActivity declares LAUNCHER (required to be
        // set-as-home) alongside HOME, so getActivityList's unscoped query
        // would otherwise include it — the launcher listing itself in its
        // own app drawer / recent section.
        val activities = launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != appContext.packageName }
        val entries = activities.map { info ->
            val pkg = info.componentName.packageName
            AppEntry(
                packageName = pkg,
                activityName = info.componentName.className,
                label = info.label?.toString().orEmpty(),
                firstInstallTime = runCatching { info.firstInstallTime }.getOrDefault(0L),
                category = runCatching { info.applicationInfo.category }
                    .getOrDefault(android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED),
                role = roles[pkg],
            )
        }
        return AppCatalog.sorted(entries)
    }

    /**
     * Build a package → standard-app-role map by resolving the platform
     * `Intent.CATEGORY_APP_*` selectors. The OS reports which installed apps
     * declare each role (an email app, a maps app, …), refining the coarse
     * [ApplicationInfo.category] without any hard-coded package list. Best-effort:
     * each query is guarded, and roles the device can't resolve simply don't
     * appear (the classifier then falls back to category / tokens). The
     * `<queries>` block in the app manifest grants the visibility these selectors
     * need on API 30+.
     */
    private fun resolveRoles(): Map<String, String> {
        val pm = appContext.packageManager
        val map = HashMap<String, String>()
        fun tag(category: String, role: String) {
            runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                pm.queryIntentActivities(intent, 0).forEach { ri ->
                    ri.activityInfo?.packageName?.let { map.putIfAbsent(it, role) }
                }
            }
        }
        tag(Intent.CATEGORY_APP_EMAIL, AppCategories.ROLE_EMAIL)
        tag(Intent.CATEGORY_APP_MESSAGING, AppCategories.ROLE_MESSAGING)
        tag(Intent.CATEGORY_APP_CONTACTS, AppCategories.ROLE_CONTACTS)
        tag(Intent.CATEGORY_APP_MUSIC, AppCategories.ROLE_MUSIC)
        tag(Intent.CATEGORY_APP_GALLERY, AppCategories.ROLE_GALLERY)
        tag(Intent.CATEGORY_APP_MAPS, AppCategories.ROLE_MAPS)
        tag(Intent.CATEGORY_APP_BROWSER, AppCategories.ROLE_BROWSER)
        tag(Intent.CATEGORY_APP_CALCULATOR, AppCategories.ROLE_CALCULATOR)
        tag(Intent.CATEGORY_APP_CALENDAR, AppCategories.ROLE_CALENDAR)
        tag(Intent.CATEGORY_APP_MARKET, AppCategories.ROLE_MARKET)
        // API 29+/30+ selectors — harmless no-ops on older devices.
        tag(Intent.CATEGORY_APP_FILES, AppCategories.ROLE_FILES)
        tag(Intent.CATEGORY_APP_WEATHER, AppCategories.ROLE_WEATHER)
        tag(Intent.CATEGORY_APP_FITNESS, AppCategories.ROLE_FITNESS)
        return map
    }
}
