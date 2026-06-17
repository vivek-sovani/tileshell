package com.tileshell

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

private const val TAG = "TileShell.LockScreen"

fun lockScreen(context: Context) {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(context, LockAdminReceiver::class.java)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Remove the device-admin grant if it is still active. It is no longer needed
        // and its presence keeps the "must use PIN" secure-lock flag on some devices.
        if (dpm.isAdminActive(admin)) runCatching { dpm.removeActiveAdmin(admin) }

        if (LockAccessibilityService.lockScreen()) return

        // Accessibility service not yet enabled — send the user to turn it on.
        openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)
        return
    }

    // API 26–27 only: fall back to device admin (biometrics won't work, but screen locks).
    if (dpm.isAdminActive(admin)) {
        dpm.lockNow()
        return
    }
    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Lets TileShell lock the screen when you long-press the settings icon on Start.",
        )
    openSettings(context, null, intent)
}

private fun openSettings(context: Context, action: String?, intent: Intent = Intent(action)) {
    val activity = context as? Activity
    if (activity != null) {
        runCatching { activity.startActivity(intent) }
            .onFailure { Log.e(TAG, "settings launch failed", it) }
    } else {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "settings launch failed (app ctx)", it) }
    }
}
