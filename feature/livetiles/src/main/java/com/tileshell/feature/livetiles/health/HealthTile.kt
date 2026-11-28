package com.tileshell.feature.livetiles.health

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tileshell.feature.livetiles.BatteryLog
import com.tileshell.feature.livetiles.Units
import com.tileshell.feature.livetiles.WeatherCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import com.tileshell.feature.livetiles.FlipTile
import com.tileshell.feature.livetiles.iconPx
import com.tileshell.feature.livetiles.openApp
import com.tileshell.feature.livetiles.rememberMonochromeAppIcon
import com.tileshell.feature.livetiles.rememberPermissionGranted
import com.tileshell.feature.livetiles.rememberStepsToday
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

private const val HEALTH_FLIP_MS = 6_000L

private val WalkGreen = Color(0xFF1F9E57)

/**
 * The health tile. Small: the ring and count. Medium: the ring with a strip of today's hours (the busiest solid), a
 * round walk button, and a back that takes turns over only the faces with something to say (sleep until noon, air
 * when it is worth a look, the last walk, the health apps). Wide and large show everything at once with labelled
 * buttons. While a walk runs the plate turns green (see `healthWalkingAccent`) and the face is the walk: time,
 * steps, distance, floors, pause and stop (stop asks "stop this walk?" once, then shows a summary for ten seconds).
 * Starting, pausing and stopping all go through [WalkSession], the one controller the notification, the quick
 * actions and the hub use too. Air comes from the cache only: the tile never fetches it.
 */
