package com.tileshell.feature.livetiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor

/**
 * The text shown on the battery tile's two faces. [hasData] is false when
 * [BatteryManager] returns no usable percent (should never happen on a real
 * device, but the read is `runCatching`-guarded regardless — same defensive
 * pattern as every other live face's permission/provider read).
 */
data class BatteryFace(
    val hasData: Boolean,
    val percentText: String,
    val statusLine: String,
)

/**
 * Pure — no [BatteryManager]/[Context] call inside, so the status-line wording
 * (full / charging-with-estimate / charging-no-estimate / not charging) is
 * unit-testable. [chargeTimeRemainingMillis] is null whenever the OS can't
 * estimate it yet (very common right after plugging in).
 */
fun batteryFace(percent: Int?, isCharging: Boolean, chargeTimeRemainingMillis: Long?): BatteryFace {
    if (percent == null) return BatteryFace(hasData = false, percentText = "--", statusLine = "unavailable")
    val statusLine = when {
        percent >= 100 -> "fully charged"
        isCharging && chargeTimeRemainingMillis != null && chargeTimeRemainingMillis > 0 ->
            "${formatChargeDuration(chargeTimeRemainingMillis)} left"
        isCharging -> "charging"
        else -> "not charging"
    }
    return BatteryFace(hasData = true, percentText = "$percent%", statusLine = statusLine)
}

internal fun formatChargeDuration(millis: Long): String {
    val totalMinutes = millis / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun currentBatteryFace(context: Context): BatteryFace {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val percent = runCatching {
        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
    }.getOrNull()
    val isCharging = runCatching {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }.getOrDefault(false)
    val remaining = if (isCharging) {
        runCatching { bm?.computeChargeTimeRemaining()?.takeIf { it > 0 } }.getOrNull()
    } else {
        null
    }
    return batteryFace(percent, isCharging, remaining)
}

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * Reads the current [BatteryFace] and keeps it current — refreshes on
 * [Intent.ACTION_BATTERY_CHANGED] and whenever the launcher resumes, same
 * registration pattern as [ClockTileFace]'s alarm-change listener and
 * [rememberDeviceStatus] in `DeviceStatus.kt`. Shared by [BatteryTileFace] and
 * [BatterySmallFace] so the broadcast registration isn't duplicated.
 */
@Composable
private fun rememberBatteryFace(): BatteryFace {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var face by remember { mutableStateOf(currentBatteryFace(context)) }

    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                face = currentBatteryFace(context)
            }
        }
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) face = currentBatteryFace(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return face
}

/**
 * The live battery tile: front shows the charge percent, back shows charging
 * status / time remaining. Purely broadcast-driven — no permission, no
 * polling loop needed.
 */
@Composable
fun BatteryTileFace(
    size: TileSize,
    flipped: Boolean,
    modifier: Modifier = Modifier,
) {
    val face = rememberBatteryFace()
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { BatteryFront(face, size) },
        back = { BatteryBack(face, size) },
    )
}

/**
 * The compact battery face for a small (1×1) tile: just the charge percent,
 * centred — mirrors [ClockSmallFace]/[CalendarSmallFace]/[WeatherSmallFace].
 * Never flips (small tiles stay out of the flip scheduler).
 */
@Composable
fun BatterySmallFace(modifier: Modifier = Modifier) {
    val face = rememberBatteryFace()
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = face.percentText,
            color = FaceText,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-1).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun BatteryFront(face: BatteryFace, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = face.percentText,
            color = FaceText,
            fontSize = if (short) 26.sp else if (narrow) 28.sp else if (big) 60.sp else 42.sp,
            lineHeight = if (short) 26.sp else if (narrow) 28.sp else if (big) 60.sp else 42.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-1).sp,
            maxLines = 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = face.statusLine,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (short) 10.sp else if (narrow) 11.sp else 13.sp,
            maxLines = if (narrow) 2 else 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (big) {
            Spacer(Modifier.weight(1f))
            Text("battery", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun BatteryBack(face: BatteryFace, size: TileSize) {
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "battery",
            color = FaceText,
            fontSize = if (narrow) 20.sp else 26.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-1).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (face.hasData) face.statusLine else "battery status unavailable",
            color = FaceText.copy(alpha = 0.65f),
            fontSize = if (narrow) 11.sp else 13.sp,
            maxLines = 2,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}
