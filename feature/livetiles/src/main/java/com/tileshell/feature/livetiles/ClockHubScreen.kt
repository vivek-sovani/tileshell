package com.tileshell.feature.livetiles

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.clock.BuzzStrength
import com.tileshell.core.data.clock.ClockSessions
import com.tileshell.core.data.clock.ClockStore
import com.tileshell.core.data.clock.Session
import com.tileshell.core.data.clock.SessionStep
import com.tileshell.core.data.clock.StopwatchState
import com.tileshell.core.data.clock.TimerPart
import com.tileshell.core.data.clock.TimerSet
import com.tileshell.core.data.clock.TimerStep
import com.tileshell.core.data.clock.WORLD_CITIES
import com.tileshell.core.data.clock.WorldCity
import com.tileshell.core.data.clock.flatten
import com.tileshell.core.data.clock.formatDuration
import com.tileshell.core.data.clock.formatStopwatch
import com.tileshell.core.data.clock.lap
import com.tileshell.core.data.clock.lapLengths
import com.tileshell.core.data.clock.parseDuration
import com.tileshell.core.data.clock.reset
import com.tileshell.core.data.clock.searchCities
import com.tileshell.core.data.clock.start
import com.tileshell.core.data.clock.stop
import com.tileshell.core.data.clock.totalMs
import com.tileshell.core.data.clock.worldRow
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.HubAppBar
import com.tileshell.core.design.HubAppBarAction
import com.tileshell.core.design.HubFilter
import com.tileshell.core.design.HubPanorama
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId

private val CLOCK_PIVOTS = listOf("alarms", "world", "timer", "stopwatch", "sets")

/** A tick of the wall clock every [intervalMs] while [enabled]: how the on-screen countdowns move. */
@Composable
private fun rememberNow(enabled: Boolean, intervalMs: Long): State<Long> =
    produceState(System.currentTimeMillis(), enabled, intervalMs) {
        value = System.currentTimeMillis()
        while (enabled) {
            delay(intervalMs - (System.currentTimeMillis() % intervalMs))
            value = System.currentTimeMillis()
        }
    }

/**
 * The clock hub: alarms (the system's next alarm, with a hand-off to the Clock
 * app, which owns and rings alarms), world clocks, timers, a stopwatch, and
 * timer sets (a saved sequence of timers for a practice or workout, with a
 * buzz between steps that works with the screen off).
 *
 * Nothing here needs a restricted permission: the timers use alarm-clock
 * alarms and a vibration ([ClockSessions]). The on-screen numbers tick only
 * while the hub is open.
 */
