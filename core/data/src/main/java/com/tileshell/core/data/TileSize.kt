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
     * Whether this size is roomy enough to be a widget-stack member: more
     * than one column, i.e. not [SMALL] (1×1), [TALL] (1×2), or [COLUMN]
     * (1×4) — a single live tile face squeezed into a one-cell-thin *column*
     * reads too cramped to be worth swiping between. [WIDE_SMALL] (2×1) and
     * [BANNER] (4×1) — single-row but multi-column — are allowed per user
     * request: a wide single-row face has plenty of horizontal room even at
     * one row tall. Originally a stack required uniform [WIDE] or [LARGE]
     * members; widened to this broader "any roomy-enough size" rule per user
     * request (see docs/DECISIONS.md).
     */
    val stackable: Boolean
        get() = cols > 1

    /**
     * True for [TALL] and [COLUMN] — the same 1-column width as [SMALL] but with
     * extra row height. Live-face composables (clock/weather/calendar/notification)
     * branch on this to lay their text out stacked and centred for a ~90dp-wide
     * column, spread across whatever height the tile has, instead of the wider
     * MEDIUM+ layout that clips at 1 column. See docs/DECISIONS.md "Narrow live
     * tiles show their data stacked vertically."
     */
    val narrowLive: Boolean
        get() = cols == 1 && this != SMALL

    /**
     * True for [WIDE_SMALL] and [BANNER] — a single grid row tall but more
     * than one column wide, the row-axis counterpart of [narrowLive]'s
     * column-axis squeeze. A live face's normal stacked text (label/value/
     * caption, each on its own line) needs roughly two rows' worth of height
     * to avoid clipping the last line; live-face composables branch on this
     * to shrink fonts/padding to fit comfortably within one row instead.
     */
    val shortLive: Boolean
        get() = rows == 1 && cols > 1
}
