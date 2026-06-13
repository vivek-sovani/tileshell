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

    /** Lookup by the prototype colour id. */
    val byId: Map<String, Color> = mapOf(
        "blue" to Blue, "cobalt" to Cobalt, "purple" to Purple, "magenta" to Magenta,
        "red" to Red, "orange" to Orange, "amber" to Amber, "lime" to Lime,
        "green" to Green, "teal" to Teal, "cyan" to Cyan, "steel" to Steel,
        "mauve" to Mauve, "slate" to Slate,
    )

    /** Accent for a colour id, falling back to [Blue] for unknown ids. */
    fun forId(id: String?): Color = byId[id] ?: Blue
}
