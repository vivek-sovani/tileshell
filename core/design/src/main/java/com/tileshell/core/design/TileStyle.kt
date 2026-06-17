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

val OutfitFamily: FontFamily = FontFamily(
    Font(R.font.outfit_variable, weight = FontWeight.W100),
    Font(R.font.outfit_variable, weight = FontWeight.W200),
    Font(R.font.outfit_variable, weight = FontWeight.W300),
    Font(R.font.outfit_variable, weight = FontWeight.W400),
    Font(R.font.outfit_variable, weight = FontWeight.W500),
    Font(R.font.outfit_variable, weight = FontWeight.W600),
    Font(R.font.outfit_variable, weight = FontWeight.W700),
)

val NunitoFamily: FontFamily = FontFamily(
    Font(R.font.nunito_variable, weight = FontWeight.W200),
    Font(R.font.nunito_variable, weight = FontWeight.W300),
    Font(R.font.nunito_variable, weight = FontWeight.W400),
    Font(R.font.nunito_variable, weight = FontWeight.W500),
    Font(R.font.nunito_variable, weight = FontWeight.W600),
    Font(R.font.nunito_variable, weight = FontWeight.W700),
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
