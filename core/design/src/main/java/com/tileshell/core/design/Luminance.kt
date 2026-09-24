package com.tileshell.core.design

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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

/**
 * WCAG 2.1 relative luminance (gamma-corrected sRGB) — unlike [perceivedLuminance]
 * above, this is the real formula behind [contrastRatio], needed wherever a
 * *guaranteed* minimum contrast matters rather than just a coarse light/dark
 * classification.
 */
private fun relativeLuminance(color: Color): Float {
    fun channel(c: Float): Float {
        val v = c.coerceIn(0f, 1f)
        return if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

/** WCAG 2.1 contrast ratio between [a] and [b], from 1 (identical) to 21 (black on white). */
fun contrastRatio(a: Color, b: Color): Float {
    val l1 = relativeLuminance(a) + 0.05f
    val l2 = relativeLuminance(b) + 0.05f
    return max(l1, l2) / min(l1, l2)
}

/**
 * True when black reads at least as well as white directly on [background] —
 * a real contrast-ratio comparison, unlike [isLightBackground]'s single
 * fixed-threshold heuristic (tuned for coarse "is this a dark or light
 * screen" calls, not for guaranteeing legible text on an arbitrary saturated
 * colour like a user-chosen accent).
 */
fun prefersDarkText(background: Color): Boolean =
    contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)
