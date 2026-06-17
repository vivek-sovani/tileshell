package com.tileshell

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Locks the screen immediately if device-admin is active, otherwise launches the
 * system activation prompt so the user can grant the force-lock policy once.
 */
fun lockScreen(context: Context) {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(context, LockAdminReceiver::class.java)
    if (dpm.isAdminActive(admin)) {
        dpm.lockNow()
    } else {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Lets TileShell lock the screen when you double-tap an empty spot on Start.",
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