@Composable
fun ClockHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onPinHub: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "clockHubProgress",
    )
    if (!visible && progress == 0f) return

    val baseTokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { CLOCK_PIVOTS.size })

    val sessions by ClockSessions.flow(context).collectAsState()
    val sets by ClockStore.setsFlow(context).collectAsState()
    val cities by ClockStore.citiesFlow(context).collectAsState()
    val stopwatch by ClockStore.stopwatchFlow(context).collectAsState()

    var dimPref by remember { mutableStateOf(ClockStore.dimWhileRunning(context)) }
    var brightUntil by remember { mutableLongStateOf(0L) }
    var editor by remember { mutableStateOf<TimerSet?>(null) }
    var runningId by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var addingCity by remember { mutableStateOf(false) }
    var editWorld by remember { mutableStateOf(false) }

    fun back() {
        when {
            editor != null -> editor = null
            runningId != null -> runningId = null
            settingsOpen -> settingsOpen = false
            addingCity -> addingCity = false
            else -> onDismiss()
        }
    }
    BackHandler(enabled = visible) { back() }

    val stopwatchRunning = stopwatch.running && pagerState.currentPage == 3
    val now by rememberNow(visible, 1000L)
    // A step ending while the hub is open is moved on right here, exactly on time.
    LaunchedEffect(now / 1000) { ClockSessions.tick(context) }
    // A set running in front keeps the screen on and, unless turned off or just tapped, nearly dark.
    val runningSession = runningId?.let { id -> sessions.firstOrNull { it.id == id } }
    val dimmed = visible && runningSession != null && dimPref && now >= brightUntil
    val tokens = if (dimmed) colorTokens(true) else baseTokens
    KeepScreenOn(keepOn = visible && runningSession != null, dim = dimmed)
    val fastNow by rememberNow(visible && stopwatchRunning, 50L)

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
            val draft = editor
            val running = runningId?.let { id -> sessions.firstOrNull { it.id == id } }
            when {
                draft != null -> SetEditor(
                    draft, tokens, accent,
                    onSave = { ClockStore.saveSet(context, it); editor = null },
                    onDelete = { ClockStore.deleteSet(context, it.id); editor = null },
                    modifier = Modifier.weight(1f),
                )
                runningId != null -> {
                    // Stopped or finished: the set is gone, so go back to the list.
                    LaunchedEffect(running == null) { if (running == null) runningId = null }
                    RunScreen(running, now, tokens, accent, dimmed, onBrighten = { brightUntil = System.currentTimeMillis() + 8_000L }, modifier = Modifier.weight(1f))
                }
                settingsOpen -> ClockSettings(tokens, accent, dimPref, { dimPref = it; ClockStore.setDimWhileRunning(context, it) }, modifier = Modifier.weight(1f))
                else -> HubPanorama(
                    title = "clock",
                    sections = CLOCK_PIVOTS,
                    pagerState = pagerState,
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        0 -> AlarmsPage(now, sessions, tokens, accent) { target -> scope.launch { pagerState.animateScrollToPage(target) } }
                        1 -> WorldPage(now, cities, addingCity, editWorld, { addingCity = it }, tokens, accent)
                        2 -> TimerPage(now, sessions.filter { it.isTimer }, tokens, accent)
                        3 -> StopwatchPage(stopwatch, if (stopwatchRunning) fastNow else now, tokens, accent)
                        else -> SetsPage(
                            now, sets, sessions.filter { !it.isTimer }, tokens, accent,
                            onNew = { editor = TimerSet(System.currentTimeMillis().toString(), "", listOf(TimerPart("", listOf(TimerStep(seconds = 120))))) },
                            onEdit = { editor = it },
                            onOpen = { runningId = it },
                        )
                    }
                }
            }
            HubAppBar(
                tokens = tokens,
                actions = buildList {
                    add(HubAppBarAction("back", "back") { back() })
                    val onPanorama = editor == null && runningId == null && !settingsOpen
                    if (onPanorama && pagerState.currentPage == 1) {
                        add(HubAppBarAction("plus", "add a city", "add") { addingCity = true; editWorld = false })
                        add(HubAppBarAction(if (editWorld) "check" else "edit", "edit cities", if (editWorld) "done" else "edit") { editWorld = !editWorld })
                    }
                    if (onPanorama && pagerState.currentPage == 4) {
                        add(HubAppBarAction("plus", "new timer set", "new set") {
                            editor = TimerSet(System.currentTimeMillis().toString(), "", listOf(TimerPart("", listOf(TimerStep(seconds = 120)))))
                        })
                    }
                    add(HubAppBarAction("settings", "clock settings", "settings") { settingsOpen = !settingsOpen })
                    add(HubAppBarAction("pin", "pin clock to start", "pin to start", onPinHub))
                },
            )
        }
    }
}

// --- alarms --------------------------------------------------------------------------

