package com.tileshell.feature.livetiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    val isCharging: Boolean = false,
)

/**
 * Pure — no [BatteryManager]/[Context] call inside, so the status-line wording
 * (full / charging-with-estimate / charging-no-estimate / not charging) is
 * unit-testable. [chargeTimeRemainingMillis] is null whenever the OS can't
 * estimate it yet (very common right after plugging in).
 *
 * User-reported: "25m left" alone reads as ambiguous while charging — it
 * could be misread as remaining battery life (as if unplugged) instead of
 * time until full, its actual meaning ([BatteryManager
 * .computeChargeTimeRemaining] is *always* "time to 100%", never a discharge
 * estimate). "25m to full" removes that ambiguity.
 */
fun batteryFace(percent: Int?, isCharging: Boolean, chargeTimeRemainingMillis: Long?): BatteryFace {
    if (percent == null) return BatteryFace(hasData = false, percentText = "--", statusLine = "unavailable")
    val statusLine = when {
        percent >= 100 -> "fully charged"
        isCharging && chargeTimeRemainingMillis != null && chargeTimeRemainingMillis > 0 ->
            "${formatChargeDuration(chargeTimeRemainingMillis)} to full"
        isCharging -> "charging"
        else -> "not charging"
    }
    return BatteryFace(hasData = true, percentText = "$percent%", statusLine = statusLine, isCharging = isCharging)
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

/** Widened to internal so the home-screen battery widget can reuse this exact read (S33). */
internal fun currentBatteryFace(context: Context): BatteryFace {
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
 *
 * Also cascades to the home-screen battery widget (user-reported: unplugging
 * left a stale "to full" widget) — the widget's own manifest-declared
 * receiver can't get `ACTION_BATTERY_CHANGED` at all (blocked since API 26),
 * so this in-app dynamic receiver is the only way it ever hears about a
 * plug/unplug event *sooner* than its own periodic push; same idea as
 * [WeatherRefreshWorker] pushing weather's widget after a fresh fetch.
 */
@Composable
internal fun rememberBatteryFace(): BatteryFace {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var face by remember { mutableStateOf(currentBatteryFace(context)) }

    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val next = currentBatteryFace(context)
                val previous = face
                face = next
                // ACTION_BATTERY_CHANGED is a firehose — it fires on voltage and
                // temperature changes too, which on many devices means every few
                // seconds while charging. Enqueuing a WorkManager request that
                // often means a write to WorkManager's own database and a round
                // of scheduler churn per broadcast, per composed battery tile,
                // to re-push a widget whose rendered content usually hasn't
                // changed. Only cascade when something the widget actually
                // displays is different.
                if (next.percentText != previous.percentText ||
                    next.isCharging != previous.isCharging ||
                    next.statusLine != previous.statusLine
                ) {
                    com.tileshell.feature.livetiles.widget.BatteryWidgetRefreshWorker.refreshNow(context)
                }
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
 * The live battery tile (redesigned, user-approved mockup): the front shows
 * the charge percent with its gauge, the recent drain rate and time left (or,
 * charging, time to full and the charge current), and — two rows tall or more —
 * the curve since unplugging, with screen-on time shaded. The back shows what
 * the screen used today on and off, the live current draw, and temperature and
 * health. Drain figures come from [BatteryLog], TileShell's own battery
 * history, since Android gives apps no history of its own. Tapping opens the
 * battery hub (routed by Start).
 */
@Composable
fun BatteryTileFace(
    size: TileSize,
    flipped: Boolean,
    modifier: Modifier = Modifier,
) {
    val face = rememberBatteryFace()
    val stats = rememberBatteryStats(face)
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { BatteryFront(face, stats, size) },
        back = { BatteryBack(face, stats, size) },
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
private fun BatteryFront(face: BatteryFace, stats: BatteryStats, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE || size == TileSize.XLARGE
    val showCurve = !narrow && !short && size.rows >= 2 && stats.curve.size >= 2
    Box(modifier = Modifier.fillMaxSize()) {
        if (showCurve) {
            BatteryCurve(
                samples = stats.curve,
                nowMillis = stats.nowMillis,
                lineColor = FaceText.copy(alpha = 0.85f),
                bandColor = FaceText.copy(alpha = 0.10f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(if (size.cols >= 4) 0.45f else 0.32f)
                    .padding(bottom = 18.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
            verticalArrangement = if (narrow) Arrangement.SpaceEvenly else if (showCurve) Arrangement.Top else Arrangement.Center,
            horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            val percentTextComposable = @Composable {
                Text(
                    text = face.percentText,
                    color = FaceText,
                    fontSize = if (short) 26.sp else if (narrow) 28.sp else if (big) 60.sp else 40.sp,
                    lineHeight = if (short) 26.sp else if (narrow) 28.sp else if (big) 60.sp else 40.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-1).sp,
                    maxLines = 1,
                    textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                )
            }
            if (narrow || !face.hasData) {
                percentTextComposable()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    percentTextComposable()
                    BatteryGaugeVisual(
                        percent = face.percentText.removeSuffix("%").toIntOrNull() ?: 0,
                        tint = FaceText,
                        isCharging = face.isCharging,
                        modifier = Modifier
                            .width(if (short) 40.dp else if (big) 64.dp else 44.dp)
                            .height(if (short) 22.dp else if (big) 34.dp else 24.dp),
                    )
                }
            }
            val lines = batteryFrontLines(face, stats, twoLines = !short && !narrow)
            lines.forEach { line ->
                Text(
                    text = line,
                    color = FaceText.copy(alpha = 0.85f),
                    fontSize = if (short) 10.sp else if (narrow) 11.sp else 12.sp,
                    maxLines = if (narrow) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
                )
            }
        }
        if (!narrow && !short && size.rows >= 2) {
            Text(
                if (showCurve && size.cols >= 4) "battery · today" else "battery",
                color = FaceText.copy(alpha = 0.82f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 11.dp, bottom = 5.dp),
            )
        }
    }
}

@Composable
private fun BatteryBack(face: BatteryFace, stats: BatteryStats, size: TileSize) {
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (!face.hasData) {
            Text("battery status unavailable", color = FaceText.copy(alpha = 0.65f), fontSize = 13.sp)
            return@Column
        }
        batteryBackLines(stats).forEach { line ->
            Text(
                line,
                color = FaceText,
                fontSize = if (narrow) 11.sp else 13.sp,
                lineHeight = if (narrow) 15.sp else 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
        if (!narrow && size.rows >= 2) {
            Spacer(Modifier.height(4.dp))
            Text("today", color = FaceText.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

/** The status line(s) under the percent. Pure. */
internal fun batteryFrontLines(face: BatteryFace, stats: BatteryStats, twoLines: Boolean): List<String> {
    if (!face.hasData) return emptyList()
    if (face.isCharging) {
        val full = face.percentText.removeSuffix("%").toIntOrNull()?.let { it >= 100 } == true
        val second = stats.currentMa?.takeIf { it < 0 && !full }?.let { "charging · ${-it} mA" }
        return listOfNotNull(face.statusLine, second.takeIf { twoLines })
    }
    val rate = stats.ratePerHour?.let { "~${"%.1f".format(it)}%/hr now" }
    val left = timeLeftLabel(stats.hoursLeft)
    return when {
        rate == null -> listOf("measuring drain…")
        twoLines -> listOfNotNull(rate, left)
        else -> listOf(listOfNotNull(rate, left).joinToString(" · "))
    }
}

/** The back face's lines: today's split, the live draw, temperature and health. Pure. */
internal fun batteryBackLines(stats: BatteryStats): List<String> = listOfNotNull(
    "screen on · ${stats.split.onDrop}%",
    "screen off · ${stats.split.offDrop}%",
    stats.currentMa?.let { if (it < 0) "now · charging ${-it} mA" else "now · $it mA" },
    listOfNotNull(stats.temperatureC?.let { "${"%.0f".format(it)} °C" }, stats.health).joinToString(" · ").ifBlank { null },
)

/** What the battery tile and hub show beyond the plain percent. */
data class BatteryStats(
    val nowMillis: Long,
    val ratePerHour: Double?,
    val hoursLeft: Double?,
    val split: ScreenSplit,
    val curve: List<BatterySample>,
    val currentMa: Int?,
    val temperatureC: Double?,
    val health: String?,
    val voltageMv: Int?,
    val cycleCount: Int?,
    val chargerType: String?,
)

/** Live details read from the sticky battery broadcast and BatteryManager. */
internal fun readBatteryDetails(context: Context): Triple<Map<String, Any?>, Int?, Int?> {
    val sticky = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val charging = batteryIsCharging(sticky)
    val raw = runCatching { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull() ?: 0
    val details = mapOf(
        "temp" to sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10.0 },
        "health" to sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)?.let(::batteryHealthLabel),
        "voltage" to sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 },
        "plugged" to sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let(::chargerTypeLabel),
    )
    val cycles = sticky?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it >= 0 }
    return Triple(details, normaliseCurrentMa(raw, charging), cycles)
}

private fun batteryIsCharging(sticky: Intent?): Boolean {
    val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0 ||
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

/** Pure. */
internal fun batteryHealthLabel(health: Int): String? = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheating"
    BatteryManager.BATTERY_HEALTH_DEAD -> "worn out"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over voltage"
    BatteryManager.BATTERY_HEALTH_COLD -> "too cold"
    else -> null
}

/** Pure. */
internal fun chargerTypeLabel(plugged: Int): String? = when (plugged) {
    BatteryManager.BATTERY_PLUGGED_AC -> "charger"
    BatteryManager.BATTERY_PLUGGED_USB -> "usb"
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
    else -> null
}

/** [BatteryStats] for the composition, kept current: re-derived when the
 * battery broadcast updates [face], when the log grows, and every minute. */
@Composable
internal fun rememberBatteryStats(face: BatteryFace): BatteryStats {
    val context = LocalContext.current
    LaunchedEffect(Unit) { BatteryLog.ensureStarted(context) }
    val samples by BatteryLog.samples.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    return remember(samples, face, now) {
        val (details, currentMa, cycles) = readBatteryDetails(context)
        val rate = drainRatePerHour(samples, now)
        val level = face.percentText.removeSuffix("%").toIntOrNull() ?: 0
        val sinceUnplug = samplesSinceUnplug(samples)
        BatteryStats(
            nowMillis = now,
            ratePerHour = rate,
            hoursLeft = hoursLeft(level, rate),
            split = screenSplit(samples, now),
            // While charging there's no "since unplug" run; show the last 12 h.
            curve = sinceUnplug.ifEmpty { samples.filter { it.time >= now - 12 * 3_600_000L } },
            currentMa = currentMa,
            temperatureC = details["temp"] as Double?,
            health = details["health"] as String?,
            voltageMv = details["voltage"] as Int?,
            cycleCount = cycles,
            chargerType = details["plugged"] as String?,
        )
    }
}

/**
 * The battery curve: level over time from the first sample to [nowMillis],
 * with screen-on stretches shaded in [bandColor]. The y axis runs from a little
 * under the lowest level to 100%, so a slow day still shows a slope.
 */
@Composable
internal fun BatteryCurve(
    samples: List<BatterySample>,
    nowMillis: Long,
    lineColor: Color,
    bandColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val t0 = samples.first().time
        val t1 = maxOf(nowMillis, samples.last().time + 1)
        val minLevel = (samples.minOf { it.level } - 10).coerceIn(0, 90)
        fun x(t: Long) = size.width * (t - t0) / (t1 - t0).toFloat()
        fun y(level: Int) = size.height * (1f - (level - minLevel) / (100f - minLevel))
        samples.forEachIndexed { i, s ->
            if (!s.screenOn) return@forEachIndexed
            val end = samples.getOrNull(i + 1)?.time ?: t1
            drawRect(bandColor, topLeft = Offset(x(s.time), 0f), size = Size((x(end) - x(s.time)).coerceAtLeast(1f), size.height))
        }
        val path = Path()
        samples.forEachIndexed { i, s -> if (i == 0) path.moveTo(x(s.time), y(s.level)) else path.lineTo(x(s.time), y(s.level)) }
        path.lineTo(x(t1), y(samples.last().level))
        drawPath(path, lineColor, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
