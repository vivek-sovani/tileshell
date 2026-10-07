package com.tileshell.feature.livetiles.health

import android.Manifest
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

/**
 * The health tile (it replaces the steps widget). Front: a ring toward the daily goal with today's steps (just the
 * count on a small tile). Back: your health apps as tappable icons (the ones marked on the hub's apps page, else all;
 * a tap elsewhere opens the hub). With more apps than fit, each turn to the back shows the next page. Flips on its own
 * while [active], only when there are apps to show.
 */
@Composable
fun HealthTileFace(size: TileSize, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HealthStore.ensureLoaded(context) }
    val settings by HealthPrefs.settings(context).collectAsStateWithLifecycle()
    val history by HealthStore.history.collectAsStateWithLifecycle()
    val granted = rememberPermissionGranted(Manifest.permission.ACTIVITY_RECOGNITION)
    LaunchedEffect(granted) { if (granted) HealthSampleWorker.ensureScheduled(context) }
    val live = if (granted) rememberStepsToday(active) else null
    val steps = live ?: if (granted) stepsOn(history, LocalDate.now().toEpochDay()).takeIf { it > 0 } else null
    val marks by HealthTileMarks.marks(context).collectAsStateWithLifecycle()
    val apps = healthAppsOnTile(rememberHealthApps().orEmpty().distinctBy { it.packageName }, marks.orEmpty())

    var flipped by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    val canFlip = apps.isNotEmpty() && size != TileSize.SMALL
    LaunchedEffect(active, canFlip) {
        if (!active || !canFlip) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            delay(HEALTH_FLIP_MS)
            flipped = !flipped
            if (flipped) page++
        }
    }
    val goal = (settings ?: HealthSettings()).goal
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { if (steps == null) HealthGlyphFront(size) else HealthRingFront(steps, goal, size) },
        back = { HealthAppsBack(apps, size, page) },
    )
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
private fun HealthRingFront(steps: Int, goal: Int, size: TileSize) {
    val color = LocalTileFaceColor.current
    val progress = goalProgress(steps, goal)
    val number = String.format(Locale.US, "%,d", steps)
    if (size == TileSize.SMALL) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Ring(color, progress, Modifier.size(46.dp))
            Text(if (steps >= 10_000) "${steps / 1000}k" else number, color = color, fontSize = 11.sp, maxLines = 1)
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ring = if (size.rows >= 2 && size.cols >= 2) 84.dp else 56.dp
            Box(contentAlignment = Alignment.Center) {
                Ring(color, progress, Modifier.size(ring))
                Text(number, color = color, fontSize = if (ring >= 80.dp) 15.sp else 11.sp, maxLines = 1)
            }
            if (size.cols >= 4) {
                Column {
                    Text("${(progress * 100).toInt()}% of goal", color = color, fontSize = 14.sp)
                    Text(String.format(Locale.US, "of %,d steps", goal), color = color.copy(alpha = 0.8f), fontSize = 11.sp)
                }
            }
        }
        Text("health", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart))
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
