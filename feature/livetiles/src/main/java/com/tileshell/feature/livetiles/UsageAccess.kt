package com.tileshell.feature.livetiles

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Usage-access opt-in, for sorting the People Hub's apps page and apps tile by
 * how often each app is opened. Like notification access, it's a special app-op
 * the user toggles in system settings, not a runtime permission: show the state,
 * deep-link to the settings screen, re-check on return.
 */
object UsageAccess {

    /** True when the user has granted TileShell usage access. */
    fun isGranted(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Opens the system "Usage access" screen, on TileShell's own entry where
     * the device supports that, else the full list. */
    fun openSettings(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(direct) }
            .recoverCatching {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
    }
}

/** Re-checks usage access on every `ON_RESUME`, so it flips as soon as the user
 * comes back from the settings screen. Same shape as [rememberNotificationAccess]. */
@Composable
fun rememberUsageAccess(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(UsageAccess.isGranted(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = UsageAccess.isGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** How far back "frequently used" looks. */
private const val USAGE_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

/** Recount at most this often; the apps page and the tile share one count. */
private const val USAGE_CACHE_MS = 10L * 60 * 1000

/**
 * Times each app was opened in the last 30 days, per package. Empty without
 * usage access. The scan walks a month of system usage events, so the result
 * is cached for [USAGE_CACHE_MS] and shared by every caller.
 */
object AppOpenCounts {
    @Volatile private var cached: Pair<Long, Map<String, Int>>? = null

    fun get(context: Context, now: Long = System.currentTimeMillis()): Map<String, Int> {
        cached?.let { (at, counts) -> if (now - at < USAGE_CACHE_MS) return counts }
        if (!UsageAccess.isGranted(context)) return emptyMap()
        val counts = runCatching { query(context, now) }.getOrDefault(emptyMap())
        cached = now to counts
        return counts
    }

    private fun query(context: Context, now: Long): Map<String, Int> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val events = usm.queryEvents(now - USAGE_WINDOW_MS, now) ?: return emptyMap()
        val resumed = mutableListOf<String>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // ACTIVITY_RESUMED (API 29) shares its value with the older
            // MOVE_TO_FOREGROUND, so this one check covers every supported API.
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) resumed += event.packageName
        }
        return countAppOpens(resumed)
    }
}

/**
 * Counts app opens from a chronological list of the packages whose activities
 * came to the foreground. Consecutive entries for the same package are one
 * open (moving between screens inside an app resumes several activities).
 * Pure for unit testing.
 */
fun countAppOpens(resumedPackages: List<String>): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    var previous: String? = null
    resumedPackages.forEach { packageName ->
        if (packageName != previous) counts[packageName] = (counts[packageName] ?: 0) + 1
        previous = packageName
    }
    return counts
}

/** [AppOpenCounts] for the composition, recounted when access changes and on
 * every return to the screen (the cache keeps that cheap). */
@Composable
fun rememberAppOpenCounts(): Map<String, Int> {
    val context = LocalContext.current
    val granted = rememberUsageAccess()
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumes by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val counts by produceState(initialValue = emptyMap<String, Int>(), granted, resumes) {
        value = if (granted) withContext(Dispatchers.IO) { AppOpenCounts.get(context) } else emptyMap()
    }
    return counts
}
