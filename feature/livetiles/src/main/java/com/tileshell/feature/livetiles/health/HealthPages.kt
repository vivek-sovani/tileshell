package com.tileshell.feature.livetiles.health

import android.Manifest
import android.app.AlarmManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.ColorTokens
import com.tileshell.feature.livetiles.BatteryLog
import com.tileshell.feature.livetiles.Units
import com.tileshell.feature.livetiles.UsageAccess
import com.tileshell.feature.livetiles.alarmCaption
import com.tileshell.feature.livetiles.alarmSourceFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val Green = Color(0xFF7CB518)

@Composable
internal fun HRow(label: String, value: String, tokens: ColorTokens, onClick: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = tokens.fg, fontSize = 15.sp, fontWeight = FontWeight.Light)
            Text(value, color = tokens.fgDim, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

private fun LazyListScope.rows(items: List<Pair<String, String>>, tokens: ColorTokens) {
    items.forEach { (l, v) -> item(key = "row-$l") { HRow(l, v, tokens) } }
}

@Composable
internal fun Caption(text: String, tokens: ColorTokens, modifier: Modifier = Modifier) {
    Text(text, color = tokens.fgDim, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
private fun FlatButton(label: String, filled: Boolean, accent: Color, tokens: ColorTokens, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .then(if (filled) Modifier.background(accent) else Modifier.border(1.5.dp, tokens.fg))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) Color.White else tokens.fg, fontSize = 16.sp, fontWeight = FontWeight.Light)
    }
}

// --- steps by hour (today) --------------------------------------------------------

