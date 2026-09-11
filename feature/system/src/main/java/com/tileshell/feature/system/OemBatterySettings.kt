package com.tileshell.feature.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * A manufacturer-specific "battery management" / "protected apps" screen —
 * the settings surface several OEM Android skins use to silently revoke a
 * background service's privileges over time, *independent of* the standard
 * Android Doze/battery-optimization exemption (already granted separately;
 * see `LauncherSettings`' battery-exemption row). This is a different,
 * OEM-proprietary mechanism with no public API — these component names are
 * community-documented (the same ones apps like the dontkillmyapp.com
 * project catalogue) and can drift or disappear across OEM software
 * versions, which is exactly why [openOemBatterySettings] always falls back
 * to this app's own App Info screen (always resolvable) rather than failing
 * silently when a specific intent doesn't resolve on a given device.
 */
internal data class OemBatteryTarget(val packageName: String, val className: String)

/**
 * Maps a [Build.MANUFACTURER] string to its known battery-management screen,
 * or null when the manufacturer isn't one of the ones with a documented
 * screen. Pure and case/whitespace-insensitive so it's unit-testable and
 * matches regardless of how a given device happens to report the value.
 */
internal fun oemBatteryTarget(manufacturer: String): OemBatteryTarget? =
    when (manufacturer.trim().lowercase()) {
        "samsung" -> OemBatteryTarget(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity",
        )
        "xiaomi" -> OemBatteryTarget(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        )
        "huawei" -> OemBatteryTarget(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        )
        "oppo" -> OemBatteryTarget(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        )
        "vivo" -> OemBatteryTarget(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        )
        "oneplus" -> OemBatteryTarget(
            "com.oneplus.security",
            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        )
        else -> null
    }

/**
 * Opens this device manufacturer's own battery-management screen so the user
 * can exempt TileShell from it in one tap — surfaced alongside the
 * accessibility "please enable again" disclosure, since an OEM silently
 * revoking the Accessibility Service grant over time (well documented on
 * Samsung in particular) is the most likely explanation for real users
 * seeing that prompt repeatedly with no action of their own. Tries the
 * manufacturer-specific screen first; if it isn't present on this device's
 * software version (or the manufacturer isn't one of the known ones), falls
 * back to this app's own App Info screen, where the user can still reach
 * battery settings manually — always resolvable, so this never leaves the
 * user with an inert button.
 */
fun openOemBatterySettings(context: Context) {
    val target = oemBatteryTarget(Build.MANUFACTURER)
    val openedSpecific = target != null && runCatching {
        context.startActivity(
            Intent()
                .setComponent(ComponentName(target.packageName, target.className))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!openedSpecific) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
