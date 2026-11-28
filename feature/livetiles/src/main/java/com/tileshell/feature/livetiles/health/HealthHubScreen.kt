package com.tileshell.feature.livetiles.health

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.StepsPrefs
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.HubAppBar
import com.tileshell.core.design.rightScreenActions
import com.tileshell.core.design.rememberRightScreenPage
import com.tileshell.core.design.HubAppBarAction
import com.tileshell.core.design.HubFilter
import com.tileshell.core.design.HubPanorama
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.hubReveal
import com.tileshell.core.design.rememberHubOrigin
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import com.tileshell.feature.livetiles.HubAppsPage
import com.tileshell.feature.livetiles.HubKind
import com.tileshell.feature.livetiles.HubPageApp
import com.tileshell.feature.livetiles.HubPinNote
import com.tileshell.feature.livetiles.canShowSystemPermissionDialog
import com.tileshell.feature.livetiles.openApp
import com.tileshell.feature.livetiles.openAppPermissionSettings
import com.tileshell.feature.livetiles.rememberPermissionGranted
import com.tileshell.feature.livetiles.rememberStepsToday
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val HEALTH_PIVOTS = listOf("today", "walk", "sleep", "air", "week", "month", "apps")
private const val APPS_PAGE = 6
private val AtGoal = Color(0xFF7CB518)
private val GOAL_CHOICES = listOf(5_000, 8_000, 10_000, 12_000, 15_000)

private fun n(v: Int): String = String.format(Locale.US, "%,d", v)

/**
 * The health hub, from the phone's own step counter (no Health Connect, no account): "today" (a ring toward your goal,
 * with distance, calories and active minutes as estimates and your streak), "week" (this and last week by day) and
 * "apps" (your health and fitness apps, with an on-tile mark each). The history starts when the hub or tile is first
 * used and stays on this phone. The app bar holds back, settings (or edit apps) and pin.
 */
@Composable
fun HealthHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onPinHub: () -> Unit,
    pinMessages: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow(),
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "healthHubProgress",
    )
    if (!visible && progress == 0f) return
    val hubOrigin = rememberHubOrigin()

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        HealthStore.ensureLoaded(context)
        HealthSampleWorker.ensureScheduled(context)
    }
    val settings by HealthPrefs.settings(context).collectAsStateWithLifecycle()
    val history by HealthStore.history.collectAsStateWithLifecycle()
    val granted = rememberPermissionGranted(Manifest.permission.ACTIVITY_RECOGNITION)
    val steps = if (granted) rememberStepsToday(visible) else null

    var settingsOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = visible) { if (settingsOpen) settingsOpen = false else onDismiss() }
    // A tap on the tile while a walk runs lands on the walk page.
    val pagerState = rememberPagerState(initialPage = if (WalkSession.state.value.running) 1 else 0, pageCount = { HEALTH_PIVOTS.size })
    var appsEditing by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { if (pagerState.currentPage != APPS_PAGE) appsEditing = false }
    var airRefresh by remember { mutableStateOf(0) }
    val hourly by HealthStore.hourly.collectAsStateWithLifecycle()
    val walks by WalkSession.walks.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { WalkSession.loadWalks(context); SleepReminder.sync(context) }
    val s = settings ?: HealthSettings()

    SheetStage(rightHalf = rightHalf, spanHinge = true, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hubReveal(hubOrigin) { progress }
                .background(tokens.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (settingsOpen) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("HEALTH", color = tokens.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 18.dp))
                    Text("settings", color = tokens.fg, fontSize = 56.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                    HealthSettingsPage(s, tokens, accent)
                }
            } else {
                HubPanorama(
                    title = "health",
                    sections = HEALTH_PIVOTS,
                    pagerState = pagerState,
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        0 -> TodayPage(steps, granted, s, history, hourly, walks, tokens, accent)
                        1 -> WalkPage(granted, s, tokens, accent) { AccessLine(tokens, accent) }
                        2 -> SleepPage(s, tokens, accent) { settingsOpen = true }
                        3 -> AirPage(visible && pagerState.currentPage == 3, airRefresh, tokens, accent)
                        4 -> PeriodPage(false, s, history, walks, tokens, accent)
                        5 -> PeriodPage(true, s, history, walks, tokens, accent)
                        else -> HealthAppsPage(tokens, accent, appsEditing)
                    }
                }
            }
            HubPinNote(pinMessages, tokens, accent)
            // The bar's buttons for a section; the second screen of a split window gets the ones for its own section.
            val barActions: (Int) -> List<HubAppBarAction> = { forPage ->
                buildList {
                    add(HubAppBarAction("back", "back") { if (settingsOpen) settingsOpen = false else onDismiss() })
                    if (!settingsOpen && forPage == 3) add(HubAppBarAction("refresh", "refresh air quality", "refresh") { airRefresh++ })
                    if (!settingsOpen && forPage == APPS_PAGE) {
                        add(HubAppBarAction(if (appsEditing) "check" else "edit", "edit apps", if (appsEditing) "done" else "edit apps") { appsEditing = !appsEditing })
                    } else {
                        add(HubAppBarAction("settings", "health settings", "settings") { settingsOpen = !settingsOpen })
                    }
                    add(HubAppBarAction("pin", "pin health to start", "pin to start", onPinHub))
                }
            }
            val leftBar = barActions(pagerState.currentPage)
            val rightBar = rightScreenActions(leftBar, rememberRightScreenPage(pagerState)?.let(barActions))
            HubAppBar(
                tokens = tokens,
                actions = leftBar,
                rightActions = rightBar,
            )
        }
    }
}

