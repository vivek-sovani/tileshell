package com.tileshell.feature.livetiles

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Returns whether [permission] is currently granted, re-checking on every
 * ON_RESUME so the value flips the moment the user returns from the system
 * permission dialog. Tiles use this to gate live content; the actual runtime
 * request is made once at app start by [RequestRuntimePermissionsOnStart] in
 * MainActivity so all permissions are asked upfront rather than lazily per-tile.
 */
@Composable
fun rememberPermissionGranted(permission: String): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/**
 * Whether the system will still show its own permission dialog for
 * [permission]. Returns false once the user has permanently denied it (two
 * declines, or a single "don't allow" on older releases): from then on
 * `ActivityResultLauncher.launch` resolves to "denied" instantly with nothing
 * ever appearing on screen, which reads as a dead button — exactly the
 * reported symptom for the steps tile's own contextual ask ("it asked but was
 * non responsive"). Callers should route to [openAppPermissionSettings]
 * instead when this is false.
 *
 * [asked] is the caller's own record of having requested [permission] before,
 * and it is required: `shouldShowRequestPermissionRationale` is *also* false
 * for a permission that has simply never been requested yet, so without it
 * a first-ever ask would be misread as permanently blocked.
 */
fun canShowSystemPermissionDialog(context: Context, permission: String, asked: Boolean): Boolean {
    if (!asked) return true
    val activity = context.findActivity() ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

/**
 * Opens this app's own entry in system Settings, where a permanently denied
 * permission can still be turned on by hand — the only remaining route once
 * [canShowSystemPermissionDialog] is false.
 */
fun openAppPermissionSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