@Composable
private fun AlarmsPage(now: Long, sessions: List<Session>, tokens: ColorTokens, accent: Color, goTo: (Int) -> Unit) {
    val context = LocalContext.current
    // The system's single next alarm, read afresh each minute (it changes only when an alarm is set, cleared or rings).
    val info = remember(now / 60_000) { (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.nextAlarmClock }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "dial") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ClockDial(now, info?.triggerTime, tokens, accent, Modifier.fillMaxWidth(0.82f))
                if (info == null) {
                    Text("no alarm set", color = tokens.fgDim, fontSize = 22.sp, fontWeight = FontWeight.ExtraLight, modifier = Modifier.padding(top = 10.dp))
                } else {
                    val source = remember(info) { alarmSourceFor(context, info) }
                    val at = java.time.Instant.ofEpochMilli(info.triggerTime).atZone(ZoneId.systemDefault())
                    Text(clockLabel(at.hour, at.minute), color = tokens.fg, fontSize = 36.sp, fontWeight = FontWeight.ExtraLight, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        "next alarm · in ${untilLabel(info.triggerTime - now)} · " + dayLabel(at, now),
                        color = tokens.fgDim, fontSize = 12.sp,
                    )
                    Text(alarmCaption("", source?.appLabel.orEmpty(), source?.isClockApp == true), color = accent, fontSize = 12.sp)
                }
            }
        }
        item(key = "open") {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    "add or change an alarm ›",
                    color = accent, fontSize = 16.sp,
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                )
                Text("opens your clock app, which rings them", color = tokens.fgDim, fontSize = 12.sp)
            }
        }
        val timers = sessions.count { it.isTimer }
        val sets = sessions.size - timers
        item(key = "jump-timers") { JumpRow("timers", if (timers == 0) "none running" else "$timers running", tokens, accent) { goTo(2) } }
        item(key = "jump-sets") { JumpRow("timer sets", if (sets == 0) "none running" else sets.toString() + " running · " + sessions.first { !it.isTimer }.current.label, tokens, accent) { goTo(4) } }
        item(key = "jump-world") { JumpRow("world clocks", "other cities' times", tokens, accent) { goTo(1) } }
    }
}

@Composable
private fun JumpRow(title: String, sub: String, tokens: ColorTokens, accent: Color, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = tokens.fg, fontSize = 20.sp, fontWeight = FontWeight.Light)
                Text(sub, color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = accent, fontSize = 20.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
    }
}

/** "14h 12m", "12m", "45s": how far off something is. */
private fun untilLabel(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h >= 24 -> "${h / 24}d ${h % 24}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

private fun clockLabel(hour: Int, minute: Int): String {
    val h12 = (hour % 12).let { if (it == 0) 12 else it }
    return "$h12:%02d %s".format(minute, if (hour < 12) "am" else "pm")
}

private fun dayLabel(at: java.time.ZonedDateTime, now: Long): String {
    val today = java.time.Instant.ofEpochMilli(now).atZone(at.zone).toLocalDate()
    return when (at.toLocalDate()) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> at.dayOfWeek.name.take(3).lowercase() + " " + at.dayOfMonth
    }
}

// --- world ---------------------------------------------------------------------------

@Composable
private fun WorldPage(
    now: Long,
    cities: List<String>,
    adding: Boolean,
    editing: Boolean,
    setAdding: (Boolean) -> Unit,
    tokens: ColorTokens,
    accent: Color,
) {
    val context = LocalContext.current
    val home = remember { ZoneId.systemDefault() }
    if (adding) {
        AddCity(tokens, accent) { picked ->
            ClockStore.addCity(context, picked.name)
            setAdding(false)
        }
        return
    }
    val homeName = remember(home) { home.id.substringAfterLast('/').replace('_', ' ') }
    val rows = remember(cities, now / 60_000) {
        listOf(WorldCity(homeName, home.id) to true) + cities.mapNotNull { name -> WORLD_CITIES.firstOrNull { it.name == name }?.let { it to false } }
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        items(rows, key = { (city, isHome) -> (if (isHome) "home-" else "") + city.name }) { (city, isHome) ->
            val row = worldRow(city, now, home)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.time, color = tokens.fg, fontSize = 30.sp, fontWeight = FontWeight.ExtraLight)
                        Text(
                            listOfNotNull(row.name, if (isHome) "home" else null, row.dayNote.ifEmpty { null }, row.offsetNote.ifEmpty { null }).joinToString(" · "),
                            color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(if (row.day) "day" else "night", color = if (row.day) accent else tokens.fgDim, fontSize = 13.sp)
                    if (editing && !isHome) {
                        Text(
                            "✕", color = tokens.fgDim, fontSize = 18.sp,
                            modifier = Modifier.clickable { ClockStore.removeCity(context, city.name) }.padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                        )
                    }
                }
                DayStrip(row.dayFraction, tokens, accent, Modifier.fillMaxWidth().height(8.dp))
                Spacer(Modifier.height(8.dp))
            }
        }
        item(key = "add") {
            Text("add a city ›", color = accent, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp).clickable { setAdding(true) })
        }
    }
}