// --- today ------------------------------------------------------------------------

@Composable
private fun AccessLine(tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { StepsPrefs.markPermissionAsked(context) }
    Text(
        "turn on physical activity so tileshell can count your steps ›",
        color = accent, fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 8.dp).clickable {
            val asked = StepsPrefs.permissionAsked(context)
            if (canShowSystemPermissionDialog(context, Manifest.permission.ACTIVITY_RECOGNITION, asked)) {
                request.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                openAppPermissionSettings(context)
            }
        },
    )
}

@Composable
private fun StepsRing(steps: Int, goal: Int, accent: Color, tokens: ColorTokens, sizeDp: Int, big: Boolean) {
    val progress = goalProgress(steps, goal)
    val track = tokens.sheetLine
    val color = if (steps >= goal) AtGoal else accent
    Box(modifier = Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.07f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(color, -90f, 360f * progress, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(n(steps), color = tokens.fg, fontSize = if (big) 34.sp else 22.sp, fontWeight = FontWeight.ExtraLight)
            if (big) Text("of ${n(goal)} steps", color = tokens.fgDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TodayPage(
    steps: Int?, granted: Boolean, settings: HealthSettings, history: List<DaySteps>,
    hourly: HourlySteps, walks: List<Walk>, tokens: ColorTokens, accent: Color,
) {
    val today = remember { LocalDate.now().toEpochDay() }
    // The live reading while it is there, else what the history last noted for today.
    val shown = steps ?: stepsOn(history, today)
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (!granted) item(key = "access") { AccessLine(tokens, accent) }
        item(key = "ring") {
            val hours = dayHours(hourly, today)
            val busiest = busiestHour(hours)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StepsRing(shown, settings.goal, accent, tokens, 170, big = true)
                Column(modifier = Modifier.weight(1f)) {
                    Text("most active hour", color = tokens.fgDim, fontSize = 12.sp)
                    Text(busiest?.let { hourRangeLabel(it) } ?: "—", color = tokens.fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraLight)
                    if (busiest != null) Text("${n(hours[busiest])} steps", color = tokens.fgDim, fontSize = 12.sp)
                }
            }
        }
        item(key = "byhour") {
            val zone = remember { java.time.ZoneId.systemDefault() }
            val nowHour = remember(shown) { (hourIndex(System.currentTimeMillis()) % 24).toInt() }
            StepsByHour(dayHours(hourly, today), walkHoursOfDay(walks, today, zone), nowHour, tokens, accent)
        }
        item(key = "gap") { Box(modifier = Modifier.height(10.dp)) }
        val rows = listOf(
            "distance" to com.tileshell.feature.livetiles.Units.distance(distanceKm(shown, settings.heightCm)),
            "calories (estimate)" to "${n(caloriesKcal(shown))} kcal",
            "active minutes (estimate)" to "${activeMinutes(shown)} min",
            "streak" to streak(history.let { recordDay(it, today, shown) }, today, settings.goal).let { if (it == 0) "no days at goal yet" else if (it == 1) "1 day at goal" else "$it days at goal" },
        )
        rows.forEach { (label, value) ->
            item(key = label) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = tokens.fg, fontSize = 15.sp, fontWeight = FontWeight.Light)
                        Text(value, color = tokens.fgDim, fontSize = 15.sp)
                    }
                }
            }
        }
        item(key = "note") {
            Text(
                "counted by your phone's step sensor, so only while you carry it. history starts when the hub or tile is first used.",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

// --- week -------------------------------------------------------------------------

/** The week and month pages: steps per day with a summary, then every walk in the period. */
@Composable
private fun PeriodPage(month: Boolean, settings: HealthSettings, history: List<DaySteps>, walks: List<Walk>, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    val today = remember { LocalDate.now().toEpochDay() }
    var previous by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }
    BackHandler(enabled = selected != null) { selected = null }
    val picked = selected?.let { id -> walks.firstOrNull { it.startMs == id } }
    if (picked != null) {
        WalkDetail(picked, settings, tokens, accent, onBack = { selected = null }, onDeleted = { selected = null })
        return
    }
    fun startOf(prev: Boolean): Long =
        if (month) monthStart(if (prev) monthStart(today) - 1 else today) else weekStart(today) - if (prev) 7 else 0
    fun lengthOf(start: Long) = if (month) monthLength(start) else 7
    val start = startOf(previous)
    val length = lengthOf(start)
    val stats = remember(history, start, settings.goal) { rangeStats(history, start, length, today, settings.goal) }
    val otherStart = startOf(!previous)
    val other = remember(history, otherStart, settings.goal) { rangeStats(history, otherStart, lengthOf(otherStart), today, settings.goal) }
    val periodWalks = remember(walks, start, length) { walksBetween(walks, start, start + length - 1) }
    val totals = remember(periodWalks, settings.heightCm) { walkTotals(periodWalks, settings.heightCm) }
    val top = maxOf(settings.goal, stats.days.filterNotNull().maxOrNull() ?: 0).coerceAtLeast(1)
    val noun = if (month) "month" else "week"
    val gap = if (month) 2.dp else 6.dp
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                listOf(false to "this $noun", true to "last $noun").forEach { (v, label) -> HubFilter(label, previous == v, tokens, accent) { previous = v } }
            }
        }
        item(key = "total") {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(n(stats.total), color = tokens.fg, fontSize = 40.sp, fontWeight = FontWeight.ExtraLight)
                Text("steps · ${n(stats.average)} a day", color = tokens.fgDim, fontSize = 12.sp)
            }
        }
        item(key = "bars") {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
                    stats.days.forEachIndexed { i, v ->
                        val day = stats.start + i
                        val frac = ((v ?: 0).toFloat() / top).coerceIn(0f, 1f)
                        val color = when {
                            v == null -> Color.Transparent
                            day == today -> accent
                            v >= settings.goal -> AtGoal
                            else -> tokens.sheetLine
                        }
                        Box(modifier = Modifier.weight(1f).height((110 * frac).coerceAtLeast(if (v != null) 2f else 0f).dp).background(color))
                    }
                }
                if (month) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(1, 8, 15, 22, length).forEach { Text("$it", color = tokens.fgDim, fontSize = 10.sp) }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (i in 0 until 7) {
                            Text(
                                LocalDate.ofEpochDay(stats.start + i).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH).lowercase(),
                                color = tokens.fgDim, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        val best = stats.bestIndex?.let { i ->
            val d = LocalDate.ofEpochDay(stats.start + i)
            val name = if (month) "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()}" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()
            "$name · ${n(stats.days[i] ?: 0)}"
        } ?: "—"
        val summary = buildList {
            add("best day" to best)
            add("days at goal" to "${stats.daysAtGoal} of ${stats.days.count { it != null }}")
            add("distance" to com.tileshell.feature.livetiles.Units.distance(distanceKm(stats.total, settings.heightCm)))
            add("active minutes" to n(activeMinutes(stats.total)))
            add("calories (estimate)" to n(caloriesKcal(stats.total)))
            add((if (previous) "this $noun" else "last $noun") to n(other.total))
        }
        summary.forEach { (label, value) ->
            item(key = label) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = tokens.fg, fontSize = 15.sp, fontWeight = FontWeight.Light)
                        Text(value, color = tokens.fgDim, fontSize = 15.sp)
                    }
                }
            }
        }
        item(key = "walks-head") {
            Caption(
                if (periodWalks.isEmpty()) "WALKS" else "WALKS · ${totals.count} · ${com.tileshell.feature.livetiles.Units.distance(totals.km)} · ${spokenDuration(totals.activeMs)}" +
                    if (totals.floors > 0) " · ${totals.floors} floors" else "",
                tokens, Modifier.padding(top = 22.dp, bottom = 6.dp),
            )
        }
        if (periodWalks.isEmpty()) {
            item(key = "walks-none") { Text("no walks this $noun", color = tokens.fgDim, fontSize = 14.sp) }
        } else {
            walksSection(periodWalks, settings, tokens, accent) { selected = it }
        }
    }
}

