package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Battery-optimisation exemption utilities for beta hardening (S28).
 *
 * Android Doze and OEM battery killers (Xiaomi AutoStart, Samsung Adaptive
 * Battery, Huawei App Launch, OnePlus/OPPO Startup Manager, Vivo Background
 * Power Consumption) all independently kill [TileNotificationListenerService],
 * breaking badges and live faces even when notification access is granted.
 *
 * Remedy strategy:
 *  1. Standard: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` adds TileShell to
 *     the Doze whitelist on all Android 6+ devices.
 *  2. OEM-specific: when a known OEM battery-management screen is present,
 *     [requestExemption] also navigates there so the user can toggle AutoStart /
 *     unrestricted-background manually — no API can do this programmatically.
 *
 * [rememberBatteryOptimizationExempt] drives the PersonalizeSheet warning row:
 * it shows whenever notification access is on but the Doze exemption is off.
 */
object OemBatteryGuard {

    /** True when TileShell is on the Android Doze whitelist (battery-exempt). */
    fun isExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the best available battery-exemption flow for this device:
     *  1. `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — standard Android dialog.
     *  2. OEM-specific autostart/battery management screen (Xiaomi, Huawei, etc.).
     *  3. `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` — generic battery-exempt list.
     *
     * Each step only fires if the previous one is unavailable or throws.
     */
    fun requestExemption(context: Context) {
        // Standard Doze-whitelist dialog (supported on all AOSP-based devices).
        val standard = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(standard) }.isSuccess) return

        // OEM-specific autostart/battery management page.
        val oem = oemBatterySettingsIntent(context)
        if (oem != null && runCatching { context.startActivity(oem) }.isSuccess) return

        // Generic fallback: system battery-optimization settings list.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Returns an intent that opens the OEM-specific battery/autostart settings,
     * or null on stock Android or unknown OEMs. The intent is only returned when
     * the target activity is actually resolvable on this device.
     */
    fun oemBatterySettingsIntent(context: Context): Intent? {
        val pm = context.packageManager
        val candidates: List<Pair<String, String>> = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> listOf(
                "com.miui.securitycenter" to
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            "huawei", "honor" -> listOf(
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
            )
            "oppo", "realme" -> listOf(
                "com.coloros.safecenter" to
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.opti.safecenter" to
                    "com.coloros.opti.safecenter.permission.startup.StartupAppListActivity",
            )
            "vivo" -> listOf(
                "com.vivo.permissionmanager" to
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            )
            "oneplus" -> listOf(
                "com.oneplus.security" to
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            )
            else -> emptyList()
        }
        return candidates.firstNotNullOfOrNull { (pkg, cls) ->
            val intent = Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            @Suppress("DEPRECATION")
            if (runCatching {
                    pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }.getOrNull() != null
            ) intent else null
        }
    }

    /**
     * Human-readable name of the OEM battery toggle that the user must enable
     * after granting Doze exemption. Returns an empty string on stock Android.
     */
    fun oemSettingName(): String = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> "AutoStart"
        "huawei", "honor" -> "App Launch (Manage manually)"
        "oppo", "realme" -> "Startup Manager"
        "vivo" -> "Background App Refresh"
        "oneplus" -> "Startup Manager"
        "samsung" -> "Battery (set to Unrestricted)"
        else -> ""
    }

    /**
     * Single-line guidance note shown under the battery-exemption row. Empty on
     * stock Android where Doze exemption alone is sufficient.
     */
    fun guidanceNote(): String {
        val name = oemSettingName()
        return if (name.isEmpty()) "" else "also enable $name in system settings"
    }
}

/**
 * Tracks whether TileShell is on the Doze whitelist, re-checking on every
 * `ON_RESUME` so the value flips the moment the user returns from any settings
 * deep-link. Drives the PersonalizeSheet battery-exemption warning row.
 */
@Composable
fun rememberBatteryOptimizationExempt(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var exempt by remember { mutableStateOf(OemBatteryGuard.isExempt(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = OemBatteryGuard.isExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return exempt
}
