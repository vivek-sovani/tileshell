package com.tileshell.core.data

/**
 * The 14 prototype tile-colour ids (palette order matches `TileAccents` in
 * :core:design). Kept here, framework-free, so layout/pin logic can pick a
 * colour without depending on the design module.
 */
object TileColors {

    /** Colour ids in palette order, from `window.TILE_COLORS` (data.js). */
    val IDS: List<String> = listOf(
        "blue", "cobalt", "purple", "magenta", "red", "orange", "amber",
        "lime", "green", "teal", "cyan", "steel", "mauve", "slate",
    )

    /**
     * A stable default colour id for an app pinned from the app list (FR-5).
     * Real Android apps carry no WP tile colour, so we derive a deterministic
     * one from the package name — the same app always pins in the same colour
     * (see docs/DECISIONS.md S11), rather than a wall of identical blue tiles.
     */
    fun defaultIdFor(packageName: String): String {
        // Deterministic, platform-independent fold (don't rely on hashCode).
        var hash = 0
        for (c in packageName) hash = (hash * 31 + c.code) and 0x7fffffff
        return IDS[hash % IDS.size]
    }
}