// --- apps -------------------------------------------------------------------------

@Composable
private fun HealthAppsPage(tokens: ColorTokens, accent: Color, editing: Boolean) {
    val context = LocalContext.current
    val apps = rememberHealthApps() ?: return
    val marks by HealthTileMarks.marks(context).collectAsStateWithLifecycle()
    HubAppsPage(
        kind = HubKind.HEALTH,
        sectionDefs = listOf(HealthAppKind.HEALTH.name to "fitness & health", HealthAppKind.WELLBEING.name to "wellbeing"),
        apps = apps.map { HubPageApp(it.packageName, it.label, it.kind.name) },
        editing = editing,
        tokens = tokens,
        accent = accent,
        emptyText = "no health or fitness apps found",
        onOpen = { openApp(context, it) },
        header = {
            Text(
                if (marks.isNullOrEmpty()) "▢ marks the apps on the health tile. none marked shows them all." else "▣ is on the health tile. tap to add or remove.",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp),
            )
        },
        subEntries = true,
        tileMarks = marks ?: emptySet(),
        onToggleTileMark = { HealthTileMarks.toggle(context, it) },
    )
}

// --- settings ---------------------------------------------------------------------

@Composable
private fun HealthSettingsPage(settings: HealthSettings, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("clear step history?") },
            text = { Text("this deletes the days tileshell has counted on this phone.") },
            confirmButton = { TextButton(onClick = { HealthStore.clear(context); confirmClear = false }) { Text("clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("cancel") } },
        )
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column {
            Text("daily goal", color = tokens.fg, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                (GOAL_CHOICES + settings.goal).distinct().sorted().forEach { g ->
                    HubFilter(n(g), settings.goal == g, tokens, accent) { HealthPrefs.update(context) { it.copy(goal = g) } }
                }
            }
        }
        Column {
            Text("height, for the distance estimate", color = tokens.fg, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 6.dp)) {
                Text("−", color = accent, fontSize = 26.sp, modifier = Modifier.clickable { HealthPrefs.update(context) { it.copy(heightCm = it.heightCm - 5) } }.padding(horizontal = 10.dp))
                Text(com.tileshell.feature.livetiles.Units.height(settings.heightCm), color = tokens.fg, fontSize = 18.sp, fontWeight = FontWeight.Light)
                Text("+", color = accent, fontSize = 26.sp, modifier = Modifier.clickable { HealthPrefs.update(context) { it.copy(heightCm = it.heightCm + 5) } }.padding(horizontal = 10.dp))
            }
        }
        Column {
            Text("bedtime reminder", color = tokens.fg, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                listOf(-1 to "off", 21 * 60 to "9 pm", 22 * 60 to "10 pm", 23 * 60 to "11 pm", 0 to "12 am").forEach { (m, label) ->
                    HubFilter(label, settings.bedtimeMinute == m, tokens, accent) {
                        HealthPrefs.update(context) { it.copy(bedtimeMinute = m) }
                        SleepReminder.sync(context)
                    }
                }
            }
            Text("a notification at that time each day, to wind down for the sleep estimate. off by default.", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column {
            Text("sound at each ${if (com.tileshell.feature.livetiles.Units.isImperial) "mile" else "kilometre"} of a walk", color = tokens.fg, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                listOf(0 to "off", 1 to "beep", 2 to "beep + time").forEach { (v, label) ->
                    HubFilter(label, settings.kmAlert == v, tokens, accent) { HealthPrefs.update(context) { it.copy(kmAlert = v) } }
                }
            }
            Text("the time is spoken by the phone's text-to-speech, for example \"2 kilometres, 9 minutes 40 seconds for the last one\".", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column {
            Text("record the route", color = tokens.fg, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                listOf(true to "on", false to "off").forEach { (v, label) ->
                    HubFilter(label, settings.recordRoute == v, tokens, accent) {
                        HealthPrefs.update(context) { it.copy(recordRoute = v) }
                        if (v) HealthPrefs.setRouteAsked(context, false)
                    }
                }
            }
            val loc = rememberLocationAsk()
            loc.instruction?.let { msg ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = loc.dismiss,
                    title = { Text("allow precise location") },
                    text = { Text(msg) },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = loc.proceed) { Text("open settings") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = loc.dismiss) { Text("not now") } },
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("location access", color = tokens.fg, fontSize = 14.sp)
                    Text(
                        when (loc.state) {
                            LocationState.PRECISE -> "allowed, precise"
                            LocationState.APPROXIMATE -> "approximate only, precise is needed"
                            LocationState.NONE -> "not allowed"
                        },
                        color = tokens.fgDim, fontSize = 12.sp,
                    )
                }
                if (loc.state != LocationState.PRECISE) Text(if (loc.state == LocationState.NONE) "allow" else "fix", color = accent, fontSize = 14.sp, modifier = Modifier.clickable(onClick = loc.act).padding(8.dp))
            }
            Text("uses GPS while a walk runs, only if you allow location, and gives a route and a more exact distance. the route is stored on this phone only and is never sent anywhere. you are asked whenever a walk starts without it, until you answer.", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column {
            Text("map behind the route", color = tokens.fg, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                listOf(true to "on", false to "off").forEach { (v, label) ->
                    HubFilter(label, settings.mapTiles == v, tokens, accent) { HealthPrefs.update(context) { it.copy(mapTiles = v) } }
                }
            }
            Text("when you open a walk with a route, map tiles for that area are downloaded from OpenStreetMap, which learns the area (not the time or who you are), and kept on this phone. off draws the route on a plain background.", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text("clear walks", color = Color(0xFFE5645A), fontSize = 15.sp, modifier = Modifier.clickable { WalkSession.clearWalks(context) }.padding(vertical = 4.dp))
        Text("clear step history", color = Color(0xFFE5645A), fontSize = 15.sp, modifier = Modifier.clickable { confirmClear = true }.padding(vertical = 4.dp))
        Text(
            "steps, walks and the sleep estimate come from your phone's own sensors and usage, and stay on this phone; they are not in backups. distance, calories, active minutes and sleep are estimates. air quality is read from open-meteo for the weather's place.",
            color = tokens.fgDim, fontSize = 12.sp,
        )
    }
}
