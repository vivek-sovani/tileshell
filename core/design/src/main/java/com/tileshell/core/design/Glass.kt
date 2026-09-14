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
     * regardless of theme, so white stays right there. The only case where a tile's
     * fill can genuinely read as light is when the wallpaper actually shows through
     * it — glass (transparent) tiles, or "wallpaper behind tiles" mode — *and* that
     * wallpaper is itself light (the plain theme background in light theme, or a
     * light custom photo; the bundled gradients are dark-base-first and stay
     * mid-toned even lifted for light theme, so they never trigger this). The
     * caller resolves that full condition into [useDarkText] — see
     * docs/DECISIONS.md "Live tile text: black when the wallpaper behind it is
     * light".
     */
    fun faceTextColor(useDarkText: Boolean): Color =
        if (useDarkText) Color(red = 20f / 255f, green = 20f / 255f, blue = 26f / 255f) else Color.White

    /**
     * Fill for a "borderless" tile — the one thing it *does* paint. Deliberately
     * far fainter than [fill]: it is not a tile colour, it is a barely-there lift
     * of whatever the wallpaper already shows there, so the tile reads as a raised
     * pane of the same material rather than a coloured square. Accent-blind for
     * that reason (unlike [fill], which tints by the tile's own colour) — a
     * borderless tile is meant to disappear into the wallpaper except for its
     * elevation.
     *
     * White in *both* themes, unlike most of this file's light/dark pairs: a
     * raised surface catches more light than the ground it sits on, so
     * darkening it would read as recessed, not raised. Only the amount
     * differs — the same 9% that clearly lifts a near-black backdrop is
     * invisible against a light one, which needs a good deal more to register.
     */
    fun raisedFill(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.30f)

    /**
     * The shadow colour under a borderless tile. Near-black in both themes —
     * a shadow is an absence of light, so it does not invert with the theme the
     * way [raisedFill] does; only its strength differs, since the same shadow
     * over a light background reads much heavier.
     */
    fun raisedShadow(dark: Boolean): Color =
        if (dark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.28f)
}