@Composable
fun HealthTileFace(size: TileSize, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HealthStore.ensureLoaded(context); WalkSession.loadWalks(context); BatteryLog.ensureStarted(context) }
    val settings by HealthPrefs.settings(context).collectAsStateWithLifecycle()
    val history by HealthStore.history.collectAsStateWithLifecycle()
    val hourly by HealthStore.hourly.collectAsStateWithLifecycle()
    val walk by WalkSession.state.collectAsStateWithLifecycle()
    val walks by WalkSession.walks.collectAsStateWithLifecycle()
    val granted = rememberPermissionGranted(Manifest.permission.ACTIVITY_RECOGNITION)
    LaunchedEffect(granted) { if (granted) HealthSampleWorker.ensureScheduled(context) }
    val live = if (granted) rememberStepsToday(active) else null
    val today = LocalDate.now().toEpochDay()
    val steps = live ?: if (granted) stepsOn(history, today).takeIf { it > 0 } else null
    val marks by HealthTileMarks.marks(context).collectAsStateWithLifecycle()
    val apps = healthAppsOnTile(rememberHealthApps().orEmpty().distinctBy { it.packageName }, marks.orEmpty())
    val s = settings ?: HealthSettings()
    val kind = healthTileKind(size)

    // Sleep (from the log) and air (cache only) for the faces that need them.
    val samples by BatteryLog.samples.collectAsStateWithLifecycle()
    val bucket = (samples.lastOrNull()?.time ?: 0L) / (30 * 60_000L)
    var sleep by remember { mutableStateOf<SleepEstimate?>(null) }
    LaunchedEffect(bucket, kind) {
        if (kind != HealthTileKind.SMALL) sleep = withContext(Dispatchers.Default) { lastNightEstimate(context, samples) }
    }
    var air by remember { mutableStateOf<AirSnapshot?>(null) }
    var place by remember { mutableStateOf("") }
    LaunchedEffect(kind, active) {
        if (kind == HealthTileKind.SMALL) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            air = AirRepository.peek(context)
            place = runCatching { WeatherCache.create(context).read().snapshot?.place.orEmpty() }.getOrDefault("")
        }
    }
    val now = System.currentTimeMillis()
    val hours = dayHours(hourly, today)
    val lastWalk = lastWalkEligible(walks, now)
    val faces = backFaces(
        BackInputs(
            sleep = sleepFaceEligible(sleep, now),
            air = air != null && airFaceEligible(air?.usAqi, air?.uvNow),
            lastWalk = lastWalk != null,
            apps = apps.isNotEmpty(),
        ),
    )

    // The walk question / summary shown over the tile.
    var walkUi by remember { mutableStateOf<WalkUi>(WalkUi.None) }
    LaunchedEffect(walk.running, walkUi) {
        walkUi = reduceWalkUi(walkUi, WalkUiEvent.Tick(System.currentTimeMillis(), walk.running))
        if (walkUi is WalkUi.Saved) {
            delay(SAVED_MS)
            walkUi = reduceWalkUi(walkUi, WalkUiEvent.Tick(System.currentTimeMillis(), WalkSession.state.value.running))
        }
    }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(walk.running, walk.paused, active) {
        elapsed = WalkSession.elapsedMs()
        while (walk.running && !walk.paused && active) {
            delay(1000)
            elapsed = WalkSession.elapsedMs()
        }
    }

    var flipped by remember { mutableStateOf(false) }
    var turn by remember { mutableIntStateOf(0) }
    val canFlip = kind == HealthTileKind.MEDIUM && faces.isNotEmpty() && !walk.running && walkUi is WalkUi.None
    LaunchedEffect(active, canFlip) {
        if (!active || !canFlip) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            delay(HEALTH_FLIP_MS)
            flipped = !flipped
            if (flipped) turn++
        }
    }

    val starter = rememberWalkStarter(s)
    val startWalk: () -> Unit = {
        if (WalkSession.canWalk(context)) starter()
        else Toast.makeText(context, "turn on physical activity in the health hub first", Toast.LENGTH_SHORT).show()
    }
    val km = walk.meters / 1000.0
    val ui = walkUi
    val face: @Composable () -> Unit = when {
        walk.running && ui is WalkUi.Confirm -> {
            { WalkConfirmFace(kind, onSave = { walkUi = reduceWalkUi(ui, WalkUiEvent.SaveTapped(WalkSession.stop(context), System.currentTimeMillis())) }, onKeep = { walkUi = reduceWalkUi(ui, WalkUiEvent.KeepGoing) }) }
        }
        walk.running -> {
            {
                WalkingFace(
                    kind, walk, elapsed, km,
                    onPause = { if (walk.paused) WalkSession.resume(context) else WalkSession.pause(context) },
                    onStop = { walkUi = reduceWalkUi(ui, WalkUiEvent.StopTapped) },
                )
            }
        }
        ui is WalkUi.Saved && kind != HealthTileKind.SMALL -> {
            { WalkSavedFace(ui.walk, walkKm(ui.walk, s.heightCm)) }
        }
        else -> {
            { StepsFront(kind, steps, s, hours, walks, sleep, air, history, today, startWalk) }
        }
    }
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { face() },
        back = {
            when (backFaceFor(faces, turn - 1)) {
                BackFace.SLEEP -> sleep?.let { SleepBack(it) }
                BackFace.AIR -> air?.let { AirBack(it, place, now) }
                BackFace.LAST_WALK -> lastWalk?.let { LastWalkBack(it, s, history, today, steps) }
                BackFace.APPS, null -> HealthAppsBack(apps, size, turn)
            }
        },
    )
}

/** The accent a health tile takes while a walk runs (green), else null; used for the plate in Start. */
@Composable
fun healthWalkingAccent(): Color? {
    val walk by WalkSession.state.collectAsStateWithLifecycle()
    return if (walk.running) WalkGreen else null
}

private fun hm(ms: Long): String {
    val m = ms / 60_000
    return "${m / 60}h ${m % 60}m"
}

@Composable
private fun FaceText(text: String, size: Int, modifier: Modifier = Modifier, dim: Boolean = false, weight: androidx.compose.ui.text.font.FontWeight? = null) {
    val c = LocalTileFaceColor.current
    Text(text, color = if (dim) c.copy(alpha = 0.8f) else c, fontSize = size.sp, maxLines = 2, fontWeight = weight, modifier = modifier)
}

