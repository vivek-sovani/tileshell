package com.tileshell

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

private const val TAG = "TileShell.LockScreen"

/**
 * Locks the screen immediately if device-admin is active, otherwise launches the
 * system activation prompt so the user can grant the force-lock policy once.
 */
fun lockScreen(context: Context) {
    Log.d(TAG, "lockScreen called")
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(context, LockAdminReceiver::class.java)
    if (dpm.isAdminActive(admin)) {
        Log.d(TAG, "admin active — locking now")
        dpm.lockNow()
    } else {
        Log.d(TAG, "admin not active — launching activation dialog")
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Lets TileShell lock the screen when you long-press the settings icon on Start.",
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { e ->
                Log.e(TAG, "failed to launch admin dialog", e)
                Toast.makeText(context, "lock: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
