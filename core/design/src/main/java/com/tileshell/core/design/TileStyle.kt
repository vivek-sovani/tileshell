package com.tileshell.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val LocalTileCornerRadius = staticCompositionLocalOf { 0f }
val LocalTileGradient = staticCompositionLocalOf { false }
val LocalTileFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

// Static-weight files (not variable fonts). Variable fonts apply the requested
// FontWeight directly to the wght axis, bypassing CSS weight-matching — so a
// W300 floor on a variable font still renders ExtraLight at wght=200. With static
// files the CSS algorithm truly caps at W300: any Thin/ExtraLight/Light request
// picks the nearest registered entry (W300) and renders at that fixed weight.
val OutfitFamily: FontFamily = FontFamily(
    Font(R.font.outfit_light,    weight = FontWeight.W300),
    Font(R.font.outfit_regular,  weight = FontWeight.W400),
    Font(R.font.outfit_medium,   weight = FontWeight.W500),
    Font(R.font.outfit_semibold, weight = FontWeight.W600),
)

val NunitoFamily: FontFamily = FontFamily(
    Font(R.font.nunito_light,    weight = FontWeight.W300),
    Font(R.font.nunito_regular,  weight = FontWeight.W400),
    Font(R.font.nunito_medium,   weight = FontWeight.W500),
    Font(R.font.nunito_semibold, weight = FontWeight.W600),
)

fun tileGradientBrush(accent: Color): Brush {
    val light = lerp(accent, Color.White, 0.15f)
    val dark = lerp(accent, Color.Black, 0.30f)
    return Brush.linearGradient(listOf(light, dark))
}

/**
 * The bottom-right corner-arc resize glyph (One-UI-inspired: a quarter-circle
 * stroke sweeping from the right edge down to the bottom, reading as "pull
 * the corner outward") — the same shape drawn for drag-resize on Quick Panel
 * tiles, the feed page's widget cards, and a selected Start tile's own corner
 * control, shared here so all three read as one visual language instead of
 * reimplementing the same arc per surface. Purely visual — draws in whatever
 * square area [modifier] gives it; the drag gesture itself is wired up
 * separately by the caller (e.g. Start's `tileStretchGesture`, unrelated to
 * this glyph).
 */
@Composable
fun CornerArcGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private fun lerp(color: Color, other: Color, fraction: Float): Color = Color(
    red = color.red + (other.red - color.red) * fraction,
    green = color.green + (other.green - color.green) * fraction,
    blue = color.blue + (other.blue - color.blue) * fraction,
    alpha = color.alpha,
)