@Composable
private fun AddCity(tokens: ColorTokens, accent: Color, onPick: (WorldCity) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { searchCities(query) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "box") { TextBox(query, "search a city", { query = it }, tokens, accent) }
        items(results, key = { it.name }) { city ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onPick(city) }.padding(vertical = 10.dp)) {
                Text(city.name, color = tokens.fg, fontSize = 18.sp, fontWeight = FontWeight.Light)
                Text(city.zoneId.replace('_', ' '), color = tokens.fgDim, fontSize = 12.sp)
            }
        }
    }
}

// --- timer ---------------------------------------------------------------------------------

@Composable
private fun TimerPage(now: Long, timers: List<Session>, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    var custom by remember { mutableStateOf("") }
    fun startTimer(seconds: Int) {
        ClockSessions.start(context, "timer", listOf(SessionStep("timer", seconds * 1000L)), isTimer = true)
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "ring") {
            val first = timers.firstOrNull()
            val remaining = first?.remainingMs(now) ?: 0L
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProgressRing(
                    fraction = if (first == null) 0f else remaining.toFloat() / first.current.ms,
                    tokens = tokens, accent = accent, modifier = Modifier.fillMaxWidth(0.72f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (first == null) "0:00" else formatDuration((remaining + 999) / 1000),
                            color = if (first == null || first.paused) tokens.fgDim else tokens.fg, fontSize = 46.sp, fontWeight = FontWeight.ExtraLight,
                        )
                        Text(
                            if (first == null) "no timer running" else if (first.paused) "paused" else "ends " + endsAtLabel(first.stepEndsAt),
                            color = tokens.fgDim, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        item(key = "presets") {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text("start a timer", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(1 to "1 min", 5 to "5 min", 10 to "10 min", 20 to "20 min").forEach { (minutes, label) ->
                        Text(label, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable { startTimer(minutes * 60) }.padding(vertical = 6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    Box(modifier = Modifier.width(120.dp)) { TextBox(custom, "minutes", { custom = it }, tokens, accent, keyboard = KeyboardType.Text) }
                    val seconds = parseDuration(custom)
                    Text(
                        if (seconds != null && seconds > 0) "start ${formatDuration(seconds.toLong())}" else "start",
                        color = if (seconds != null && seconds > 0) accent else tokens.fgDim, fontSize = 16.sp,
                        modifier = Modifier.padding(start = 16.dp).clickable(enabled = seconds != null && seconds > 0) { startTimer(seconds!!); custom = "" },
                    )
                }
                Text("6 is six minutes · 1:30 is a minute and a half · 90s is ninety seconds", color = tokens.fgDim, fontSize = 11.sp)
            }
        }
        if (timers.isEmpty()) {
            item(key = "none") { Text("no timers running", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 14.dp)) }
        }
        items(timers, key = { it.id }) { t ->
            val remaining = t.remainingMs(now)
            Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatDuration((remaining + 999) / 1000), color = if (t.paused) tokens.fgDim else tokens.fg, fontSize = 26.sp, fontWeight = FontWeight.ExtraLight, modifier = Modifier.weight(1f))
                    Text(if (t.paused) "resume" else "pause", color = accent, fontSize = 15.sp, modifier = Modifier.clickable { if (t.paused) ClockSessions.resume(context, t.id) else ClockSessions.pause(context, t.id) }.padding(8.dp))
                    Text("cancel", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.clickable { ClockSessions.stop(context, t.id) }.padding(8.dp))
                }
                Text(
                    "of ${formatDuration(t.current.ms / 1000)}" + if (t.paused) " · paused" else " · ends " + endsAtLabel(t.stepEndsAt),
                    color = tokens.fgDim, fontSize = 12.sp,
                )
                ProgressLine((1f - remaining.toFloat() / t.current.ms).coerceIn(0f, 1f), accent, tokens)
            }
        }
    }
}

