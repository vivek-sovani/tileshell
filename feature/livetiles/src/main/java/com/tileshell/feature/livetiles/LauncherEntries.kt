package com.tileshell.feature.livetiles

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * One launcher entry of an installed app. An app can have several (Amazon: Shopping, Pay, Now…); [id] is the app's
 * package for its main entry and "package/Class" for any other, so a hub can list, show and open each separately.
 * Hubs that only know packages (people, money, productivity) keep using package ids and are unaffected.
 */
internal data class LauncherEntry(val id: String, val packageName: String, val label: String)

/** The package an entry id belongs to. */
internal fun packageOfEntry(id: String): String = id.substringBefore('/')

/** Every launcher entry of every installed app (without TileShell), the main entry of each app first. */
internal fun launcherEntries(context: Context): List<LauncherEntry> {
    // Several hub pages and tiles ask for this on first composition; the answer (hundreds of binder calls) changes only
    // when an app is installed or removed, so it is kept for two minutes.
    val now = System.currentTimeMillis()
    entriesCache?.let { (at, list) -> if (now - at < ENTRIES_TTL_MS) return list }
    return queryLauncherEntries(context).also { if (it.isNotEmpty()) entriesCache = now to it }
}

@Volatile private var entriesCache: Pair<Long, List<LauncherEntry>>? = null
private const val ENTRIES_TTL_MS = 2 * 60_000L

private fun queryLauncherEntries(context: Context): List<LauncherEntry> = runCatching {
    val pm = context.packageManager
    val infos = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    val out = ArrayList<LauncherEntry>()
    infos.groupBy { it.activityInfo?.packageName.orEmpty() }.forEach { (pkg, group) ->
        if (pkg.isEmpty() || pkg == context.packageName) return@forEach
        // The main entry is the one the system launches for the package.
        val main = pm.getLaunchIntentForPackage(pkg)?.component?.className ?: group.first().activityInfo.name
        group.forEach { info ->
            val cls = info.activityInfo.name
            val id = if (cls == main) pkg else ComponentName(pkg, cls).flattenToString()
            out += LauncherEntry(id, pkg, info.loadLabel(pm).toString())
        }
    }
    out
}.getOrDefault(emptyList())

/** The icon of an entry id: a sub-entry has its own icon, the main entry the app's. */
internal fun entryIconDrawable(context: Context, id: String): Drawable {
    val pm = context.packageManager
    return if (id.contains('/')) {
        val component = ComponentName.unflattenFromString(id)
        if (component != null) pm.getActivityIcon(component) else pm.getApplicationIcon(packageOfEntry(id))
    } else {
        pm.getApplicationIcon(id)
    }
}
