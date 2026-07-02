package com.tileshell.core.data

/**
 * Tile footprints on the 4-column grid (CLAUDE.md normative values):
 * small 1×1, medium 2×2, wide 4×2, large 3×3.
 *
 * [LARGE] (3×3) is special: it is only reachable in the resize cycle for music /
 * news app tiles on a 5- or 6-column grid (see [AppCategories.allowsLargeTile]),
 * and a large tile auto-shrinks back to [MEDIUM] when the grid drops to 4 columns.
 * Every other tile cycles medium → small → wide → medium and never sees large.
 *
 * Canonical home for the size enum: it is a persisted layout value (Room) and
 * the packer in `:feature:start` consumes it. See docs/DECISIONS.md (S5).
 */
enum class TileSize(val cols: Int, val rows: Int) {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    LARGE(3, 3),
    ;

    /**
     * The next size in the resize cycle (FR-3.4). A tap on a tile's resize control
     * walks this order; medium is the default landing size, so the cycle starts and
     * returns there.
     *
     * When [largeAllowed] is false (the default — every non-music/news tile, and any
     * tile on a 4-column grid) the cycle is medium → small → wide → medium. When
     * true (music / news tiles on a 5/6-column grid) wide steps up to [LARGE] before
     * wrapping back to medium: medium → small → wide → large → medium.
     */
    fun next(largeAllowed: Boolean = false): TileSize = when (this) {
        MEDIUM -> SMALL
        SMALL -> WIDE
        WIDE -> if (largeAllowed) LARGE else MEDIUM
        LARGE -> MEDIUM
    }

    val area get() = cols * rows

    fun nextIsLarger(largeAllowed: Boolean = false) = next(largeAllowed).area > area

    /**
     * Resize cycle for a folder child, which is deliberately tighter than a
     * top-level tile's: on a 5/6-column grid ([largeAllowed]) it gets the full
     * [next] cycle (small→medium→wide→large); on a 4-column grid it keeps a
     * plain small↔medium toggle rather than [next]'s medium→small→wide, since a
     * WIDE child would crowd the folder overlay's grid at that density.
     */
    fun nextForFolderChild(largeAllowed: Boolean): TileSize =
        if (largeAllowed) next(largeAllowed = true) else if (this == SMALL) MEDIUM else SMALL
}
