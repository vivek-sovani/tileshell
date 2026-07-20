package com.tileshell.feature.livetiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
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
 * Live-updating system state readers + true-toggle actions for the quick panel
 * (two-finger swipe-up on Start — see docs/QUICK-PANEL-SPEC.md). Every function
 * here is scoped to what's reachable with permissions already declared, or with
 * normal-protection permissions that need no Play Console disclosure, or with
 * special-access grants deep-linked via Settings (the same pattern already
 * shipped for the notification listener / accessibility service). Bluetooth is
 * deliberately absent: reading `BluetoothAdapter.isEnabled()` requires the
 * dangerous `BLUETOOTH_CONNECT` permission on API 31+, which would require a new
 * Play Console "Nearby devices" declaration — the quick panel's Bluetooth chip
 * is tap-to-settings only, with no live state, to avoid that entirely.
 */

/** Wi-Fi on/off (`ACCESS_WIFI_STATE`, normal permission — read only, never toggled directly). */
@Composable
fun rememberWifiEnabled(): Boolean {
    val context = LocalContext.current
    val wifi = remember(context) { context.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    var enabled by remember { mutableStateOf(wifi?.isWifiEnabled == true) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                enabled = wifi?.isWifiEnabled == true
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return enabled
}

/** Airplane mode on/off — read only; toggling requires `WRITE_SECURE_SETTINGS` (system-only). */
@Composable
fun rememberAirplaneModeOn(): Boolean {
    val context = LocalContext.current
    fun read(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    var on by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                on = read()
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return on
}

/** Location services on/off — no location permission needed to just read this. */
@Composable
fun rememberLocationEnabled(): Boolean {
    val context = LocalContext.current
    val location = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }
    fun read(): Boolean = location?.isLocationEnabled == true
    var enabled by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                enabled = read()
            }
        }
        context.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return enabled
}

/** Battery saver on/off — read only, tap deep-links to battery settings (matches [rememberLiveTilesActive]'s gate). */
@Composable
fun rememberBatterySaverOn(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var saving by remember { mutableStateOf(power.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                saving = power.isPowerSaveMode
            }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return saving
}

/** Flashlight/torch on/off — true toggle, no permission at all for torch-only use (API 23+). */
@Composable
fun rememberTorchOn(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val camera = remember(context) { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }
    val cameraId = remember(camera) {
        camera?.cameraIdList?.firstOrNull { id ->
            camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }
    var on by remember { mutableStateOf(false) }
    DisposableEffect(camera, cameraId) {
        if (camera == null || cameraId == null) return@DisposableEffect onDispose {}
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) {
                if (id == cameraId) on = enabled
            }
        }
        camera.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { camera.unregisterTorchCallback(callback) }
    }
    val toggle: () -> Unit = {
        if (camera != null && cameraId != null) {
            runCatching { camera.setTorchMode(cameraId, !on) }
        }
    }
    return on to toggle
}

