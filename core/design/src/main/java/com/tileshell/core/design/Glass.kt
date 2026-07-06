package com.tileshell.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Transparent-tile ("glass") fill. The alpha formula matches
 * `applyTransparency()` in design/.../launcher/launcher.js:
 *
 *   a = 0.62·(1 − t) + 0.05,  t = transparency slider in 0..1
 *
 * The prototype's own colour is a fixed neutral (dark 18,18,24 / light
 * 250,250,252) — deliberately deviated from here (user-requested, see
 * DECISIONS "Glass tint follows tile accent"): a glass tile should still read
 * as *that tile's* colour seen through frosted glass, not an accent-blind
 * neutral square identical across every tile. [tint] blends the neutral frost
 * with the tile's own resolved [accent] before applying the transparency
 * alpha, so e.g. a blue tile and a red tile render distinguishably tinted
 * glass instead of the same grey/near-white square.
 */
object Glass {

    /** Alpha for a transparency slider value in 0..1 (pure; unit-tested). */
    fun alpha(transparency: Float): Float =
        (0.62f * (1f - transparency) + 0.05f).coerceIn(0f, 1f)

    /** The prototype's neutral frost colour, before any accent tint. */
    private fun neutral(dark: Boolean): Color =
        if (dark) Color(red = 18f / 255f, green = 18f / 255f, blue = 24f / 255f)
        else Color(red = 250f / 255f, green = 250f / 255f, blue = 252f / 255f)

    /** Glass fill colour for [accent], tinted and at the given [transparency]. */
    fun fill(dark: Boolean, transparency: Float, accent: Color): Color {
        val tinted = lerp(neutral(dark), accent, 0.65f)
        return tinted.copy(alpha = alpha(transparency))
    }
}
