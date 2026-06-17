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
    // Preferred: accessibility GLOBAL_ACTION_LOCK_SCREEN — biometric unlock still works (API 28+).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && LockAccessibilityService.lockScreen()) return

    // Fallback: device admin lockNow() — requires PIN on next unlock (no biometrics).
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(context, LockAdminReceiver::class.java)
    if (dpm.isAdminActive(admin)) {
        dpm.lockNow()
        return
    }

    // Neither enabled — prompt user to enable the accessibility service.
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    val activity = context as? Activity
    if (activity != null) {
        runCatching { activity.startActivity(intent) }
            .onFailure { Log.e(TAG, "accessibility settings failed", it) }
    } else {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "accessibility settings failed (app ctx)", it) }
    }
}
