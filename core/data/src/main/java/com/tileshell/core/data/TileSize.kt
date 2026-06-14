package com.tileshell.core.data

/**
 * Tile footprints on the 4-column grid (CLAUDE.md normative values):
 * small 1×1, medium 2×2, wide 4×2.
 *
 * The 4×4 "large" size was dropped (small/medium/wide are sufficient); any legacy
 * `LARGE` row persisted before that change decodes to [MEDIUM] via the Room
 * converter's tolerant fallback.
 *
 * Canonical home for the size enum: it is a persisted layout value (Room) and
 * the packer in `:feature:start` consumes it. See docs/DECISIONS.md (S5).
 */
enum class TileSize(val cols: Int, val rows: Int) {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    ;

    /**
     * The next size in the resize cycle (FR-3.4): medium → small → wide → medium,
     * wrapping. A tap on a tile's resize control walks this order; medium is the
     * default landing size, so the cycle starts and returns there.
     */
    fun next(): TileSize = when (this) {
        MEDIUM -> SMALL
        SMALL -> WIDE
        WIDE -> MEDIUM
    }
}
