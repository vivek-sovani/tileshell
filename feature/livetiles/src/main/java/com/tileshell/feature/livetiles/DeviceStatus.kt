package com.tileshell.feature.livetiles

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Pure read-only device status for the glance page's status card — no permission is dangerous or new here. */
data class DeviceStatus(
    val batteryPercent: Int?,
    val storageFreeGb: Float?,
    val connectivity: Connectivity,
    val nextAlarmMillis: Long?,
)

enum class Connectivity { WIFI, CELLULAR, NONE }

/**
 * Live device status: battery %, free storage, connectivity type, and the next
 * scheduled alarm clock — all normal-permission or no-permission reads (see
 * docs/QUICK-PANEL-SPEC.md §5). Read-only; there is no toggle here.
 */
@Composable
fun rememberDeviceStatus(): DeviceStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readDeviceStatus(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                status = readDeviceStatus(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return status
}

private fun readDeviceStatus(context: Context): DeviceStatus {
    val battery = runCatching {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }.getOrNull()

    val storageFreeGb = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        (stat.availableBytes.toFloat() / (1024f * 1024f * 1024f))
    }.getOrNull()

    val connectivity = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> Connectivity.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Connectivity.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Connectivity.CELLULAR
            else -> Connectivity.NONE
        }
    }.getOrDefault(Connectivity.NONE)

    val nextAlarm = runCatching {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.nextAlarmClock?.triggerTime
    }.getOrNull()

    return DeviceStatus(battery, storageFreeGb, connectivity, nextAlarm)
}
