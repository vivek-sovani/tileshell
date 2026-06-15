package com.tileshell.core.data.seed

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony

/** A resolved installed app activity. */
data class ResolvedComponent(
    val packageName: String,
    val activityName: String,
    val label: String,
)

/** Resolves a [RoleQuery] to an installed app, or null when none matches. */
fun interface RoleResolver {
    fun resolve(query: RoleQuery): ResolvedComponent?
}

/**
 * [RoleResolver] backed by [PackageManager]. Every role resolves to the
 * launcher (entry-point) activity of the matching package, so tapping a seeded
 * tile opens the app normally regardless of which action/category matched.
 */
class AndroidRoleResolver(context: Context) : RoleResolver {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    override fun resolve(query: RoleQuery): ResolvedComponent? {
        val packageName = when (query) {
            is RoleQuery.DefaultSms -> Telephony.Sms.getDefaultSmsPackage(appContext)
            is RoleQuery.Category -> {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(query.category)
                resolvePackage(intent)
            }
            is RoleQuery.Action -> {
                val intent = Intent(query.action).apply {
                    query.dataUri?.let { data = Uri.parse(it) }
                }
                resolvePackage(intent)
            }
            // First sub-query that resolves to a launchable app wins.
            is RoleQuery.AnyOf -> return query.queries.firstNotNullOfOrNull { resolve(it) }
        } ?: return null

        return launcherComponentOf(packageName)
    }

    /** Package of the best match for [intent], skipping the system chooser. */
    private fun resolvePackage(intent: Intent): String? {
        val info = pm.resolveActivity(intent, 0)?.activityInfo ?: return null
        if (info.packageName == "android") return null // disambiguation chooser, no default
        return info.packageName
    }

    /** The package's launcher activity as a [ResolvedComponent], if launchable. */
    private fun launcherComponentOf(packageName: String): ResolvedComponent? {
        val component = pm.getLaunchIntentForPackage(packageName)?.component ?: return null
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        return ResolvedComponent(component.packageName, component.className, label)
    }
}