private fun endsAtLabel(epochMillis: Long): String {
    val at = java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return clockLabel(at.hour, at.minute)
}

@Composable
private fun ProgressLine(fraction: Float, accent: Color, tokens: ColorTokens) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp).background(tokens.sheetLine)) {
        Box(modifier = Modifier.fillMaxWidth(fraction).height(3.dp).background(accent))
    }
}

// --- stopwatch ----------------------------------------------------------------------------------

@Composable
private fun StopwatchPage(state: StopwatchState, now: Long, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "time") {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                StopwatchRing(state.elapsedMs(now), state.laps, tokens, accent, Modifier.fillMaxWidth(0.72f)) {
                    Text(formatStopwatch(state.elapsedMs(now)), color = tokens.fg, fontSize = 34.sp, fontWeight = FontWeight.ExtraLight)
                }
            }
        }
        item(key = "buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                    if (state.running) "stop" else "start",
                    color = accent, fontSize = 20.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable { ClockStore.updateStopwatch(context) { if (it.running) it.stop(System.currentTimeMillis()) else it.start(System.currentTimeMillis()) } }.padding(vertical = 6.dp),
                )
                Text(
                    "lap", color = if (state.running) accent else tokens.fgDim, fontSize = 20.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable(enabled = state.running) { ClockStore.updateStopwatch(context) { it.lap(System.currentTimeMillis()) } }.padding(vertical = 6.dp),
                )
                Text(
                    "reset", color = if (!state.running && (state.accumulatedMs > 0 || state.laps.isNotEmpty())) accent else tokens.fgDim, fontSize = 20.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable(enabled = !state.running) { ClockStore.updateStopwatch(context) { it.reset() } }.padding(vertical = 6.dp),
                )
            }
        }
        val lengths = state.lapLengths()
        items(state.laps.indices.reversed().toList(), key = { "lap-$it" }) { i ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("lap ${i + 1}", color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(formatStopwatch(lengths[i]), color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light)
                    Text(formatStopwatch(state.laps[i]), color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
            }
        }
    }
}

// --- sets --------------------------------------------------------------------------------------

@Composable
private fun SetsPage(
    now: Long,
    sets: List<TimerSet>,
    running: List<Session>,
    tokens: ColorTokens,
    accent: Color,
    onNew: () -> Unit,
    onEdit: (TimerSet) -> Unit,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        items(running, key = { "run-" + it.id }) { s ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(s.id) }.padding(bottom = 12.dp)) {
                Text("running", color = accent, fontSize = 12.sp)
                Text(s.title, color = tokens.fg, fontSize = 20.sp, fontWeight = FontWeight.Light)
                Text(
                    s.current.label + " · " + formatDuration((s.remainingMs(now) + 999) / 1000) + if (s.paused) " · paused" else "",
                    color = tokens.fgDim, fontSize = 13.sp,
                )
                ProgressLine(1f - s.totalRemainingMs(now).toFloat() / s.totalMs(), accent, tokens)
            }
        }
        if (sets.isEmpty()) {
            item(key = "empty") {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("a set is a row of timers for a practice or workout, with a buzz between steps, even with the screen off.", color = tokens.fgDim, fontSize = 14.sp)
                }
            }
        }
        items(sets, key = { it.id }) { set ->
            val steps = flatten(set).size
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.weight(1f).clickable { onEdit(set) }) {
                        Text(set.name.ifBlank { "untitled set" }, color = tokens.fg, fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${set.parts.size} part${if (set.parts.size == 1) "" else "s"} · $steps step${if (steps == 1) "" else "s"} · ${formatDuration(totalMs(set) / 1000)}", color = tokens.fgDim, fontSize = 12.sp)
                    }
                    Text(
                        "start", color = if (steps > 0) accent else tokens.fgDim, fontSize = 16.sp,
                        modifier = Modifier.clickable(enabled = steps > 0) {
                            ClockSessions.start(context, set.name.ifBlank { "timer set" }, flatten(set).map { SessionStep(it.label, it.ms, it.partName) })?.let { onOpen(it.id) }
                        }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                StructureBar(set, tokens, accent)
                Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(0.5.dp).background(tokens.sheetLine))
            }
        }
        item(key = "new") { Text("+ new set", color = accent, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp).clickable(onClick = onNew)) }
    }
}

