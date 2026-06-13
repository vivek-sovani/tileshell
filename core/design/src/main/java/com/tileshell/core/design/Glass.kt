package com.tileshell.core.design

import androidx.compose.ui.graphics.Color

/**
 * Transparent-tile ("glass") fill, matching `applyTransparency()` in
 * design/.../launcher/launcher.js:
 *
 *   a = 0.62·(1 − t) + 0.05,  t = transparency slider in 0..1
 *   rgb = dark (18,18,24) / light (250,250,252)
 */
object Glass {

    /** Alpha for a transparency slider value in 0..1 (pure; unit-tested). */
    fun alpha(transparency: Float): Float =
        (0.62f * (1f - transparency) + 0.05f).coerceIn(0f, 1f)

    /** Glass fill colour for the active theme at the given transparency. */
    fun fill(dark: Boolean, transparency: Float): Color {
        val a = alpha(transparency)
        return if (dark) {
            Color(red = 18f / 255f, green = 18f / 255f, blue = 24f / 255f, alpha = a)
        } else {
            Color(red = 250f / 255f, green = 250f / 255f, blue = 252f / 255f, alpha = a)
        }
    }
}
