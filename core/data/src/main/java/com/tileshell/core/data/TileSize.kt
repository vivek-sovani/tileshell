package com.tileshell.core.data

/**
 * Tile footprints on the 4-column grid (CLAUDE.md normative values):
 * small 1×1, medium 2×2, wide 4×2, large 3×3 — plus seven presets added for
 * gesture-based drag resize (Android-icons-mode arc): [WIDE_SMALL] 2×1,
 * [TALL] 1×2, [WIDE_MEDIUM] 3×2, [TALL_MEDIUM] 2×3, [XLARGE] 4×4, [BANNER]
 * 4×1, [COLUMN] 1×4. The tap resize cycle ([next]) deliberately stays on the
 * original four — dragging a tile's resize handles (`StartViewModel.resizeTo`)
 * is the only way to reach the seven newer presets, since cycling eleven
 * sizes by tap would be unusable. All eleven are reachable at any column
 * count; [GridPacker] reads only [cols]/[rows] and never branches on the
 * enum, so no packer change was needed to add these.
 *
 * [LARGE] (3×3) is reachable in the resize cycle for any app tile on any grid
 * density (see [AppCategories.allowsLargeTile]) — a caller that doesn't opt a
 * tile into the large step (`largeAllowed = false`) still cycles
 * medium → small → wide → medium and never sees large.
 *
 * Canonical home for the size enum: it is a persisted layout value (Room) and
 * the packer in `:feature:start` consumes it. See docs/DECISIONS.md (S5).
 */
enum class TileSize(val cols: Int, val rows: Int) {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    LARGE(3, 3),
    WIDE_SMALL(2, 1),
    TALL(1, 2),
    WIDE_MEDIUM(3, 2),
    TALL_MEDIUM(2, 3),
    XLARGE(4, 4),
    BANNER(4, 1),
    COLUMN(1, 4),
    ;

    /**
     * The next size in the resize cycle (FR-3.4). A tap on a tile's resize control
     * walks this order; medium is the default landing size, so the cycle starts and
     * returns there.
     *
     * When [largeAllowed] is false (the default) the cycle is medium → small → wide
     * → medium. When true ([AppCategories.allowsLargeTile], now unconditional — any
     * app tile on any grid density) wide steps up to [LARGE] before wrapping back to
     * medium: medium → small → wide → large → medium.
     */
    fun next(largeAllowed: Boolean = false): TileSize = when (this) {
        MEDIUM -> SMALL
        SMALL -> WIDE
        WIDE -> if (largeAllowed) LARGE else MEDIUM
        LARGE -> MEDIUM
        // Only reached via drag-to-resize, never by this cycle itself (see the
        // class doc comment). Tapping resize while at one of these newer
        // presets folds back into the original four-size cycle at MEDIUM —
        // the cycle's own documented "always starts and returns" landing
        // size — rather than extending the tap cycle to eleven stops.
        WIDE_SMALL, TALL, WIDE_MEDIUM, TALL_MEDIUM, XLARGE, BANNER, COLUMN -> MEDIUM
    }

    val area get() = cols * rows

    fun nextIsLarger(largeAllowed: Boolean = false) = next(largeAllowed).area > area

    /**
     * Resize cycle for a folder child, which is deliberately tighter than a
     * top-level tile's: when [largeAllowed] it gets the full [next] cycle
     * (small→medium→wide→large); otherwise it keeps a plain small↔medium toggle
     * rather than [next]'s medium→small→wide, since a WIDE child would crowd the
     * folder overlay's grid.
     */
    fun nextForFolderChild(largeAllowed: Boolean): TileSize =
        if (largeAllowed) next(largeAllowed = true) else if (this == SMALL) MEDIUM else SMALL

    /**
     * Whether this size is roomy enough to be a widget-stack member — every
     * size except the four smallest/thinnest presets, where a single live
     * tile face reads too cramped to be worth swiping between: [SMALL] (1×1),
     * [WIDE_SMALL] (2×1), [TALL] (1×2), [COLUMN] (1×4). Originally a stack
     * required uniform [WIDE] or [LARGE] members; widened to this broader,
     * still-deliberately-excluded-list rule per user request (see
     * docs/DECISIONS.md).
     */
    val stackable: Boolean
        get() = this != SMALL && this != WIDE_SMALL && this != TALL && this != COLUMN
}
