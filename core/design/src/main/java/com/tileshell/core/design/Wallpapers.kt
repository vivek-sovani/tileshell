package com.tileshell.core.design

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * One radial layer of a mesh-gradient wallpaper, modelling a CSS
 * `radial-gradient(<radiusPct>% … at <cx>% <cy>%, <color> 0%, transparent <fade>%)`.
 *
 * @property cx,cy centre as a fraction of the box (0..1)
 * @property radiusPct gradient radius as a fraction of box width (CSS first %, e.g. 1.2 = 120%)
 * @property fade fraction of the radius at which the colour reaches transparent
 * @property core fraction of [fade] out to which the colour holds at full alpha
 *   before it starts falling off. `0` (the default, and what every ported
 *   prototype gradient uses) is the original soft glow — alpha decays from the
 *   very centre. A value close to `1` instead paints a flat disc with a crisp
 *   edge, which is how [Wallpapers.Nebula]'s two circles are drawn; the
 *   remaining `fade - core` sliver is the antialiasing feather, so it must
 *   stay non-zero or the edge aliases.
 */
data class WallpaperLayer(
    val color: Color,
    val cx: Float,
    val cy: Float,
    val radiusPct: Float,
    val fade: Float,
    val core: Float = 0f,
)

/**
 * A mesh-gradient wallpaper: a flat [base] colour with [layers] of radial
 * gradients painted over it, ported from `window.WALLPAPERS` in
 * design/.../launcher/data.js. The vertical-radius component of each CSS
 * gradient is approximated by a circle (radius derived from box width), which
 * reads identically as a soft backdrop.
 */
data class WallpaperGradient(
    val id: String,
    val label: String,
    val base: Color,
    val layers: List<WallpaperLayer>,
    /**
     * `true` only for the "disc field" family ([Wallpapers.Nebula]/[Ember]/
     * [Reef]) — their layers are crisp, flat-edged circles (see
     * [WallpaperLayer.core]), not soft glows. That hard-edged look is right
     * as the actual Start/lock-screen wallpaper, but doesn't suit Quick
     * Panel or the glance page — those surfaces need a soft, muted backdrop
     * behind their cards/text, the same reason a custom *photo* is never
     * shown there directly either (see [rememberFeedPalette]/
     * `photoGradient` in `feature/start/feed/FeedPage.kt`). A `discField`
     * wallpaper should get the same treatment: synthesize a soft gradient
     * from its own colours instead of passing it through as-is.
     */
    val discField: Boolean = false,
)

object Wallpapers {

    val Aurora = WallpaperGradient(
        id = "aurora", label = "aurora", base = Color(0xFF0C1320),
        layers = listOf(
            WallpaperLayer(Color(0xFF1C6E5A), 0.15f, 0.10f, 1.2f, 0.55f),
            WallpaperLayer(Color(0xFF2A3B7A), 0.85f, 0.00f, 1.2f, 0.50f),
            WallpaperLayer(Color(0xFF5B2A6E), 0.70f, 1.00f, 1.4f, 0.55f),
        ),
    )

    val Dusk = WallpaperGradient(
        id = "dusk", label = "dusk", base = Color(0xFF160D1A),
        layers = listOf(
            WallpaperLayer(Color(0xFFB5341F), 0.10f, 1.00f, 1.2f, 0.55f),
            WallpaperLayer(Color(0xFFD06A1E), 0.90f, 0.90f, 1.2f, 0.50f),
            WallpaperLayer(Color(0xFF4A2360), 0.60f, 0.00f, 1.4f, 0.60f),
        ),
    )

    val Ocean = WallpaperGradient(
        id = "ocean", label = "ocean", base = Color(0xFF06121D),
        layers = listOf(
            WallpaperLayer(Color(0xFF1486C4), 0.80f, 0.15f, 1.2f, 0.55f),
            WallpaperLayer(Color(0xFF0E5F8A), 0.10f, 0.85f, 1.2f, 0.55f),
            WallpaperLayer(Color(0xFF146B9B), 0.50f, 0.50f, 1.4f, 0.70f),
        ),
    )

    val Forest = WallpaperGradient(
        id = "forest", label = "forest", base = Color(0xFF0A140C),
        layers = listOf(
            WallpaperLayer(Color(0xFF2F7D3A), 0.20f, 0.20f, 1.2f, 0.55f),
            WallpaperLayer(Color(0xFF156B52), 0.90f, 0.80f, 1.2f, 0.55f),
            WallpaperLayer(Color(0xFF3A5A1F), 0.60f, 0.50f, 1.2f, 0.70f),
        ),
    )

    val Rose = WallpaperGradient(
        id = "rose", label = "rose", base = Color(0xFF1A0D16),
        layers = listOf(
            WallpaperLayer(Color(0xFFC4287E), 0.15f, 0.10f, 1.2f, 0.55f),
            WallpaperLayer(Color(0xFF7A2C8A), 0.90f, 0.90f, 1.2f, 0.55f),
            WallpaperLayer(Color(0xFFD0556A), 0.60f, 0.40f, 1.2f, 0.65f),
        ),
    )