/** A thin strip of today's hourly bars; the busiest hour is solid, the rest about 40%. */
@Composable
private fun HourStrip(hours: List<Int>, modifier: Modifier) {
    val color = LocalTileFaceColor.current
    val busiest = busiestHour(hours)
    val top = (hours.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier = modifier) {
        val w = size.width / 24f
        hours.forEachIndexed { h, v ->
            val bh = (size.height * v / top).coerceAtLeast(1.dp.toPx())
            drawRect(color.copy(alpha = if (h == busiest) 1f else 0.4f), Offset(h * w + 0.5f, size.height - bh), Size((w - 1f).coerceAtLeast(1f), bh))
        }
    }
}

@Composable
private fun RoundButton(icon: String, filled: Boolean, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTileFaceColor.current
    Box(
        modifier = modifier.size(34.dp).clip(CircleShape)
            .then(if (filled) Modifier.background(c) else Modifier.border(1.5.dp, c, CircleShape))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(TileIcons[icon], contentDescription = icon, tint = tint, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun LabelButton(label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTileFaceColor.current
    Box(
        modifier = modifier
            .then(if (filled) Modifier.background(c) else Modifier.border(1.5.dp, c))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (filled) WalkGreen else c, fontSize = 13.sp, maxLines = 1) }
}

/** The tile at rest: ring and what else the size has room for. */
@Composable
private fun StepsFront(
    kind: HealthTileKind, steps: Int?, s: HealthSettings, hours: List<Int>, walks: List<Walk>,
    sleep: SleepEstimate?, air: AirSnapshot?, history: List<DaySteps>, today: Long, onStart: () -> Unit,
) {
    val color = LocalTileFaceColor.current
    val goal = s.goal
    if (steps == null) {
        HealthGlyphFront(if (kind == HealthTileKind.SMALL) TileSize.SMALL else TileSize.MEDIUM)
        return
    }
    val progress = goalProgress(steps, goal)
    val number = String.format(Locale.US, "%,d", steps)
    val now = System.currentTimeMillis()
    val lastWalk = lastWalkEligible(walks, now)
    when (kind) {
        HealthTileKind.SMALL -> HealthRingFront(steps, goal, TileSize.SMALL)
        HealthTileKind.MEDIUM -> Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Column(modifier = Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Ring(color, progress, Modifier.size(70.dp))
                    Text(number, color = color, fontSize = 13.sp, maxLines = 1)
                }
                HourStrip(hours, Modifier.padding(top = 8.dp).fillMaxWidth().height(14.dp))
            }
            Text("health", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart))
            RoundButton("steps", false, color, onStart, Modifier.align(Alignment.BottomEnd))
        }
        HealthTileKind.WIDE -> Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(modifier = Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Ring(color, progress, Modifier.size(84.dp))
                    Text(number, color = color, fontSize = 15.sp, maxLines = 1)
                }
                Column {
                    FaceText("${(progress * 100).toInt()}% of ${String.format(Locale.US, "%,d", goal)}", 14)
                    sleep?.let { FaceText("slept ${hm(it.durationMs)}", 12, dim = true) }
                    air?.usAqi?.let { aqi -> FaceText("air $aqi" + (air.uvNow?.let { " · uv ${it.toInt()}" } ?: ""), 12, dim = true) }
                    lastWalk?.let { w -> FaceText("walk ${Units.distance(walkKm(w, s.heightCm))}" + if (walkAscentM(w) > 0) " · ${Units.elevation(walkAscentM(w))} up" else "", 12, dim = true) }
                }
            }
            Text("health", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart))
            LabelButton("start walk", false, onStart, Modifier.align(Alignment.BottomEnd))
        }
        HealthTileKind.LARGE -> Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Ring(color, progress, Modifier.size(84.dp))
                        Text(number, color = color, fontSize = 15.sp, maxLines = 1)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        InfoLine("moon", sleep?.let { hm(it.durationMs) })
                        InfoLine("cloud", air?.usAqi?.let { "aqi $it" })
                        InfoLine("brightness", air?.uvNow?.let { "uv ${it.toInt()}" })
                        InfoLine("steps", lastWalk?.takeIf { walkAscentM(it) > 0 }?.let { "${Units.elevation(walkAscentM(it))} up" })
                    }
                }
                Spacer(Modifier.height(10.dp))
                FaceText("STEPS BY HOUR", 10, dim = true)
                HourStrip(hours, Modifier.padding(top = 4.dp).fillMaxWidth().weight(1f))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("12a", "6a", "12p", "6p", "12a").forEach { FaceText(it, 9, dim = true) }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaceText("health", 12)
                    Spacer(Modifier.weight(1f))
                    LabelButton("start walk", false, onStart)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: String, text: String?) {
    if (text == null) return
    val color = LocalTileFaceColor.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(TileIcons[icon], contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(text, color = color, fontSize = 13.sp, maxLines = 1)
    }
}