// --- running a set -----------------------------------------------------------------------------

@Composable
private fun RunScreen(
    session: Session?,
    now: Long,
    tokens: ColorTokens,
    accent: Color,
    dimmed: Boolean,
    onBrighten: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ClockSubScreen("TIMER SET", session?.title ?: "done", tokens, modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBrighten() }) {
        if (session == null) {
            Text("this set has finished", color = tokens.fgDim, fontSize = 16.sp, modifier = Modifier.padding(18.dp))
            return@ClockSubScreen
        }
        val remaining = session.remainingMs(now)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SegmentedRing(session, now, tokens, accent, Modifier.fillMaxWidth(0.86f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatDuration((remaining + 999) / 1000),
                        color = if (session.paused) tokens.fgDim else tokens.fg, fontSize = 54.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-2).sp,
                    )
                    Text(session.current.label, color = accent, fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                    Text(
                        formatDuration((session.totalRemainingMs(now) + 999) / 1000) + " left of " + formatDuration(session.totalMs() / 1000),
                        color = tokens.fgDim, fontSize = 12.sp,
                    )
                }
            }
            val parts = remember(session.id) { session.steps.groupBy { it.part }.filterKeys { it.isNotBlank() } }
            if (parts.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                    parts.forEach { (name, steps) -> Text("$name ${formatDuration(steps.sumOf { it.ms } / 1000)}", color = tokens.fgDim, fontSize = 11.sp) }
                }
            }
            session.next?.let { next ->
                Text("next: " + next.label + " · " + formatDuration(next.ms / 1000), color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(34.dp), modifier = Modifier.padding(top = 18.dp)) {
                Text(if (session.paused) "resume" else "pause", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable { if (session.paused) ClockSessions.resume(context, session.id) else ClockSessions.pause(context, session.id) }.padding(vertical = 6.dp))
                if (session.next != null) Text("skip", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable { ClockSessions.skip(context, session.id) }.padding(vertical = 6.dp))
                Text("stop", color = tokens.fgDim, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable { ClockSessions.stop(context, session.id) }.padding(vertical = 6.dp))
            }
            Text(
                when {
                    dimmed -> "the screen stays on, dimmed, so every step is exact. tap to brighten."
                    ClockSessions.exactAlarmsAllowed(context) -> "keeps going with the screen off. a short buzz moves you on."
                    else -> "keeps going with the screen off, but the buzz may come late there. allow alarms and reminders in settings for exact timing."
                },
                color = tokens.fgDim, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/** A set's shape as one bar: a block per step as long as the step, a wider gap between parts. */
@Composable
private fun StructureBar(set: TimerSet, tokens: ColorTokens, accent: Color) {
    val steps = remember(set) { flatten(set) }
    if (steps.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        steps.forEachIndexed { i, step ->
            Box(modifier = Modifier.weight(step.ms.toFloat()).height(5.dp).background(accent.copy(alpha = 0.6f)))
            if (steps.getOrNull(i + 1)?.let { it.partName != step.partName } == true) Spacer(Modifier.width(8.dp))
        }
    }
}

// --- set editor ----------------------------------------------------------------------------------

@Composable
private fun SetEditor(
    initial: TimerSet,
    tokens: ColorTokens,
    accent: Color,
    onSave: (TimerSet) -> Unit,
    onDelete: (TimerSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    fun partAt(p: Int, change: (TimerPart) -> TimerPart) {
        draft = draft.copy(parts = draft.parts.mapIndexed { i, part -> if (i == p) change(part) else part })
    }
    ClockSubScreen("TIMER SET", if (initial.name.isBlank() && draft.name.isBlank()) "new set" else draft.name.ifBlank { "untitled" }, tokens, modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            item(key = "name") { TextBox(draft.name, "name of the set", { draft = draft.copy(name = it) }, tokens, accent, keyboard = KeyboardType.Text) }
            item(key = "total") {
                Text("${draft.parts.size} part${if (draft.parts.size == 1) "" else "s"} · ${formatDuration(totalMs(draft) / 1000)} in all", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            draft.parts.forEachIndexed { p, part ->
                item(key = "part-$p") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) { TextBox(part.name, "part ${p + 1} name (preparatory, kriya…)", { v -> partAt(p) { it.copy(name = v) } }, tokens, accent, keyboard = KeyboardType.Text) }
                            if (draft.parts.size > 1) {
                                Text("✕", color = tokens.fgDim, fontSize = 18.sp, modifier = Modifier.clickable { draft = draft.copy(parts = draft.parts.filterIndexed { i, _ -> i != p }) }.padding(start = 14.dp, top = 6.dp, bottom = 6.dp))
                            }
                        }
                        if (part.steps.any { it.remainder }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                Text("part total", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.width(90.dp))
                                Box(modifier = Modifier.width(110.dp)) {
                                    DurationBox(part.totalSeconds ?: 0, "21", tokens, accent) { s -> partAt(p) { it.copy(totalSeconds = s) } }
                                }
                            }
                        }
                    }
                }
                items(part.steps.size, key = { "step-$p-$it" }) { s ->
                    val step = part.steps[s]
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            TextBox(step.name, if (step.remainder) "remaining" else "step ${s + 1}", { v -> partAt(p) { pt -> pt.copy(steps = pt.steps.mapIndexed { i, st -> if (i == s) st.copy(name = v) else st }) } }, tokens, accent, keyboard = KeyboardType.Text)
                        }
                        Spacer(Modifier.width(10.dp))
                        if (step.remainder) {
                            Text("the rest", color = accent, fontSize = 14.sp, modifier = Modifier.width(70.dp).clickable { partAt(p) { pt -> pt.copy(steps = pt.steps.mapIndexed { i, st -> if (i == s) st.copy(remainder = false) else st }) } }, textAlign = TextAlign.Center)
                        } else {
                            Box(modifier = Modifier.width(70.dp)) {
                                DurationBox(step.seconds, "2", tokens, accent) { v -> partAt(p) { pt -> pt.copy(steps = pt.steps.mapIndexed { i, st -> if (i == s) st.copy(seconds = v) else st }) } }
                            }
                        }
                        if (s == part.steps.lastIndex && !step.remainder && part.steps.none { it.remainder }) {
                            Text("rest", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.clickable { partAt(p) { pt -> pt.copy(steps = pt.steps.mapIndexed { i, st -> if (i == s) st.copy(remainder = true) else st }, totalSeconds = pt.totalSeconds ?: pt.steps.sumOf { it.seconds }) } }.padding(start = 10.dp, top = 6.dp, bottom = 6.dp))
                        }
                        Text("✕", color = tokens.fgDim, fontSize = 16.sp, modifier = Modifier.clickable { partAt(p) { pt -> pt.copy(steps = pt.steps.filterIndexed { i, _ -> i != s }) } }.padding(start = 10.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
                item(key = "addstep-$p") {
                    Text("+ add a step", color = accent, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp).clickable { partAt(p) { it.copy(steps = it.steps.filterNot { st -> st.remainder } + TimerStep(seconds = it.steps.lastOrNull { st -> !st.remainder }?.seconds ?: 120) + it.steps.filter { st -> st.remainder }) } })
                }
            }
            item(key = "addpart") {
                Text("+ add a part", color = accent, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp).clickable { draft = draft.copy(parts = draft.parts + TimerPart("", listOf(TimerStep(seconds = 120)))) })
            }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                    val ok = flatten(draft).isNotEmpty()
                    Text("save", color = if (ok) accent else tokens.fgDim, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable(enabled = ok) { onSave(draft) }.padding(vertical = 6.dp))
                    Text("delete", color = tokens.fgDim, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.clickable { onDelete(draft) }.padding(vertical = 6.dp))
                }
            }
        }
    }
}

