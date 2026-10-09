package com.tileshell.feature.start

/** Which screen edge a finger at [x] rests in: -1 left, 1 right, 0 neither. [zone] is the edge band's width. Pure. */
internal fun edgeDirectionOf(x: Float, width: Float, zone: Float): Int = when {
    x < zone -> -1
    x > width - zone -> 1
    else -> 0
}

/**
 * Whether a tile held by a finger that has rested in an edge band since [sinceMs] is now carried to the next page:
 * only after [dwellMs], so brushing past the edge while aiming for the last column never does it. Pure.
 */
internal fun carriesToNextPage(direction: Int, sinceMs: Long, nowMs: Long, dwellMs: Long): Boolean =
    direction != 0 && nowMs - sinceMs >= dwellMs

/**
 * Size of a selected tile's corner button zones (unpin, colour, resize): [maxZone], but never more than 27% of the
 * tile's shorter side, so on a small tile the three corners leave most of it free for grabbing and dragging. Pure.
 */
internal fun cornerZoneSize(tileWidth: Float, tileHeight: Float, maxZone: Float): Float =
    minOf(maxZone, minOf(tileWidth, tileHeight) * 0.27f)
