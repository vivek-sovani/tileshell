package com.tileshell.feature.livetiles

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tileshell.core.data.clock.RingArc
import com.tileshell.core.data.clock.Session
import com.tileshell.core.data.clock.dialAngle
import com.tileshell.core.data.clock.dialAngles
import com.tileshell.core.data.clock.ringArcs
import com.tileshell.core.data.clock.sweepBetween
import com.tileshell.core.design.ColorTokens
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** A point at [deg] degrees clockwise from the top, [radius] from [center]. */
private fun polar(center: Offset, radius: Float, deg: Float): Offset {
    val t = Math.toRadians(deg.toDouble())
    return Offset(center.x + radius * sin(t).toFloat(), center.y - radius * cos(t).toFloat())
}

/** Draws an arc from [startDeg] clockwise for [sweepDeg] (degrees from the top) on the circle of [radius]. */
private fun DrawScope.ring(center: Offset, radius: Float, startDeg: Float, sweepDeg: Float, color: Color, width: Float, round: Boolean = false) {
    if (sweepDeg <= 0f) return
    drawArc(
        color = color,
        startAngle = startDeg - 90f,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = width, cap = if (round) StrokeCap.Round else StrokeCap.Butt),
    )
}

/**
 * An analog dial at [now] with the next alarm ([alarmAt], epoch ms, or null) as
 * an arc running clockwise from the hour hand to the alarm's mark.
 */
@Composable
fun ClockDial(now: Long, alarmAt: Long?, tokens: ColorTokens, accent: Color, modifier: Modifier = Modifier) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val angles = dialAngles(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND))
    val alarmCal = alarmAt?.let { Calendar.getInstance().apply { timeInMillis = it } }
    val alarmDeg = alarmCal?.let { dialAngle(it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f - 6.dp.toPx()
        val line = 1.5.dp.toPx()
        drawCircle(tokens.sheetLine, radius = r, center = c, style = Stroke(line))
        for (k in 0 until 12) {
            val major = k % 3 == 0
            drawLine(tokens.fgDim, polar(c, r * if (major) 0.84f else 0.91f, k * 30f), polar(c, r * 0.98f, k * 30f), strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx())
        }
        if (alarmDeg != null) {
            ring(c, r * 0.86f, angles.hour, sweepBetween(angles.hour, alarmDeg), accent, 6.dp.toPx(), round = true)
            drawCircle(accent, radius = 6.dp.toPx(), center = polar(c, r * 0.86f, alarmDeg))
        }
        drawLine(tokens.fg, c, polar(c, r * 0.5f, angles.hour), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(tokens.fg, c, polar(c, r * 0.76f, angles.minute), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(accent, polar(c, -r * 0.14f, angles.second), polar(c, r * 0.86f, angles.second), strokeWidth = 1.5.dp.toPx())
        drawCircle(tokens.fg, radius = 4.dp.toPx(), center = c)
    }
}

/** A city's own 24 hours, midnight to midnight, with the light band 6 am to 6 pm and a marker for now. */
@Composable
fun DayStrip(fraction: Float, tokens: ColorTokens, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(tokens.chip)
        drawRect(accent.copy(alpha = 0.42f), topLeft = Offset(size.width * 0.25f, 0f), size = Size(size.width * 0.5f, size.height))
        val x = size.width * fraction.coerceIn(0f, 1f)
        drawRect(tokens.fg, topLeft = Offset((x - 1.5.dp.toPx()).coerceAtLeast(0f), 0f), size = Size(3.dp.toPx(), size.height))
    }
}

/** A progress ring with [content] in the middle: [fraction] (0..1) of the way round, clockwise from the top. */
@Composable
fun ProgressRing(fraction: Float, tokens: ColorTokens, accent: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = 7.dp.toPx()
            val r = size.minDimension / 2f - w
            ring(center, r, 0f, 360f, tokens.sheetLine, w)
            ring(center, r, 0f, 360f * fraction.coerceIn(0f, 1f), accent, w, round = true)
        }
        content()
    }
}

/**
 * The set as a ring of its steps (a gap between steps, a wider one between
 * parts): finished steps filled, the running one filling as it goes, a dot at
 * the head, the rest dim. [content] sits in the middle.
 */
@Composable
fun SegmentedRing(session: Session, now: Long, tokens: ColorTokens, accent: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val arcs: List<RingArc> = ringArcs(session.steps)
    val remaining = session.remainingMs(now)
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = 12.dp.toPx()
            val r = size.minDimension / 2f - w
            var head: Float? = null
            arcs.forEachIndexed { i, arc ->
                ring(center, r, arc.startDeg, arc.sweepDeg, tokens.sheetLine, w)
                val done = when {
                    i < session.index -> 1f
                    i == session.index -> 1f - remaining.toFloat() / session.steps[i].ms
                    else -> 0f
                }.coerceIn(0f, 1f)
                ring(center, r, arc.startDeg, arc.sweepDeg * done, accent, w)
                if (i == session.index) head = arc.startDeg + arc.sweepDeg * done
            }
            head?.let { drawCircle(tokens.fg, radius = 8.dp.toPx(), center = polar(center, r, it)) }
        }
        content()
    }
}

/** A stopwatch ring: one turn a minute, the current minute filled, a mark at each lap. */
@Composable
fun StopwatchRing(elapsedMs: Long, lapMarks: List<Long>, tokens: ColorTokens, accent: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = 7.dp.toPx()
            val r = size.minDimension / 2f - w
            ring(center, r, 0f, 360f, tokens.sheetLine, w)
            lapMarks.forEach { at ->
                val deg = (at % 60_000L) / 60_000f * 360f
                drawLine(tokens.fgDim, polar(center, r - w, deg), polar(center, r + w, deg), strokeWidth = 2.dp.toPx())
            }
            val deg = (elapsedMs % 60_000L) / 60_000f * 360f
            ring(center, r, 0f, deg, accent, w, round = true)
            drawCircle(tokens.fg, radius = 6.dp.toPx(), center = polar(center, r, deg))
        }
        content()
    }
}

/**
 * While [keepOn] the screen stays on, and while also [dim] it is dropped to
 * the lowest brightness: a timer set that runs in the foreground then needs no
 * service and no sleeping screen to keep its steps exact. Everything is put
 * back when this leaves the composition or the flags change.
 */
@Composable
fun KeepScreenOn(keepOn: Boolean, dim: Boolean) {
    val context = LocalContext.current
    val currentDim by rememberUpdatedState(dim)
    DisposableEffect(keepOn, dim) {
        val window = context.findActivity()?.window
        if (window != null && keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (currentDim) {
                window.attributes = window.attributes.also { it.screenBrightness = DIM_BRIGHTNESS }
            }
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }
}

/** The lowest brightness that still lets the screen be read: nearly off. */
private const val DIM_BRIGHTNESS = 0.02f