/** Ringer mode (normal/vibrate) — a true toggle; switching *into* silent needs DND access, so this only flips normal↔vibrate. */
@Composable
fun rememberRingerVibrate(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var vibrate by remember { mutableStateOf(audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                vibrate = audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.media.RINGER_MODE_CHANGED"))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val toggle: () -> Unit = {
        val next = if (vibrate) AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_VIBRATE
        runCatching { audio.ringerMode = next }
    }
    return vibrate to toggle
}

/** Whether Notification Policy Access (DND control) has been granted — special access, no manifest permission. */
@Composable
fun rememberDndAccessGranted(): Boolean {
    val context = LocalContext.current
    val notificationManager = remember(context) {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    }
    var granted by remember { mutableStateOf(notificationManager.isNotificationPolicyAccessGranted) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                granted = notificationManager.isNotificationPolicyAccessGranted
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return granted
}

/** Current DND (do-not-disturb) state — only meaningful once [rememberDndAccessGranted] is true. */
@Composable
fun rememberDndOn(): Boolean {
    val context = LocalContext.current
    val notificationManager = remember(context) {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    }
    fun read(): Boolean =
        notificationManager.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
    var on by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                on = read()
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return on
}

/** Toggle DND. No-op (silently) if access hasn't been granted yet — the caller should deep-link to settings instead. */
fun toggleDnd(context: Context, on: Boolean) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    if (!notificationManager.isNotificationPolicyAccessGranted) return
    runCatching {
        notificationManager.setInterruptionFilter(
            if (on) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
            else android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
        )
    }
}

/** A single volume stream's current/max level (0..1) and a setter (writes back only on release, not per-frame). */
@Composable
fun rememberStreamVolume(stream: Int): Pair<Float, (Float) -> Unit> {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember(audio, stream) { audio.getStreamMaxVolume(stream).coerceAtLeast(1) }
    var level by remember { mutableStateOf(audio.getStreamVolume(stream) / max.toFloat()) }
    DisposableEffect(context, stream) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                level = audio.getStreamVolume(stream) / max.toFloat()
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val setLevel: (Float) -> Unit = { fraction ->
        val value = (fraction.coerceIn(0f, 1f) * max).toInt()
        runCatching { audio.setStreamVolume(stream, value, 0) }
    }
    return level to setLevel
}

/**
 * Rotation lock, screen brightness, and screen timeout — all three read fine
 * with no permission at all, but *writing* any of them needs `WRITE_SETTINGS`,
 * a special app-op (like notification-listener/DND access: a one-time
 * Settings deep link, not a manifest "dangerous" permission — confirmed
 * absent from Play Console's restricted-permissions list). Until granted,
 * these read-only and the caller should deep-link to
 * [Settings.ACTION_MANAGE_WRITE_SETTINGS] instead of calling the setters.
 */

/** Whether WRITE_SETTINGS has been granted. No change broadcast exists for this app-op, so it's re-checked on every ON_RESUME (mirrors [rememberPermissionGranted]'s convention for runtime permissions). */
@Composable
fun rememberWriteSettingsGranted(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(Settings.System.canWrite(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = Settings.System.canWrite(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** Deep-links to the WRITE_SETTINGS grant screen, scoped to this app. */
fun openWriteSettingsAccess(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
    }
}

/** Rotation lock on/off (the inverse of Android's own "auto-rotate" setting). */
@Composable
fun rememberRotationLockOn(): Boolean {
    val context = LocalContext.current
    fun read(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
    var locked by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                locked = read()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return locked
}

/** Sets rotation lock. No-op (silently) without WRITE_SETTINGS — the caller should deep-link instead. */
fun setRotationLock(context: Context, locked: Boolean) {
    if (!Settings.System.canWrite(context)) return
    runCatching {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (locked) 0 else 1,
        )
    }
}

/** Screen brightness 0..1 (of the system's 0..255 range) and a setter (writes back only on release). */
@Composable
fun rememberScreenBrightness(): Pair<Float, (Float) -> Unit> {
    val context = LocalContext.current
    fun read(): Float =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
    var level by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                level = read()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val setLevel: (Float) -> Unit = { fraction ->
        if (Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    (fraction.coerceIn(0f, 1f) * 255).toInt(),
                )
            }
        }
    }
    return level to setLevel
}

/** Screen-off timeout in milliseconds and a setter. */
@Composable
fun rememberScreenTimeoutMs(): Pair<Long, (Long) -> Unit> {
    val context = LocalContext.current
    fun read(): Long =
        Settings.System.getLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000L)
    var timeout by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                timeout = read()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), false, observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val setTimeout: (Long) -> Unit = { ms ->
        if (Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms)
            }
        }
    }
    return timeout to setTimeout
}

/** Ascending screen-timeout presets the quick panel's row cycles through on tap. */
val SCREEN_TIMEOUT_PRESETS_MS = listOf(15_000L, 30_000L, 60_000L, 120_000L, 300_000L, 600_000L, 1_800_000L)

/** The next preset after [current] (wraps to the first once past the last) — pure, unit-tested. */
fun nextScreenTimeoutPreset(current: Long): Long {
    val idx = SCREEN_TIMEOUT_PRESETS_MS.indexOfFirst { it >= current }
    val nextIdx = if (idx == -1) 0 else (idx + 1) % SCREEN_TIMEOUT_PRESETS_MS.size
    return SCREEN_TIMEOUT_PRESETS_MS[nextIdx]
}

/** Compact label for a screen-timeout value ("30s" / "2m" / "1h") — pure, unit-tested. */
fun screenTimeoutLabel(ms: Long): String = when {
    ms < 60_000L -> "${ms / 1000L}s"
    ms < 3_600_000L -> "${ms / 60_000L}m"
    else -> "${ms / 3_600_000L}h"
}
