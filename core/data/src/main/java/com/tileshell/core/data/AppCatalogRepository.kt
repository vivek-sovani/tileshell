package com.tileshell.core.data

import android.content.Context
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
        val activities = launcherApps.getActivityList(null, Process.myUserHandle())
        val entries = activities.map { info ->
            AppEntry(
                packageName = info.componentName.packageName,
                activityName = info.componentName.className,
                label = info.label?.toString().orEmpty(),
            )
        }
        return AppCatalog.sorted(entries)
    }
}
