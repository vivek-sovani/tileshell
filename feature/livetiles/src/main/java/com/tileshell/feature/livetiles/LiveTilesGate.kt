package com.tileshell.feature.livetiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
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
 * Whether live tiles should be animating right now (FR-2 gating). Combines the
 * caller's [suspended] flag (edit mode, app-list shown, an open overlay) with
 * the system signals that must also pause flips:
 *
 *  - the launcher is not resumed (home backgrounded / screen off),
 *  - battery saver is on,
 *  - system animations are disabled (animator duration scale 0).
 *
 * Each signal is observed live, so toggling battery saver or animations while
 * Start is up starts/stops the flips without a relaunch.
 */
@Composable
fun rememberLiveTilesActive(suspended: Boolean): Boolean {
    val resumed = rememberLifecycleResumed()
    val powerSave = rememberPowerSaveMode()
    val animationsEnabled = rememberAnimationsEnabled()
    return resumed && animationsEnabled && !powerSave && !suspended
}

@Composable
private fun rememberLifecycleResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var saving by remember { mutableStateOf(power.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                saving = power.isPowerSaveMode
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return saving
}

@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    fun read(): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f

    var enabled by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = read()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return enabled
}
