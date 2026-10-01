package com.tileshell.feature.livetiles

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BATTERY_PIVOTS = listOf("today", "week", "apps", "details")

/**
 * The battery hub (user-approved mockup), opened from the battery tile or
 * widget. "today": the level, time left, the curve since unplugging with
 * screen-on time shaded, what the screen used on and off, a live readout and
 * the most used apps. "week": % used per day and average drain by screen
 * state. "apps": screen time per app, labelled as such — Android gives apps no
 * per-app battery figure. "details": voltage, charger, cycles and a link to
 * Android's own battery settings. Everything comes from [BatteryLog] and
 * BatteryManager; nothing leaves the phone.
 */
@Composable
fun BatteryHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "batteryHubProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val face = rememberBatteryFace()
    val stats = rememberBatteryStats(face)
    val samples by BatteryLog.samples.collectAsState()

    BackHandler(enabled = visible) { onDismiss() }
    val pagerState = rememberPagerState(pageCount = { BATTERY_PIVOTS.size })
    val pagerScope = rememberCoroutineScope()

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            HubPanorama(
                title = "battery",
                sections = BATTERY_PIVOTS,
                pagerState = pagerState,
                tokens = tokens,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> BatteryTodayPage(face, stats, tokens, accent)
                    1 -> BatteryWeekPage(samples, stats.nowMillis, tokens, accent)
                    2 -> BatteryAppsPage(tokens, accent)
                    else -> BatteryDetailsPage(face, stats, tokens, accent)
                }
            }
            HubAppBar(tokens = tokens, actions = listOf(HubAppBarAction("back", "back", onDismiss)))
        }
    }
}

@Composable
private fun BatteryTodayPage(face: BatteryFace, stats: BatteryStats, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(face.percentText, color = tokens.fg, fontSize = 52.sp, fontWeight = FontWeight.ExtraLight)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (face.isCharging) face.statusLine else timeLeftLabel(stats.hoursLeft)?.let { "$it at this rate" } ?: "measuring drain…",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        item {
            if (stats.curve.size >= 2) {
                BatteryCurve(
                    samples = stats.curve,
                    nowMillis = stats.nowMillis,
                    lineColor = accent,
                    bandColor = accent.copy(alpha = 0.16f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        if (face.isCharging) "last 12 h" else "unplugged ${clockLabel(stats.curve.first().time)}",
                        color = tokens.fgDim,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text("now · shaded = screen on", color = tokens.fgDim, fontSize = 11.sp)
                }
            } else {
                Text(
                    "the curve fills in as tileshell records your battery — check back in an hour.",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                )
            }
        }
        item {
            val used = stats.split.onDrop + stats.split.offDrop
            BatterySection(if (face.isCharging) "last time on battery · $used%" else "used since unplugging · $used%", tokens)
        }
        item {
            val total = (stats.split.onDrop + stats.split.offDrop).coerceAtLeast(1)
            UsageBar("screen on · ${durationLabel(stats.split.onMillis)} · ${stats.split.onDrop}%", stats.split.onDrop / total.toFloat(), accent, tokens)
            Spacer(Modifier.height(8.dp))
            UsageBar("screen off · ${durationLabel(stats.split.offMillis)} · ${stats.split.offDrop}%", stats.split.offDrop / total.toFloat(), tokens.fgDim, tokens)
        }
        item { BatterySection("right now", tokens) }
        item {
            DetailGrid(
                listOf(
                    "current" to (stats.currentMa?.let { if (it < 0) "charging ${-it} mA" else "$it mA draw" } ?: "—"),
                    "temperature" to (stats.temperatureC?.let { "${"%.0f".format(it)} °C" } ?: "—"),
                    "health" to (stats.health ?: "—"),
                    "charge cycles" to (stats.cycleCount?.toString() ?: "—"),
                ),
                tokens,
            )
        }
        item { BatterySection("most used apps today · screen time", tokens) }
        item {
            val apps = rememberScreenTime(startOfToday())
            when {
                apps == null -> UsagePrompt(tokens, accent)
                apps.isEmpty() -> Text("nothing yet today", color = tokens.fgDim, fontSize = 13.sp)
                else -> apps.take(3).forEach { ScreenTimeRow(it, tokens) }
            }
        }
        item {
            Text(
                "battery settings ›",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(vertical = 18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { openBatterySettings(context) },
                    ),
            )
        }
    }
}