    val Mono = WallpaperGradient(
        id = "mono", label = "mono", base = Color(0xFF131318),
        layers = listOf(
            WallpaperLayer(Color(0xFF2A2A31), 0.30f, 0.20f, 1.2f, 0.70f),
        ),
    )

    /**
     * Shared geometry for the "disc field" family ([Nebula]/[Ember]/[Reef]):
     * eleven flat, crisp-edged circles (see [WallpaperLayer.core]) scattered
     * across the *whole* canvas — not just two corners. The original two-disc
     * version only covered opposite corners, which read as near-empty on a
     * full-height lock screen (no tile grid there to fill in the gap); this
     * spreads circles from top to bottom at varied sizes, including the
     * middle band, so the design holds up full-bleed. `discFieldColorSlot`
     * selects which of the family's two colours each same-index circle uses.
     */
    private val discFieldLayout: List<Triple<Float, Float, Float>> = listOf(
        Triple(0.08f, 0.05f, 0.34f), // top-left, large
        Triple(0.90f, 0.10f, 0.22f), // top-right, medium
        Triple(0.35f, 0.20f, 0.16f), // upper-middle, small
        Triple(0.65f, 0.30f, 0.20f), // upper-right-middle, medium
        Triple(0.05f, 0.42f, 0.18f), // left-middle, small
        Triple(0.50f, 0.50f, 0.24f), // centre, medium
        Triple(0.92f, 0.48f, 0.16f), // right-middle, small
        Triple(0.20f, 0.68f, 0.20f), // lower-left-middle, medium
        Triple(0.75f, 0.75f, 0.28f), // lower-right-middle, medium
        Triple(0.10f, 0.90f, 0.22f), // bottom-left, medium
        Triple(0.96f, 0.96f, 0.36f), // bottom-right, large
    )
    private val discFieldColorSlot = listOf(0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0)

    private fun discField(colorA: Color, colorB: Color): List<WallpaperLayer> =
        discFieldLayout.mapIndexed { i, (cx, cy, r) ->
            val color = if (discFieldColorSlot[i] == 0) colorA else colorB
            WallpaperLayer(color, cx, cy, r, fade = 1f, core = 0.96f)
        }

    /**
     * Not from the prototype — added after the "borderless tiles" design pass,
     * where this near-black backdrop read best behind unfilled tiles. Its
     * layers are **flat discs with crisp edges** (see [WallpaperLayer.core]),
     * not soft glows: that hard-edged geometry is the whole look, and
     * rendering it as a glow reads as a different wallpaper entirely. See
     * [discField] — six circles spread across the full canvas, not just two
     * corners, so the design still reads on a full-height lock screen. Sits
     * on the same near-black base as the dark theme's own `bg`, so a
     * borderless/glass tile's content stays high-contrast wherever no disc
     * reaches.
     */
    val Nebula = WallpaperGradient(
        id = "nebula", label = "nebula", base = Color(0xFF0A0A0D),
        layers = discField(Color(0xFF1D5AA8), Color(0xFF7A3A6A)),
        discField = true,
    )

    /**
     * [Nebula]'s warm counterpart. Same geometry to the pixel — only the two
     * disc colours differ, which is what makes the three read as one family
     * rather than three unrelated wallpapers.
     */
    val Ember = WallpaperGradient(
        id = "ember", label = "ember", base = Color(0xFF0A0A0D),
        layers = discField(Color(0xFFC25A14), Color(0xFF8C2F4A)),
        discField = true,
    )

    /** The cool-green third of the disc family — see [Ember]. */
    val Reef = WallpaperGradient(
        id = "reef", label = "reef", base = Color(0xFF0A0A0D),
        layers = discField(Color(0xFF128C7A), Color(0xFF5C8A1C)),
        discField = true,
    )

    /**
     * The 6 prototype wallpapers in prototype order, then the three hard-edged
     * disc wallpapers as their own trailing row. Kept at a multiple of three so
     * the picker's 3-per-row grid has no short final row — see
     * `PersonalizeSheet`'s stock swatch grid.
     */
    val all: List<WallpaperGradient> =
        listOf(Aurora, Dusk, Ocean, Forest, Rose, Mono, Nebula, Ember, Reef)

    val byId: Map<String, WallpaperGradient> = all.associateBy { it.id }

    fun forId(id: String?): WallpaperGradient = byId[id] ?: Aurora

    /** Sentinel id meaning "no wallpaper — render the theme bg colour". */
    const val NONE_ID = "none"
}

/**
 * All 6 bundled gradients are designed dark-base-first (a WP-style deep
 * backdrop with colourful glows). In light theme that near-black base read as
 * a flat black fill wherever a layer's glow hasn't reached — most of the
 * gaps between tiles. [dark] lifts the base toward the light theme's own
 * background tone, but only partway — a full blend washed the base to almost
 * pure `LightColorTokens.bg` with barely a hint of the original hue, which
 * read as "too light"/washed out. A ~45% lift keeps a mid-tone version of the
 * gradient's own colour (recognisably not black, not blown out). Layers get a
 * much smaller lift, just enough that the glow doesn't sink into the now
 * mid-toned base.
 */
