package com.tileshell.core.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * One radial layer of a mesh-gradient wallpaper, modelling a CSS
 * `radial-gradient(<radiusPct>% … at <cx>% <cy>%, <color> 0%, transparent <fade>%)`.
 *
 * @property cx,cy centre as a fraction of the box (0..1)
 * @property radiusPct gradient radius as a fraction of box width (CSS first %, e.g. 1.2 = 120%)
 * @property fade fraction of the radius at which the colour reaches transparent
 */
data class WallpaperLayer(
    val color: Color,
    val cx: Float,
    val cy: Float,
    val radiusPct: Float,
    val fade: Float,
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

    /** The 6 bundled wallpapers in prototype order. */
    val all: List<WallpaperGradient> = listOf(Aurora, Dusk, Ocean, Forest, Rose, Mono)

    val byId: Map<String, WallpaperGradient> = all.associateBy { it.id }

    fun forId(id: String?): WallpaperGradient = byId[id] ?: Aurora

    /** Sentinel id meaning "no wallpaper — render the theme bg colour". */
    const val NONE_ID = "none"
}

/**
 * All 6 bundled gradients are designed dark-base-first (a WP-style deep
 * backdrop with colourful glows). In light theme that near-black base read as
 * a flat black fill wherever a layer's glow hasn't reached — most of the
 * gaps between tiles. [dark] blends the base most of the way toward the
 * light theme's own background tone (rather than a plain "invert"), and eases
 * each glow layer a little toward white so it stays legible against the
 * lighter backdrop instead of muddying it.
 */
private fun themedBase(base: Color, dark: Boolean): Color =
    if (dark) base else lerp(base, LightColorTokens.bg, 0.82f)

private fun themedLayer(color: Color, dark: Boolean): Color =
    if (dark) color else lerp(color, Color.White, 0.30f)

/**
 * Paints [wallpaper] as the background of the modified node: the base colour
 * first, then each radial layer composited over it (matching the CSS layer
 * order, top gradient last). [dark] selects the theme-appropriate palette
 * (see [themedBase]/[themedLayer]).
 */
fun Modifier.wallpaperBackground(wallpaper: WallpaperGradient, dark: Boolean = true): Modifier = drawBehind {
    drawRect(themedBase(wallpaper.base, dark))
    wallpaper.layers.forEach { layer ->
        val color = themedLayer(layer.color, dark)
        val radius = (layer.radiusPct * size.width).coerceAtLeast(0.01f)
        val fade = layer.fade.coerceIn(0.01f, 1f)
        drawRect(
            brush = Brush.radialGradient(
                // A third, partially-faded stop midway through the falloff
                // smooths the transition to transparent — a plain 2-stop
                // gradient bands visibly across the large, mostly-flat areas
                // these radial glows fall off into.
                colorStops = arrayOf(
                    0f to color,
                    fade * 0.55f to color.copy(alpha = color.alpha * 0.35f),
                    fade to Color.Transparent,
                ),
                center = Offset(layer.cx * size.width, layer.cy * size.height),
                radius = radius,
            ),
        )
    }
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
        val fade = layer.fade.coerceIn(0.01f, 1f)
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to color,
                    fade * 0.55f to color.copy(alpha = color.alpha * 0.35f),
                    fade to Color.Transparent,
                ),
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