@Composable
private fun BatteryWeekPage(samples: List<BatterySample>, nowMillis: Long, tokens: ColorTokens, accent: Color) {
    val days = remember(samples, nowMillis) { dailyUsage(samples, nowMillis) }
    val (onRate, offRate) = remember(samples) { screenRates(samples) }
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item { BatterySection("% used per day", tokens) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { (start, used) ->
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(if (used > 0) "$used" else "", color = tokens.fgDim, fontSize = 11.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.75f * used / max + 0.01f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(dayFormat.format(Date(start)).lowercase(), color = tokens.fgDim, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Text(
                "recording started ${clockLabel(samples.firstOrNull()?.time ?: nowMillis, withDay = true)} — earlier days fill in as the week goes on.",
                color = tokens.fgDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item { BatterySection("average drain", tokens) }
        item {
            DetailGrid(
                listOf(
                    "screen on" to (onRate?.let { "${"%.1f".format(it)}%/hr" } ?: "—"),
                    "screen off" to (offRate?.let { "${"%.1f".format(it)}%/hr" } ?: "—"),
                ),
                tokens,
            )
        }
    }
}

@Composable
private fun BatteryAppsPage(tokens: ColorTokens, accent: Color) {
    val today = rememberScreenTime(startOfToday())
    val week = rememberScreenTime(startOfToday() - 6 * 24 * 3_600_000L)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item {
            Text(
                "screen time, not battery: android doesn't give apps a per-app battery figure. apps you use most usually use the most battery.",
                color = tokens.fgDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (today == null) {
            item { UsagePrompt(tokens, accent) }
        } else {
            item { BatterySection("today", tokens) }
            items(today.take(10), key = { "t-${it.packageName}" }) { ScreenTimeRow(it, tokens) }
            item { BatterySection("last 7 days", tokens) }
            items(week.orEmpty().take(10), key = { "w-${it.packageName}" }) { ScreenTimeRow(it, tokens) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BatteryDetailsPage(face: BatteryFace, stats: BatteryStats, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item { BatterySection("battery", tokens) }
        item {
            DetailGrid(
                listOf(
                    "level" to face.percentText,
                    "status" to face.statusLine,
                    "charger" to (stats.chargerType ?: if (face.isCharging) "plugged in" else "unplugged"),
                    "current" to (stats.currentMa?.let { if (it < 0) "charging ${-it} mA" else "$it mA draw" } ?: "—"),
                    "voltage" to (stats.voltageMv?.let { "${"%.2f".format(it / 1000.0)} V" } ?: "—"),
                    "temperature" to (stats.temperatureC?.let { "${"%.1f".format(it)} °C" } ?: "—"),
                    "health" to (stats.health ?: "—"),
                    "charge cycles" to (stats.cycleCount?.toString() ?: "not reported"),
                ),
                tokens,
            )
        }
        item {
            Text(
                "battery settings ›",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(vertical = 18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { openBatterySettings(context) },
                    ),
            )
        }
    }
}

@Composable
private fun BatterySection(text: String, tokens: ColorTokens) {
    Text(text, color = tokens.fgDim, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun UsageBar(label: String, fraction: Float, color: Color, tokens: ColorTokens) {
    Text(label, color = tokens.fg, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))
    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(tokens.fg.copy(alpha = 0.08f))) {
        Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

@Composable
private fun DetailGrid(items: List<Pair<String, String>>, tokens: ColorTokens) {
    items.chunked(2).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            row.forEach { (label, value) ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, color = tokens.fgDim, fontSize = 11.sp)
                    Text(value, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun UsagePrompt(tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    Text(
        "see screen time per app · allow usage access",
        color = accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { UsageAccess.openSettings(context) },
        ),
    )
}

/** One app's screen time. */
data class ScreenTimeEntry(val packageName: String, val label: String, val millis: Long)

@Composable
private fun ScreenTimeRow(entry: ScreenTimeEntry, tokens: ColorTokens) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { openApp(context, entry.packageName) },
            )
            .padding(vertical = 6.dp),
    ) {
        val icon = rememberAppIconBitmap(entry.packageName, sizePx = iconPx(28.dp))
        Box(modifier = Modifier.size(28.dp)) {
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Text(entry.label.lowercase(), color = tokens.fg, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(durationLabel(entry.millis), color = tokens.fgDim, fontSize = 13.sp)
    }
}

/**
 * Screen time per launchable app since [startMillis], most first; null without
 * usage access. TileShell itself and zero-time apps are left out.
 */
@Composable
private fun rememberScreenTime(startMillis: Long): List<ScreenTimeEntry>? {
    val context = LocalContext.current
    val granted = rememberUsageAccess()
    val result by produceState<List<ScreenTimeEntry>?>(initialValue = if (granted) emptyList() else null, granted, startMillis) {
        value = if (!granted) null else withContext(Dispatchers.IO) { queryScreenTime(context, startMillis) }
    }
    return result
}

private fun queryScreenTime(context: Context, startMillis: Long): List<ScreenTimeEntry> = runCatching {
    val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
    val pm = context.packageManager
    usm.queryAndAggregateUsageStats(startMillis, System.currentTimeMillis()).values
        .filter { it.totalTimeInForeground > 60_000 && it.packageName != context.packageName }
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        .sortedByDescending { it.totalTimeInForeground }
        .take(15)
        .map { ScreenTimeEntry(it.packageName, appLabelOrNull(context, it.packageName) ?: it.packageName, it.totalTimeInForeground) }
}.getOrDefault(emptyList())

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** "1 h 17 m" / "42 m". Pure. */
internal fun durationLabel(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} m" else "$minutes m"
}

private fun clockLabel(millis: Long, withDay: Boolean = false): String =
    SimpleDateFormat(if (withDay) "EEE h:mm a" else "h:mm a", Locale.getDefault()).format(Date(millis)).lowercase()

private fun openBatterySettings(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