/** The walk in progress. */
@Composable
private fun WalkingFace(kind: HealthTileKind, walk: WalkState, elapsed: Long, km: Double, onPause: () -> Unit, onStop: () -> Unit) {
    val color = LocalTileFaceColor.current
    if (kind == HealthTileKind.SMALL) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(TileIcons["steps"], contentDescription = "walking", tint = color, modifier = Modifier.size(20.dp))
            Text(clockDuration(elapsed), color = color, fontSize = 11.sp, maxLines = 1)
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            FaceText(if (walk.paused) "walk paused" else "walking", 12, dim = true)
            Text(clockDuration(elapsed), color = color, fontSize = if (kind == HealthTileKind.MEDIUM) 30.sp else 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraLight, maxLines = 1)
            FaceText(String.format(Locale.US, "%,d steps", walk.steps), 13)
            FaceText(Units.distance(km) + if (walk.hasBarometer) " · ${Units.elevation(walk.ascentM)} up" else "", 13)
        }
        if (kind == HealthTileKind.MEDIUM) {
            RoundButton(if (walk.paused) "play" else "pause", false, color, onPause, Modifier.align(Alignment.BottomStart))
            RoundButton("stop", true, WalkGreen, onStop, Modifier.align(Alignment.BottomEnd))
        } else {
            LabelButton(if (walk.paused) "resume" else "pause", false, onPause, Modifier.align(Alignment.BottomStart))
            LabelButton("stop", true, onStop, Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun WalkConfirmFace(kind: HealthTileKind, onSave: () -> Unit, onKeep: () -> Unit) {
    if (kind == HealthTileKind.SMALL) return
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        FaceText("stop this walk?", 18, Modifier.align(Alignment.TopStart))
        Row(modifier = Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelButton("save", true, onSave)
            LabelButton("keep going", false, onKeep)
        }
    }
}

@Composable
private fun WalkSavedFace(w: Walk, km: Double) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
        FaceText("walk saved", 12, dim = true)
        FaceText(Units.distance(km), 26, weight = androidx.compose.ui.text.font.FontWeight.ExtraLight)
        FaceText(walkTimeFloors(w), 13)
        FaceText(plusSteps(w.steps), 13, dim = true)
    }
}

@Composable
private fun SleepBack(est: SleepEstimate) {
    val color = LocalTileFaceColor.current
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Column(modifier = Modifier.align(Alignment.TopStart)) {
            FaceText("last night", 12, dim = true)
            FaceText(hm(est.durationMs), 26, weight = androidx.compose.ui.text.font.FontWeight.ExtraLight)
            FaceText("${clockLabel(est.start)} – ${clockLabel(est.end)}", 12)
        }
        val day = LocalDate.now().toEpochDay()
        Canvas(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp)) {
            drawRect(color.copy(alpha = 0.3f), Offset.Zero, size)
            val a = timelineFraction(est.start, day) * size.width
            val b = timelineFraction(est.end, day) * size.width
            drawRect(color, Offset(a, 0f), Size((b - a).coerceAtLeast(2f), size.height))
        }
    }
}

