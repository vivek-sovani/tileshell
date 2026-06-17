package com.tileshell

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

private const val TAG = "TileShell.LockScreen"

/**
 * Locks the screen immediately if device-admin is active, otherwise launches the
 * system activation prompt. Context must be an Activity — no FLAG_ACTIVITY_NEW_TASK
 * so the dialog launches in the same task (required on Android 14 from a HOME activity).
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
                "Lets TileShell lock the screen when you long-press the settings icon on Start.",
            )
        val activity = context as? Activity
        if (activity != null) {
            // Preferred: same-task start from the Activity so Android 14 shows the dialog.
            runCatching { activity.startActivity(intent) }
                .onFailure { Log.e(TAG, "admin dialog failed", it) }
        } else {
            // Fallback (application context): needs the flag but may be silently blocked on 14+.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure {
                    Log.e(TAG, "admin dialog failed (app ctx)", it)
                    Toast.makeText(
                        context,
                        "go to Settings → Security → Device admin apps to enable screen lock",
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }
}
