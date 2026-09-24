package com.tileshell.feature.livetiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Same warm amber as [WeatherConditionVisual]'s sun. */
private val SunGlyphColor = Color(0xFFFFB347)

/**
 * Today's ("6:23 am", "6:29 pm") sunrise/sunset in the forecast place's own
 * local time, or null when the snapshot has no sun times yet (a cache file
 * from before they were fetched). Pure.
 */
fun todaySunTimes(snapshot: WeatherSnapshot): Pair<String, String>? {
    val today = snapshot.forecast.firstOrNull() ?: return null
    val rise = today.sunriseMillis ?: return null
    val set = today.sunsetMillis ?: return null
    return sunTimeLabel(rise, snapshot.utcOffsetSeconds) to sunTimeLabel(set, snapshot.utcOffsetSeconds)
}

/**
 * A compact "↑ 6:23 am   ↓ 6:29 pm" row (user-requested sunrise/sunset on the
 * weather tile and glance card), each time led by a small drawn glyph — an
 * amber half-sun on a horizon line with an up (rise) or down (set) arrow —
 * instead of spelled-out labels that don't fit a medium tile or half-width
 * card. Renders nothing when [todaySunTimes] is null.
 */
@Composable
fun SunTimesRow(
    snapshot: WeatherSnapshot,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    glyphSize: Dp = 14.dp,
) {
    val (rise, set) = todaySunTimes(snapshot) ?: return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        SunHorizonGlyph(rising = true, color = color, modifier = Modifier.size(glyphSize))
        Spacer(Modifier.width(3.dp))
        Text(rise, color = color, fontSize = fontSize, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(10.dp))
        SunHorizonGlyph(rising = false, color = color, modifier = Modifier.size(glyphSize))
        Spacer(Modifier.width(3.dp))
        Text(set, color = color, fontSize = fontSize, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SunHorizonGlyph(rising: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val stroke = s / 10f
        val horizonY = s * 0.78f
        val r = s * 0.26f
        // Half-disc sitting on the horizon.
        drawArc(
            color = SunGlyphColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(s / 2f - r, horizonY - r),
            size = Size(r * 2f, r * 2f),
        )
        drawLine(color, Offset(s * 0.06f, horizonY), Offset(s * 0.94f, horizonY), strokeWidth = stroke, cap = StrokeCap.Round)
        // Arrow above the disc: tip up for sunrise, down for sunset.
        val cx = s / 2f
        val top = s * 0.04f
        val bottom = horizonY - r - s * 0.08f
        val tipY = if (rising) top else bottom
        val baseY = if (rising) bottom else top
        val wing = s * 0.14f
        val wingY = if (rising) tipY + wing else tipY - wing
        drawLine(color, Offset(cx, baseY), Offset(cx, tipY), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - wing, wingY), Offset(cx, tipY), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + wing, wingY), Offset(cx, tipY), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
