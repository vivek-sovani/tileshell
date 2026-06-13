package com.tileshell.core.data

/**
 * Tile footprints on the 4-column grid (CLAUDE.md normative values):
 * small 1×1, medium 2×2, wide 4×2, large 4×4.
 *
 * Canonical home for the size enum: it is a persisted layout value (Room) and
 * the packer in `:feature:start` consumes it. See docs/DECISIONS.md (S5).
 */
enum class TileSize(val cols: Int, val rows: Int) {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    LARGE(4, 4),
    ;

    /**
     * The next size in the resize cycle (FR-3.4): small → medium → wide → large
     * → small, wrapping. Mirrors the prototype `cycleSize` order.
     */
    fun next(): TileSize {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }
}