fun themedBase(base: Color, dark: Boolean): Color =
    if (dark) base else lerp(base, LightColorTokens.bg, 0.45f)

private fun themedLayer(color: Color, dark: Boolean): Color =
    if (dark) color else lerp(color, Color.White, 0.12f)

/**
 * The radial colour stops for one [layer], already resolved to [color] for the
 * active theme. Shared by [wallpaperBackground] and [wallpaperWindow] so a
 * gradient looks identical whether it is painted behind the whole screen or
 * windowed into a single tile.
 */
private fun layerStops(layer: WallpaperLayer, color: Color): Array<Pair<Float, Color>> {
    val fade = layer.fade.coerceIn(0.01f, 1f)
    val core = layer.core.coerceIn(0f, 0.99f)
    return if (core <= 0f) {
        // A third, partially-faded stop midway through the falloff smooths the
        // transition to transparent — a plain 2-stop gradient bands visibly
        // across the large, mostly-flat areas these radial glows fall off into.
        arrayOf(
            0f to color,
            fade * 0.55f to color.copy(alpha = color.alpha * 0.35f),
            fade to Color.Transparent,
        )
    } else {
        // Flat disc: full alpha all the way out to the core, then a short
        // feather to transparent. No mid-stop — there is no long falloff to
        // band across, and one would visibly soften the edge.
        arrayOf(
            0f to color,
            fade * core to color,
            fade to Color.Transparent,
        )
    }
}

/**
 * The base colour, then each radial layer composited over it (matching the
 * CSS layer order, top gradient last) — shared by [wallpaperBackground] (a
 * live composition) and [renderWallpaperToBitmap] (an offscreen raster), so
 * a pushed system wallpaper looks identical to the in-app one.
 */
private fun DrawScope.drawWallpaperGradient(wallpaper: WallpaperGradient, dark: Boolean) {
    drawRect(themedBase(wallpaper.base, dark))
    wallpaper.layers.forEach { layer ->
        val color = themedLayer(layer.color, dark)
        val radius = (layer.radiusPct * size.width).coerceAtLeast(0.01f)
        drawRect(
            brush = Brush.radialGradient(
                colorStops = layerStops(layer, color),
                center = Offset(layer.cx * size.width, layer.cy * size.height),
                radius = radius,
            ),
        )
    }
}

/**
 * Paints [wallpaper] as the background of the modified node. [dark] selects
 * the theme-appropriate palette (see [themedBase]/[themedLayer]).
 */
fun Modifier.wallpaperBackground(wallpaper: WallpaperGradient, dark: Boolean = true): Modifier =
    drawBehind { drawWallpaperGradient(wallpaper, dark) }

/**
 * Rasterizes [wallpaper] to a real [Bitmap] at [widthPx]×[heightPx] — the
 * same draw as [wallpaperBackground], just off-screen, for pushing a bundled
 * gradient to the real Android `WallpaperManager` (see `SystemWallpaperSync`
 * in `:feature:start`), which needs an actual bitmap rather than a live
 * Compose draw.
 */
fun renderWallpaperToBitmap(wallpaper: WallpaperGradient, widthPx: Int, heightPx: Int, dark: Boolean): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = androidx.compose.ui.graphics.Canvas(android.graphics.Canvas(bitmap))
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        drawWallpaperGradient(wallpaper, dark)
    }
    return bitmap
}

/**
 * Paints [wallpaper] as a *window* onto a screen-anchored canvas: the gradient is
 * laid out over a virtual [fullWidth]×[fullHeight] rectangle (the screen) and this
 * tile shows the slice at its current screen [origin]. [origin] is a lambda read in
 * the draw phase, so as the grid scrolls the tile's screen position changes and the
 * wallpaper stays put while the tiles move over it (WP parallax). Used by "wallpaper
 * behind tiles" mode (FR-7 follow-up); adjacent tiles continue one continuous image
 * and the gaps stay dark.
 */
fun Modifier.wallpaperWindow(
    wallpaper: WallpaperGradient,
    fullWidth: Float,
    fullHeight: Float,
    origin: () -> Offset,
    dark: Boolean = true,
): Modifier = drawBehind {
    val o = origin()
    drawRect(themedBase(wallpaper.base, dark))
    wallpaper.layers.forEach { layer ->
        val color = themedLayer(layer.color, dark)
        val radius = (layer.radiusPct * fullWidth).coerceAtLeast(0.01f)
        drawRect(
            brush = Brush.radialGradient(
                colorStops = layerStops(layer, color),
                // Screen-space centre shifted into this tile's local space, so the
                // gradient is continuous across tiles and fixed to the screen.
                center = Offset(
                    layer.cx * fullWidth - o.x,
                    layer.cy * fullHeight - o.y,
                ),
                radius = radius,
            ),
        )
    }
}