@Composable
fun StepsByHour(hours: List<Int>, walkHours: Set<Int>, currentHour: Int?, tokens: ColorTokens, accent: Color) {
    val top = (hours.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Caption("STEPS BY HOUR", tokens)
        Row(modifier = Modifier.fillMaxWidth().height(90.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            hours.forEachIndexed { h, v ->
                val frac = v.toFloat() / top
                val color = when {
                    h == currentHour -> accent
                    h in walkHours -> Green
                    currentHour != null && h > currentHour -> Color.Transparent
                    else -> tokens.fgDim.copy(alpha = 0.45f)
                }
                Box(modifier = Modifier.weight(1f).height((82 * frac).coerceAtLeast(if (currentHour == null || h <= currentHour) 2f else 0f).dp).background(color))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12a", "6a", "12p", "6p", "12a").forEach { Text(it, color = tokens.fgDim, fontSize = 10.sp) }
        }
    }
}

// --- walk -------------------------------------------------------------------------

@Composable
fun WalkPage(granted: Boolean, settings: HealthSettings, tokens: ColorTokens, accent: Color, accessLine: @Composable () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WalkSession.loadWalks(context) }
    val state by WalkSession.state.collectAsStateWithLifecycle()
    val walks by WalkSession.walks.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.running, state.paused) {
        while (state.running) {
            elapsed = WalkSession.elapsedMs()
            if (state.paused) break
            delay(1000)
        }
        if (!state.running) elapsed = 0L
    }
    val hasBaro = remember { WalkSession.hasBarometer(context) }
    val km = state.meters / 1000.0
    var selected by remember { mutableStateOf<Long?>(null) }
    BackHandler(enabled = selected != null) { selected = null }
    val startWalk = rememberWalkStarter(settings)
    val picked = selected?.let { id -> walks.firstOrNull { it.startMs == id } }
    if (picked != null) {
        WalkDetail(picked, settings, tokens, accent, onBack = { selected = null }, onDeleted = { selected = null })
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (!granted) item(key = "access") { accessLine() }
        if (!state.running) {
            item(key = "idle") {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                    Text("0:00", color = tokens.fgDim, fontSize = 56.sp, fontWeight = FontWeight.ExtraLight)
                    Text(
                        "counts steps, distance${if (hasBaro) ", elevation gain" else ""} and pace, records your route with GPS, beeps at each ${if (Units.isImperial) "mile" else "kilometre"}, and keeps going with the screen off",
                        color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp),
                    )
                    FlatButton("start walk", true, accent, tokens, Modifier.fillMaxWidth()) {
                        if (!granted) return@FlatButton
                        startWalk()
                    }
                }
            }
        } else {
            item(key = "timer") {
                Column(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                    Text(clockDuration(elapsed), color = tokens.fg, fontSize = 64.sp, fontWeight = FontWeight.ExtraLight)
                    Text(if (state.paused) "paused" else "walking", color = if (state.paused) tokens.fgDim else Green, fontSize = 13.sp)
                }
            }
            val pace = paceText(elapsed, km, Units.isImperial)
            val spm = stepsPerMinute(state.steps, elapsed)
            rows(
                buildList {
                    add("steps" to "%,d".format(Locale.US, state.steps))
                    add("distance" to Units.distance(km))
                    if (state.hasBarometer) add("elevation gain" to Units.elevation(state.ascentM))
                    add("calories (estimate)" to "${walkKcal(elapsed, km, state.ascentM, settings.weightKg)} kcal")
                    add("pace" to (pace ?: "—"))
                    if (settings.recordRoute && WalkSession.canRoute(context)) add("route" to if (state.gps) "recording" else "waiting for gps")
                    add("steps a minute" to (spm?.toString() ?: "—"))
                },
                tokens,
            )
            item(key = "buttons") {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlatButton(if (state.paused) "resume" else "pause", false, accent, tokens, Modifier.weight(1f)) {
                        if (state.paused) WalkSession.resume(context) else WalkSession.pause(context)
                    }
                    FlatButton("stop", true, accent, tokens, Modifier.weight(1f)) { WalkSession.stop(context) }
                }
            }
        }
        item(key = "recent") { Caption("RECENT WALKS", tokens, Modifier.padding(top = 22.dp, bottom = 6.dp)) }
        if (walks.isEmpty()) {
            item(key = "none") { Text("no walks yet", color = tokens.fgDim, fontSize = 14.sp) }
        } else {
            walksSection(walks, settings, tokens, accent) { selected = it }
        }
        item(key = "note") {
            Text(
                "steps come from the phone's step sensor. elevation gain uses the barometer (only while you are stepping, small wobbles ignored)${if (hasBaro) "" else ", which this phone does not have, so elevation is not counted"}. the route uses GPS while a walk runs, if location is allowed; distance then comes from it, else from your steps. walks and routes stay on this phone.",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * Starts a walk from anywhere (the page, the tile): asks for the notification permission (Android 13+) and, while the
 * route is on and location is not allowed, for location once; the walk starts whatever the answer.
 */
@Composable
fun rememberWalkStarter(settings: HealthSettings): () -> Unit {
    val context = LocalContext.current
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val startNow: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        WalkSession.start(context)
    }
    val locationAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startNow() }
    val recordRoute = rememberUpdatedState(settings.recordRoute)
    return {
        if (recordRoute.value && !WalkSession.canRoute(context) && !HealthPrefs.routeAsked(context)) {
            HealthPrefs.setRouteAsked(context, true)
            locationAsk.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            if (recordRoute.value && !WalkSession.canRoute(context)) {
                android.widget.Toast.makeText(context, "no route will be recorded: precise location is off (health settings › location access)", android.widget.Toast.LENGTH_LONG).show()
            }
            startNow()
        }
    }
}

/** What the walk route can use: precise location (needed), only approximate, or nothing. */
enum class LocationState { PRECISE, APPROXIMATE, NONE }

fun locationState(context: Context): LocationState {
    fun granted(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    return when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationState.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationState.APPROXIMATE
        else -> LocationState.NONE
    }
}

/**
 * State of the location permission for the settings row (re-read when the app is shown again, e.g. back from the
 * system settings) and the action for it: ask, or, when the system will not ask again, explain and open the settings.
 * [instruction] is non-null while the explanation should be shown; call [dismiss] to clear it.
 */
class LocationAsk(val state: LocationState, val act: () -> Unit, val instruction: String?, val proceed: () -> Unit, val dismiss: () -> Unit)

