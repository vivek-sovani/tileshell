package com.tileshell.core.design

import androidx.compose.ui.graphics.Color

/**
 * Perceived (non-linear sRGB) brightness in 0..1 — a cheap heuristic for "does
 * this backdrop need light or dark text", not a WCAG-exact relative luminance.
 */
fun perceivedLuminance(color: Color): Float =
    (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue).coerceIn(0f, 1f)

/**
 * Threshold separating this app's own dark/light screen tokens (~0.04 for
 * [DarkColorTokens.bg], ~0.92 for [LightColorTokens.bg]) — also a reasonable
 * cutoff for an arbitrary user-chosen wallpaper photo.
 */
const val LIGHT_BACKGROUND_LUMINANCE_THRESHOLD = 0.6f

/** True when [color] is light enough that dark (not white) text reads better on it. */
fun isLightBackground(color: Color): Boolean =
    perceivedLuminance(color) > LIGHT_BACKGROUND_LUMINANCE_THRESHOLD
