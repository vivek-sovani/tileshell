package com.tileshell.core.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

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

private fun lerp(color: Color, other: Color, fraction: Float): Color = Color(
    red = color.red + (other.red - color.red) * fraction,
    green = color.green + (other.green - color.green) * fraction,
    blue = color.blue + (other.blue - color.blue) * fraction,
    alpha = color.alpha,
)