@Composable
fun rememberLocationAsk(): LocationAsk {
    val context = LocalContext.current
    var state by remember { mutableStateOf(locationState(context)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state = locationState(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { state = locationState(context) }
    var instruction by remember { mutableStateOf<String?>(null) }
    val openSettings: () -> Unit = {
        runCatching {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    val act: () -> Unit = {
        val activity = context as? android.app.Activity
        val canAsk = state == LocationState.NONE && (!HealthPrefs.routeAsked(context) ||
            (activity != null && androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)))
        if (canAsk) {
            HealthPrefs.setRouteAsked(context, true)
            ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            instruction = if (state == LocationState.APPROXIMATE) {
                "Only approximate location is allowed, which is not enough to draw a route. On the next screen open Permissions, then Location, choose \"Allow only while using the app\" and turn on \"Use precise location\"."
            } else {
                "Location was turned off for tileshell. On the next screen open Permissions, then Location, choose \"Allow only while using the app\" and turn on \"Use precise location\"."
            }
        }
    }
    return LocationAsk(state, act, instruction, proceed = { instruction = null; openSettings() }, dismiss = { instruction = null })
}

/** One tappable row per walk (when, time, distance, elevation gain), for the walk, week and month pages. */
internal fun LazyListScope.walksSection(walks: List<Walk>, settings: HealthSettings, tokens: ColorTokens, accent: Color, onOpen: (Long) -> Unit) {
    walks.forEach { w ->
        item(key = "walk-${w.startMs}") {
            val parts = buildList {
                add(spokenDuration(w.activeMs))
                add(Units.distance(walkKm(w, settings.heightCm)))
                if (walkAscentM(w) > 0) add("${Units.elevation(walkAscentM(w))} up")
            }
            Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(w.startMs) }) {
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
                Row(modifier = Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(walkWhenLabel(w.startMs), color = tokens.fg, fontSize = 15.sp, fontWeight = FontWeight.Light)
                        Text(parts.joinToString(" · "), color = tokens.fgDim, fontSize = 13.sp)
                    }
                    Text(if (w.hasRoute) "route ›" else "›", color = accent, fontSize = 13.sp)
                }
            }
        }
    }
}

/** The route over map tiles (when [useTiles]); tiles arrive one by one, a few at a time, and are cached. */
@Composable
internal fun RouteMap(pts: List<RoutePoint>, intervalM: Double, useTiles: Boolean, accent: Color, backdrop: Color, modifier: Modifier) {
    val context = LocalContext.current
    val marks = remember(pts, intervalM) { routeMarks(pts, intervalM).map { m -> pts.indexOfFirst { it === m } } }
    var tiles by remember(pts, useTiles) { mutableStateOf<Map<TileKey, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }
    var boxSize by remember { mutableStateOf(Size.Zero) }
    val fit = remember(pts, boxSize) { if (boxSize.width > 0f) fitMap(pts, boxSize.width, boxSize.height, minOf(boxSize.width, boxSize.height) * 0.1f) else null }
    LaunchedEffect(fit, useTiles) {
        val f = fit ?: return@LaunchedEffect
        if (!useTiles) return@LaunchedEffect
        val gate = kotlinx.coroutines.sync.Semaphore(6)
        kotlinx.coroutines.coroutineScope {
            f.tiles().take(60).forEach { k ->
                launch(Dispatchers.IO) {
                    gate.acquire()
                    val bmp = try { MapTiles.get(context, k) } finally { gate.release() }
                    if (bmp != null) tiles = tiles + (k to bmp.asImageBitmap())
                }
            }
        }
    }
    Canvas(modifier = modifier.background(backdrop)) {
        if (boxSize != size) boxSize = size
        val f = fit ?: return@Canvas
        tiles.forEach { (k, img) ->
            val (l, t, side) = f.place(k)
            drawImage(img, srcOffset = androidx.compose.ui.unit.IntOffset.Zero, srcSize = androidx.compose.ui.unit.IntSize(img.width, img.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(l.roundToInt(), t.roundToInt()), dstSize = androidx.compose.ui.unit.IntSize(side.roundToInt() + 1, side.roundToInt() + 1))
        }
        val xy = pts.map { f.project(it.lat, it.lon) }
        val path = Path()
        xy.forEachIndexed { i, (x, y) -> if (i == 0 || pts[i].newSegment) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path, Color.White, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, accent, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        marks.filter { it >= 0 }.forEach { i -> drawCircle(Color.White, 6.dp.toPx(), Offset(xy[i].first, xy[i].second)); drawCircle(accent, 3.5.dp.toPx(), Offset(xy[i].first, xy[i].second)) }
        drawCircle(Color.White, 9.dp.toPx(), Offset(xy.first().first, xy.first().second))
        drawCircle(Green, 7.dp.toPx(), Offset(xy.first().first, xy.first().second))
        drawCircle(Color.White, 9.dp.toPx(), Offset(xy.last().first, xy.last().second))
        drawCircle(Color(0xFFD6262B), 7.dp.toPx(), Offset(xy.last().first, xy.last().second))
    }
}

/** The route map over the whole screen: pinch to zoom, drag to move, back or "close" to leave. */
@Composable
internal fun RouteMapFullScreen(pts: List<RoutePoint>, intervalM: Double, useTiles: Boolean, accent: Color, tokens: ColorTokens, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        Box(modifier = Modifier.fillMaxSize().background(tokens.bg)) {
            RouteMap(
                pts, intervalM, useTiles, accent, tokens.sheetLine.copy(alpha = 0.25f),
                Modifier.fillMaxSize()
                    .onSizeChanged { box = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            val maxX = box.width * (scale - 1f) / 2f
                            val maxY = box.height * (scale - 1f) / 2f
                            offset = Offset((offset.x + pan.x * scale).coerceIn(-maxX, maxX), (offset.y + pan.y * scale).coerceIn(-maxY, maxY))
                        }
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            )
            Text(
                "‹ close", color = accent, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)
                    .background(tokens.bg.copy(alpha = 0.7f)).clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                "map © OpenStreetMap contributors", color = tokens.fgDim, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(8.dp).background(tokens.bg.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun WalkDetail(w: Walk, settings: HealthSettings, tokens: ColorTokens, accent: Color, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    var route by remember(w.startMs) { mutableStateOf<List<RoutePoint>?>(null) }
    LaunchedEffect(w.startMs) {
        route = if (w.hasRoute) withContext(Dispatchers.IO) { WalkSession.loadRoute(context, w.startMs) } else emptyList()
    }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("delete this walk?") },
            text = { Text("its time, distance and route are removed from this phone.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false; WalkSession.deleteWalk(context, w.startMs); onDeleted() }) { Text("delete") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("cancel") } },
        )
    }
    val imperial = Units.isImperial
    val km = walkKm(w, settings.heightCm)
    val interval = if (imperial) 1609.344 else 1000.0
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "back") { Text("‹ walks", color = accent, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp)) }
        item(key = "head") {
            Column {
                Caption(walkWhenLabel(w.startMs).uppercase(), tokens)
                Text(Units.distance(km), color = tokens.fg, fontSize = 56.sp, fontWeight = FontWeight.ExtraLight)
                Text("${clockDuration(w.activeMs)} walking", color = tokens.fgDim, fontSize = 14.sp)
            }
        }
        item(key = "route") {
            val pts = route
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Caption("ROUTE", tokens)
                if (pts == null) {
                    Text("…", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                } else if (pts.size < 2) {
                    Text(
                        if (w.hasRoute) "the route could not be read" else "no route was recorded: location was off or not allowed for this walk",
                        color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    var full by remember { mutableStateOf(false) }
                    if (full) RouteMapFullScreen(pts, interval, settings.mapTiles, accent, tokens) { full = false }
                    RouteMap(
                        pts, interval, settings.mapTiles, accent, tokens.sheetLine.copy(alpha = 0.25f),
                        Modifier.fillMaxWidth().padding(top = 8.dp).height(260.dp).clipToBounds().clickable { full = true },
                    )
                    Text("tap the map to see it full screen. green start · red end · dots at each ${if (imperial) "mile" else "kilometre"}.${if (settings.mapTiles) " map © OpenStreetMap contributors." else " map background is off in settings."}", color = tokens.fgDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item(key = "gap") { Box(modifier = Modifier.height(12.dp)) }
        val spm = stepsPerMinute(w.steps, w.activeMs)
        rows(
            buildList {
                add("time walking" to clockDuration(w.activeMs))
                add("average pace" to (paceText(w.activeMs, km, imperial) ?: "—"))
                add("steps" to "%,d".format(Locale.US, w.steps))
                add("steps a minute" to (spm?.toString() ?: "—"))
                if (walkAscentM(w) > 0) add("elevation gain" to Units.elevation(walkAscentM(w)))
                add("calories (estimate)" to "${walkKcal(w.activeMs, km, walkAscentM(w), settings.weightKg)} kcal")
                add("started" to clockLabel(w.startMs))
                add("ended" to clockLabel(w.endMs))
                add("distance from" to if (w.hasRoute && w.meters > 0) "gps" else "step estimate")
            },
            tokens,
        )
        val times = splitTimes(w.splits)
        if (times.isNotEmpty()) {
            item(key = "splits") { Caption(if (imperial) "MILE TIMES" else "KILOMETRE TIMES", tokens, Modifier.padding(top = 18.dp, bottom = 4.dp)) }
            item(key = "split-bars") {
                val fastest = times.min()
                val slowest = times.max()
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    times.forEachIndexed { i, t ->
                        val frac = (t.toFloat() / slowest).coerceIn(0.15f, 1f)
                        val best = times.size > 1 && t == fastest
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                            Box(modifier = Modifier.weight(1f).height(20.dp).background(tokens.sheetLine.copy(alpha = 0.3f))) {
                                Box(modifier = Modifier.fillMaxWidth(frac).height(20.dp).background(if (best) Green else accent.copy(alpha = 0.7f)))
                            }
                            Text(clockDuration(t), color = tokens.fg, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
                        }
                    }
                    Text("longer bar = slower ${if (imperial) "mile" else "kilometre"}${if (times.size > 1) ", green is the fastest" else ""}.", color = tokens.fgDim, fontSize = 11.sp)
                }
            }
        }
        item(key = "delete") {
            Text("delete this walk", color = Color(0xFFE5645A), fontSize = 15.sp, modifier = Modifier.clickable { confirmDelete = true }.padding(top = 20.dp, bottom = 12.dp))
        }
    }
}

// --- sleep ------------------------------------------------------------------------

private data class SleepView(val last: SleepEstimate?, val nights: List<SleepEstimate?>, val monday: Long, val today: Long)

@Composable
fun SleepPage(settings: HealthSettings, tokens: ColorTokens, accent: Color, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { BatteryLog.ensureStarted(context) }
    val samples by BatteryLog.samples.collectAsStateWithLifecycle()
    val usageOn = remember { UsageAccess.isGranted(context) }
    var view by remember { mutableStateOf<SleepView?>(null) }
    LaunchedEffect(samples.size) {
        view = withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toEpochDay()
            val monday = weekStart(today)
            val use = screenOnSpans(samples, now) + if (usageOn) usageMoments(resumeTimes(context, now - 8L * 86_400_000L, now)) else emptyList()
            SleepView(nightEstimate(use, today, now), weekNights(use, monday, today, now), monday, today)
        }
    }
    val v = view
    val alarm = remember { runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock }.getOrNull() }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "head") {
            val est = v?.last
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Caption("LAST NIGHT · ESTIMATE", tokens)
                Text(est?.let { sleepDurationLabel(it.durationMs) } ?: if (v == null) "…" else "—", color = tokens.fg, fontSize = 48.sp, fontWeight = FontWeight.ExtraLight)
                Text(
                    est?.let { "${clockLabel(it.start)} → ${clockLabel(it.end)} · ${pickupLabel(it.pickups.size)}" }
                        ?: if (v == null) "reading the log" else "no night found yet: the phone was in use, or tileshell has not been watching long",
                    color = tokens.fgDim, fontSize = 13.sp,
                )
            }
        }
        if (v?.last != null) item(key = "timeline") {
            val est = v.last
            Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                    drawRect(tokens.sheetLine, Offset.Zero, size)
                    val a = timelineFraction(est.start, v.today) * size.width
                    val b = timelineFraction(est.end, v.today) * size.width
                    drawRect(accent.copy(alpha = 0.85f), Offset(a, 0f), Size((b - a).coerceAtLeast(2f), size.height))
                    est.pickups.forEach { p ->
                        val x = timelineFraction(p.start, v.today) * size.width
                        val w = (timelineFraction(p.end, v.today) * size.width - x).coerceAtLeast(2.dp.toPx())
                        drawRect(tokens.bg, Offset(x, 0f), Size(w, size.height))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("10p", "12a", "2a", "4a", "6a", "8a").forEach { Text(it, color = tokens.fgDim, fontSize = 10.sp) }
                }
            }
        }
        if (v != null) {
            item(key = "week") {
                val top = 10 * 3_600_000L
                Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Caption("THIS WEEK", tokens)
                    Row(modifier = Modifier.fillMaxWidth().height(90.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        v.nights.forEachIndexed { i, n ->
                            val frac = ((n?.durationMs ?: 0L).toFloat() / top).coerceIn(0f, 1f)
                            val color = when {
                                n == null -> Color.Transparent
                                v.monday + i == v.today -> accent
                                else -> tokens.fgDim.copy(alpha = 0.45f)
                            }
                            Box(modifier = Modifier.weight(1f).height((82 * frac).coerceAtLeast(if (n != null) 2f else 0f).dp).background(color))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until 7) Text(
                            LocalDate.ofEpochDay(v.monday + i).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH).lowercase(),
                            color = tokens.fgDim, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            val avg = averageSleep(v.nights)
            item(key = "avg") { Column(modifier = Modifier.padding(top = 10.dp)) { HRow("average this week", avg?.let(::sleepDurationLabel) ?: "—", tokens) } }
        }
        item(key = "alarm") {
            val label = alarm?.let { info ->
                val src = alarmSourceFor(context, info)
                val cap = alarmCaption("", src?.appLabel.orEmpty(), src?.isClockApp == true)
                "${clockLabel(info.triggerTime)} · $cap"
            } ?: "none set"
            HRow("next alarm", label, tokens)
        }
        item(key = "bedtime") {
            HRow("bedtime reminder", if (settings.bedtimeMinute < 0) "off ›" else "${clockLabel(bedtimeMillis(settings.bedtimeMinute))} ›", tokens, onOpenSettings)
        }
        item(key = "note") {
            Text(
                "an estimate, not a measurement: the longest stretch between 8 pm and noon when the phone's screen was off. a quick look at the phone counts as a pick-up; longer use ends the stretch. read from tileshell's own battery log${if (usageOn) " and app use" else "; usage access would add app use"}. it stays on this phone.",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun bedtimeMillis(minuteOfDay: Int): Long = nextOccurrence(minuteOfDay, System.currentTimeMillis())

// --- air --------------------------------------------------------------------------

@Composable
fun AirPage(visible: Boolean, refreshKey: Int, tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    val ui by AirRepository.ui.collectAsStateWithLifecycle()
    // Fetched when the page is shown (cached; not before the weather rate has passed), and when refresh is pressed.
    LaunchedEffect(visible, refreshKey) { if (visible) AirRepository.load(context, force = refreshKey > 0) }
    val s = ui.snapshot
    val now = System.currentTimeMillis()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (s == null) {
            item(key = "empty") {
                Text(ui.problem ?: if (ui.loading) "reading air quality…" else "…", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            }
            return@LazyColumn
        }
        val aqi = s.usAqi
        item(key = "aqi") {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Caption("AIR QUALITY · US AQI", tokens)
                Text(aqi?.toString() ?: "—", color = tokens.fg, fontSize = 64.sp, fontWeight = FontWeight.ExtraLight)
                if (aqi != null) {
                    val band = aqiBand(aqi)
                    Text(band.label, color = Color(band.argb), fontSize = 15.sp)
                    AqiStrip(aqi)
                }
            }
        }
        fun f(v: Double?, unit: String = "µg/m³") = v?.let { "${it.toInt()} $unit" } ?: "—"
        rows(
            listOf(
                "pm2.5" to f(s.pm25),
                "pm10" to f(s.pm10),
                "ozone · no₂" to "${s.ozone?.toInt() ?: "—"} · ${s.no2?.toInt() ?: "—"} µg/m³",
            ),
            tokens,
        )
        item(key = "uv") {
            val peak = uvPeak(s.hours)
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Caption("UV INDEX", tokens)
                Text(
                    s.uvNow?.let { "%.0f · %s".format(Locale.US, it, uvBand(it)) } ?: "—",
                    color = tokens.fg, fontSize = 32.sp, fontWeight = FontWeight.ExtraLight,
                )
                if (peak != null) Text("today's peak ${"%.0f".format(Locale.US, peak.uv)} · ${uvBand(peak.uv!!)} at ${clockLabel(peak.timeMs, java.time.ZoneOffset.ofTotalSeconds(s.utcOffsetSeconds))}", color = tokens.fgDim, fontSize = 13.sp)
                UvStrip(s, now, tokens)
            }
        }
        item(key = "best") {
            val best = bestWalkHour(s.hours, now)
            val zone = java.time.ZoneOffset.ofTotalSeconds(s.utcOffsetSeconds)
            Column(modifier = Modifier.padding(top = 16.dp)) {
                HRow(
                    "best time for a walk",
                    best?.let { "${hourRangeLabel(java.time.Instant.ofEpochMilli(it.timeMs).atZone(zone).hour)} · aqi ${it.aqi}" } ?: "no good hour left today",
                    tokens,
                )
            }
        }
        item(key = "note") {
            Text(
                "from open-meteo for the weather's place; only its coordinates are sent, when this page is shown or refreshed. ${ui.problem ?: ""}",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AqiStrip(aqi: Int) {
    val bands = AqiBand.entries
    val idx = bands.indexOfFirst { aqi <= it.max }.coerceAtLeast(0)
    val prev = if (idx == 0) 0 else bands[idx - 1].max
    val span = (if (bands[idx].max == Int.MAX_VALUE) 500 else bands[idx].max) - prev
    val frac = ((idx + ((aqi - prev).toFloat() / span).coerceIn(0f, 1f)) / bands.size).coerceIn(0.01f, 0.99f)
    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp).padding(top = 6.dp)) {
        val w = size.width / bands.size
        bands.forEachIndexed { i, b -> drawRect(Color(b.argb), Offset(i * w + 1f, 4.dp.toPx()), Size(w - 2f, size.height - 4.dp.toPx())) }
        drawRect(Color.White, Offset(frac * size.width - 1.5f, 0f), Size(3f, size.height))
    }
}

@Composable
private fun UvStrip(s: AirSnapshot, nowMs: Long, tokens: ColorTokens) {
    val day = s.hours.filter { it.isDay }
    if (day.isEmpty()) return
    val top = maxOf(day.maxOf { it.uv ?: 0.0 }, 6.0)
    val zone = java.time.ZoneOffset.ofTotalSeconds(s.utcOffsetSeconds)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            day.forEach { h ->
                val uv = h.uv ?: 0.0
                val color = when {
                    uv < 3 -> Green
                    uv < 6 -> Color(0xFFE2A200)
                    uv < 8 -> Color(0xFFE5641E)
                    uv < 11 -> Color(0xFFD6262B)
                    else -> Color(0xFF6B3FD4)
                }
                val isNow = nowMs in h.timeMs until h.timeMs + 3_600_000L
                Box(modifier = Modifier.weight(1f).height((56 * (uv / top).toFloat()).coerceAtLeast(2f).dp).background(if (isNow) color else color.copy(alpha = 0.6f)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            fun lbl(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).hour.let { (if (it % 12 == 0) 12 else it % 12).toString() + if (it < 12) "a" else "p" }
            Text(lbl(day.first().timeMs), color = tokens.fgDim, fontSize = 10.sp)
            Text(lbl(day.last().timeMs), color = tokens.fgDim, fontSize = 10.sp)
        }
    }
}
