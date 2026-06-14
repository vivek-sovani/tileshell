package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Notification-access opt-in (FR-1.2 / FR-2). Listener access is not a runtime
 * permission — it can only be toggled by the user in system settings — so the
 * flow is: show current state, deep-link to the settings screen, re-check when
 * the user returns. [TileNotificationListenerService] does the actual work once
 * granted; this object is just the gate the UI consults.
 */
object NotificationAccess {

    /** True when TileShell is an enabled notification listener. */
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    /**
     * Intent to the system "Notification access" / "Device & app notifications"
     * screen where the user enables (or revokes) the listener.
     */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Tracks whether notification access is currently granted, re-checking on every
 * `ON_RESUME` so the value flips the moment the user comes back from the settings
 * deep-link (granting or revoking). Drives both the personalize toggle's label
 * and whether the mail/messages faces attempt to show live content.
 */
@Composable
fun rememberNotificationAccess(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = NotificationAccess.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}
