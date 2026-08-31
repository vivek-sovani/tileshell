package com.tileshell.feature.livetiles

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * The text shown on the alarm tile's front face. [hasAlarm] false means
 * [AlarmManager.getNextAlarmClock] reports nothing scheduled system-wide (same
 * single "next" value [nextAlarmString]/[ClockBack] read from — see that file's
 * doc comment on why this can't be scoped to just a clock app's own alarms).
 */
data class AlarmFace(
    val hasAlarm: Boolean,
    val time: String,
    val dayLabel: String,
)

/**
 * Pure — no [Calendar]/[Context] call inside — so the "today" / "tomorrow" /
 * weekday-name choice is unit-testable. A year boundary (e.g. today Dec 31,
 * alarm Jan 1) falls through to the weekday-name branch rather than "tomorrow"
 * since day-of-year alone can't express that across a year rollover; a known,
 * accepted inaccuracy for one day a year rather than extra date-math for it.
 */
fun alarmFace(
    hasAlarm: Boolean,
    time: String,
    nowDayOfYear: Int,
    nowYear: Int,
    triggerDayOfYear: Int,
    triggerYear: Int,
    triggerWeekday: String,
): AlarmFace {
    if (!hasAlarm) return AlarmFace(hasAlarm = false, time = "", dayLabel = "no alarm set")
    val dayLabel = when {
        nowYear == triggerYear && nowDayOfYear == triggerDayOfYear -> "today"
        nowYear == triggerYear && triggerDayOfYear == nowDayOfYear + 1 -> "tomorrow"
        else -> triggerWeekday
    }
    return AlarmFace(hasAlarm = true, time = time, dayLabel = dayLabel)
}

private fun currentAlarmFace(context: Context): AlarmFace {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val info = am?.nextAlarmClock
        ?: return alarmFace(false, "", 0, 0, 0, 0, "")
    val now = Calendar.getInstance()
    val trigger = Calendar.getInstance().apply { timeInMillis = info.triggerTime }
    return alarmFace(
        hasAlarm = true,
        time = nextAlarmString(context),
        nowDayOfYear = now.get(Calendar.DAY_OF_YEAR),
        nowYear = now.get(Calendar.YEAR),
        triggerDayOfYear = trigger.get(Calendar.DAY_OF_YEAR),
        triggerYear = trigger.get(Calendar.YEAR),
        triggerWeekday = WEEKDAYS[trigger.get(Calendar.DAY_OF_WEEK) - 1],
    )
}

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live alarm tile: front shows the next system-wide alarm time and day
 * ("today"/"tomorrow"/weekday), back is a plain label + tap hint. Ticks on the
 * minute boundary while [active] (same as [ClockTileFace]) so the day label
 * rolls over exactly at midnight, and refreshes immediately on
 * [AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED] or resume — same pattern
 * [ClockTileFace] already uses for its own alarm line.
 */
@Composable
fun AlarmTileFace(
    size: TileSize,
    flipped: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var face by remember { mutableStateOf(currentAlarmFace(context)) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            face = currentAlarmFace(context)
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                face = currentAlarmFace(context)
            }
        }
        runCatching {
            context.registerReceiver(receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) face = currentAlarmFace(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { AlarmFront(face, size) },
        back = { AlarmBack(face, size) },
    )
}

@Composable
private fun AlarmFront(face: AlarmFace, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (face.hasAlarm) {
            Text(
                text = face.time,
                color = FaceText,
                fontSize = if (short) 20.sp else if (narrow) 22.sp else if (big) 50.sp else 34.sp,
                lineHeight = if (short) 20.sp else if (narrow) 22.sp else if (big) 50.sp else 34.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-1).sp,
                maxLines = 1,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
            Text(
                text = face.dayLabel,
                color = FaceText.copy(alpha = 0.82f),
                fontSize = if (short) 10.sp else if (narrow) 12.sp else 14.sp,
                maxLines = 1,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        } else {
            Text(
                text = "no alarm set",
                color = FaceText.copy(alpha = 0.82f),
                fontSize = if (short) 12.sp else 14.sp,
                maxLines = if (narrow) 2 else 1,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
        if (big) {
            Spacer(Modifier.weight(1f))
            Text("alarm", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlarmBack(face: AlarmFace, size: TileSize) {
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "alarm",
            color = FaceText,
            fontSize = if (narrow) 20.sp else 26.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-1).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (face.hasAlarm) "tap to open your alarms" else "tap to set one",
            color = FaceText.copy(alpha = 0.65f),
            fontSize = if (narrow) 11.sp else 13.sp,
            maxLines = 2,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}
