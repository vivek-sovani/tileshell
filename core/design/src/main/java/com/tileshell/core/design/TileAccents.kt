package com.tileshell.core.design

import androidx.compose.ui.graphics.Color

/**
 * The 14 recolourable tile accent colours, ported from `window.TILE_COLORS` in
 * design/.../launcher/data.js. [byId] keys match the prototype colour ids used
 * by the default layout (e.g. tile `color:'cobalt'`).
 */
object TileAccents {
    val Blue = Color(0xFF2B78E4)
    val Cobalt = Color(0xFF1452CC)
    val Purple = Color(0xFF6B3FD4)
    val Magenta = Color(0xFFC4287E)
    val Red = Color(0xFFD6262B)
    val Orange = Color(0xFFE5641E)
    val Amber = Color(0xFFE2A200)
    val Lime = Color(0xFF7CB518)
    val Green = Color(0xFF1F9E57)
    val Teal = Color(0xFF0F9B9B)
    val Cyan = Color(0xFF1399C6)
    val Steel = Color(0xFF5A6B7B)
    val Mauve = Color(0xFF9B6A8F)
    val Slate = Color(0xFF3A4554)

    /** All 14 accents in palette order. */
    val all: List<Color> = listOf(
        Blue, Cobalt, Purple, Magenta, Red, Orange, Amber,
        Lime, Green, Teal, Cyan, Steel, Mauve, Slate,
    )

    /** The 14 colour ids in palette order (parallel to [all]). */
    val ids: List<String> = listOf(
        "blue", "cobalt", "purple", "magenta", "red", "orange", "amber",
        "lime", "green", "teal", "cyan", "steel", "mauve", "slate",
    )

    /** (id, colour) pairs in palette order — drives the personalize swatches. */
    val swatches: List<Pair<String, Color>> = ids.zip(all)

    /** Lookup by the prototype colour id. */
    val byId: Map<String, Color> = mapOf(
        "blue" to Blue, "cobalt" to Cobalt, "purple" to Purple, "magenta" to Magenta,
        "red" to Red, "orange" to Orange, "amber" to Amber, "lime" to Lime,
        "green" to Green, "teal" to Teal, "cyan" to Cyan, "steel" to Steel,
        "mauve" to Mauve, "slate" to Slate,
    )

    /** Accent for a colour id, falling back to [Blue] for unknown ids. */
    fun forId(id: String?): Color = byId[id] ?: Blue

    /**
     * Resolve a per-tile [override] to a colour (FR-7): a `#RRGGBB` hex (an exact
     * icon colour) wins, then a palette id, otherwise the global [globalAccentId]
     * accent. `null`/blank/unknown override → follow the global accent.
     */
    fun colorForOverride(override: String?, globalAccentId: String): Color {
        if (override != null) {
            parseHexColor(override)?.let { return it }
            byId[override]?.let { return it }
        }
        return forId(globalAccentId)
    }

    /** Parse a `#RRGGBB` string to an opaque [Color], or null if malformed. */
    fun parseHexColor(value: String): Color? {
        if (!value.startsWith("#") || value.length != 7) return null
        val rgb = value.substring(1).toLongOrNull(16) ?: return null
        return Color(0xFF000000L or rgb)
    }

    /**
     * The palette id whose accent is closest to [color], by luminance-weighted
     * RGB distance (green weighted highest, matching perceived difference). Used
     * to suggest a per-tile colour from an app icon's dominant hue (FR-7). Pure.
     */
    fun nearestAccentId(color: Color): String {
        var bestId = ids.first()
        var bestD = Float.MAX_VALUE
        swatches.forEach { (id, c) ->
            val dr = c.red - color.red
            val dg = c.green - color.green
            val db = c.blue - color.blue
            val d = 2f * dr * dr + 4f * dg * dg + 3f * db * db
            if (d < bestD) { bestD = d; bestId = id }
        }
        return bestId
    }
}
