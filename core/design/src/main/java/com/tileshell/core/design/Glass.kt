package com.tileshell.core.design

import androidx.compose.ui.graphics.Brush
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
     * Card fill for a "borderless" tile — the one thing it paints besides its
     * own drop shadow. Meant to read as a genuine widget-style card (like an
     * Android home-screen gadget's own translucent surface), not a coloured
     * tile: accent-blind on purpose.
     *
     * White in *both* themes — a first pass here flipped to a dark neutral
     * tint for dark theme (reasoning: "a real widget's card matches the
     * theme"), which was wrong in practice: layering a dark tint over an
     * already near-black wallpaper composites to something just as dark, so
     * the card never actually lifted off its background — user-reported
     * "looks very dark." Material's own dark-theme convention is the opposite
     * of that intuition: an *elevated* dark-theme surface gets lighter than
     * its background, not darker, which is what a white overlay (at a modest
     * alpha) actually achieves.
     *
     * **Same alpha range in both themes** — a second pass gave light theme a
     * much stronger range (0.34–0.78 vs. dark's 0.12–0.30) on the theory that
     * "the same wash that lifts a near-black backdrop needs to be much
     * stronger to register against a light one." Wrong in practice too, in
     * the opposite direction from the dark-theme mistake above: user-reported
     * "light mode tiles too white" and "transparency level not comparable to
     * dark mode. dark mode has right levels" — nearly 3x the alpha reads as a
     * stark white square rather than a subtle lift, and the two themes no
     * longer felt like the same slider. One range for both themes fixes both
     * complaints at once: comparable by construction (it's the same number),
     * and light theme is no longer overexposed.
     *
     * [transparency] is the same 0..1 "tile transparency" slider glass
     * already uses (Personalize surfaces it for borderless too, alongside
     * transparent) — 0 = the most opaque, clearly-a-card end; 1 = the
     * faintest the card can go while still reading as a raised surface (never
     * all the way to invisible, since a truly-zero-alpha "card" isn't a card
     * any more — that's what [Glass.fill]'s own 0.05 floor is for, at a
     * lower target since glass tints don't need a real shadow's help).
     */
    fun raisedCardFill(dark: Boolean, transparency: Float): Color {
        val t = transparency.coerceIn(0f, 1f)
        val (max, min) = 0.30f to 0.12f
        return Color.White.copy(alpha = max - t * (max - min))
    }

    /**
     * [accent] tinted for use directly as an icon/label colour on a widget
     * card (Quick Panel's own "on" glyph under `borderlessTiles` — see
     * DECISIONS.md "Widget cards carried onto Quick Panel"), user-reported as
     * needing to read "a little darker" against a light-theme card: the
     * accent's own saturated brightness, fine against a saturated accent
     * *fill*, reads washed-out as a thin glyph sitting on the pale, near-white
     * card [raisedCardFill] paints in light theme. Left untouched in dark
     * theme, where the same accent already has enough contrast against the
     * darker card. A flat 18% blend toward black rather than a per-colour
     * luminance calculation — simple, and "a little darker" is what was
     * asked for, not "as dark as it can go."
     */
    fun accentOnCard(dark: Boolean, accent: Color): Color =
        if (dark) accent else lerp(accent, Color.Black, 0.18f)

    /**
     * Fill for a section's tinted panel background (Start "sections"): a
     * visibly boxed, [accent]-tinted region behind a section's header + tiles,
     * distinguishing it from the plain unsectioned area. A first mockup used a
     * faint ~14% alpha, which composites to almost no visible shift over a
     * near-black background (`accent.copy(alpha = 0.14f)` over `#0a0a0d` nets
     * roughly `rgb(15,25,43)` vs. the background's own `rgb(10,10,13)`) — the
     * user correctly couldn't tell it apart from an untinted section. This is
     * the stronger value verified (via real colour math, then visually) to
     * read clearly in both themes.
     */
    fun sectionPanelFill(dark: Boolean, accent: Color): Color = accent.copy(alpha = if (dark) 0.30f else 0.16f)

    /** Border for the same panel (see [sectionPanelFill]) — stronger alpha so the
     *  panel's edge itself is legible, not just its fill. */
    fun sectionPanelBorder(dark: Boolean, accent: Color): Color = accent.copy(alpha = if (dark) 0.55f else 0.4f)
}
