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

    /**
     * Colour for tile face text/icons (live-tile faces, the static glyph, tile
     * labels). A solid tile's fill is always the user's saturated accent colour
     * regardless of theme, so white stays right there. Only when [glass] (transparent
     * tiles) is on *and* the theme is light does the tile fill itself wash out toward
     * near-white (see [fill]/[neutral]), where white text loses contrast — user-requested,
     * see docs/DECISIONS.md "Live tile text: black on glass tiles in light theme".
     */
    fun faceTextColor(dark: Boolean, glass: Boolean): Color =
        if (glass && !dark) Color(red = 20f / 255f, green = 20f / 255f, blue = 26f / 255f) else Color.White
}
