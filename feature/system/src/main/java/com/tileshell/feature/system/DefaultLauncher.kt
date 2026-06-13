package com.tileshell.feature.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Helpers for the "set TileShell as default launcher" flow.
 *
 * API 29+ uses [RoleManager] (system dialog with a direct choice). API 26–28
 * has no role API, so we fall back to the Home settings screen.
 */
object DefaultLauncher {

    /** True when TileShell is the device's current default HOME app. */
    fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        // Pre-Q (or no role): resolve the preferred HOME activity. Not
        // authoritative when several launchers exist and none is preferred,
        // but the worst case is one redundant prompt.
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(probe, 0)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Intent that asks the user to make TileShell the default launcher, or
     * null when there is nothing to ask (already default, or role unavailable
     * and no settings screen to open).
     */
    fun createPromptIntent(context: Context): Intent? {
        if (isDefault(context)) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
            return null
        }

        // API 26–28: open the system Home-app picker in Settings.
        val settings = Intent(Settings.ACTION_HOME_SETTINGS)
        return settings.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