// --- settings --------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClockSettings(tokens: ColorTokens, accent: Color, dim: Boolean, onDim: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var strength by remember { mutableStateOf(ClockStore.buzzStrength(context)) }
    var sound by remember { mutableStateOf(ClockStore.soundToo(context)) }
    val notifications = remember { android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    ClockSubScreen("CLOCK", "settings", tokens, modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Text("buzz at the end of a step", color = tokens.fgDim, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BuzzStrength.entries.forEach { b -> HubFilter(b.label, strength == b, tokens, accent) { strength = b; ClockStore.setBuzzStrength(context, b) } }
            }
            Text("sound as well", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HubFilter("off", !sound, tokens, accent) { sound = false; ClockStore.setSoundToo(context, false) }
                HubFilter("a chime", sound, tokens, accent) { sound = true; ClockStore.setSoundToo(context, true) }
            }
            Text("dim the screen while a set runs", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HubFilter("on", dim, tokens, accent) { onDim(true) }
                HubFilter("off", !dim, tokens, accent) { onDim(false) }
            }
            Text("a running set keeps the screen on, nearly dark, so every step is exact. tap the screen to brighten it for a few seconds.", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Text("the buzz follows your phone's vibration and do not disturb settings.", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            if (!ClockSessions.exactAlarmsAllowed(context)) {
                Text(
                    "for a buzz exactly on time with the screen off, allow alarms and reminders for tileshell ›",
                    color = accent, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 14.dp).clickable {
                        runCatching { context.startActivity(com.tileshell.core.data.reminders.TaskReminders.exactAccessIntent(context)) }
                    },
                )
            }
            if (!notifications) {
                Text(
                    "turn on notifications for tileshell to see the countdown on the lock screen ›",
                    color = accent, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 14.dp).clickable {
                        runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                )
            }
        }
    }
}

// --- shared bits ---------------------------------------------------------------------------------------

/** A page of its own inside the hub: the app name in small capitals over a large light title. */
@Composable
private fun ClockSubScreen(caption: String, title: String, tokens: ColorTokens, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(caption, color = tokens.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 18.dp))
        Text(title, color = tokens.fg, fontSize = 34.sp, fontWeight = FontWeight.Light, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun TextBox(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    tokens: ColorTokens,
    accent: Color,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Box(modifier = Modifier.fillMaxWidth().background(tokens.chip).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(placeholder, color = tokens.fgDim, fontSize = 15.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = tokens.fg, fontSize = 16.sp),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A length typed as "6", "6:30" or "90s", shown back as "6:00" once it makes sense. */
@Composable
private fun DurationBox(seconds: Int, placeholder: String, tokens: ColorTokens, accent: Color, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (seconds > 0) formatDuration(seconds.toLong()) else "") }
    TextBox(
        text, placeholder,
        { t ->
            text = t
            val parsed = parseDuration(t)
            if (parsed != null) onChange(parsed) else if (t.isBlank()) onChange(0)
        },
        tokens, accent,
    )
}