@Composable
private fun AirBack(a: AirSnapshot, place: String, nowMs: Long) {
    val best = bestWalkHour(a.hours, nowMs)
    val zone = java.time.ZoneOffset.ofTotalSeconds(a.utcOffsetSeconds)
    Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
        FaceText("air" + if (place.isNotBlank()) " · ${place.lowercase()}" else "", 12, dim = true)
        a.usAqi?.let { FaceText(airBandShort(it), 20, weight = androidx.compose.ui.text.font.FontWeight.ExtraLight) }
        a.uvNow?.let { FaceText("uv ${it.toInt()} ${uvBand(it)}", 13) }
        best?.let { FaceText("walk after ${hourLabel(java.time.Instant.ofEpochMilli(it.timeMs).atZone(zone).hour)}", 12, dim = true) }
    }
}

@Composable
private fun LastWalkBack(w: Walk, s: HealthSettings, history: List<DaySteps>, today: Long, liveSteps: Int?) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
        FaceText("last walk · ${clockLabel(w.startMs)}", 12, dim = true)
        FaceText(Units.distance(walkKm(w, s.heightCm)), 26, weight = androidx.compose.ui.text.font.FontWeight.ExtraLight)
        FaceText(walkTimeFloors(w), 13)
        daysAtGoalLabel(history, today, liveSteps, s.goal)?.let { FaceText(it, 12, dim = true) }
    }
}

@Composable
private fun HealthRingFront(steps: Int, goal: Int, size: TileSize) {
    val color = LocalTileFaceColor.current
    val progress = goalProgress(steps, goal)
    val number = String.format(Locale.US, "%,d", steps)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Ring(color, progress, Modifier.size(46.dp))
        Text(if (steps >= 10_000) "${steps / 1000}k" else number, color = color, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun HealthGlyphFront(size: TileSize) {
    val color = LocalTileFaceColor.current
    Box(modifier = Modifier.fillMaxSize()) {
        Icon(TileIcons["healthhub"], contentDescription = null, tint = color, modifier = Modifier.align(Alignment.Center).size(if (size == TileSize.SMALL) 28.dp else 44.dp))
        if (size != TileSize.SMALL) Text("health", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
}

@Composable
private fun Ring(color: androidx.compose.ui.graphics.Color, progress: Float, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val stroke = this.size.minDimension * 0.09f
        val inset = stroke / 2
        val arc = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(color.copy(alpha = 0.3f), 0f, 360f, false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
        if (progress > 0f) drawArc(color, -90f, 360f * progress, false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun HealthAppsBack(apps: List<HealthApp>, size: TileSize, page: Int) {
    val context = LocalContext.current
    val color = LocalTileFaceColor.current
    val columns = size.cols.coerceAtLeast(1).let { if (it == 2) 3 else it }
    val rows = size.rows.coerceAtLeast(1)
    val perPage = columns * rows
    val pages = ((apps.size + perPage - 1) / perPage).coerceAtLeast(1)
    val shown = apps.drop((page % pages) * perPage).take(perPage)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 18.dp)) {
            shown.chunked(columns).forEach { rowApps ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    rowApps.forEach { app ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openApp(context, app.packageName) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val icon = rememberMonochromeAppIcon(app.packageName, sizePx = iconPx(26.dp))
                            if (icon != null) Image(icon, app.label, colorFilter = ColorFilter.tint(color), modifier = Modifier.size(26.dp))
                        }
                    }
                    repeat(columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Text(
            if (pages > 1) "fit · ${(page % pages) + 1}/$pages" else "fit",
            color = color, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp),
        )
    }
}

