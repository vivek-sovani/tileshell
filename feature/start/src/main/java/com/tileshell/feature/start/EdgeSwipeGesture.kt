package com.tileshell.feature.start

import kotlin.math.abs

/** Which half of the screen a touch started in, per [edgeZoneFor]. */
internal enum class EdgeZone { LEFT, RIGHT, NONE }

/**
 * Classifies where a touch went down relative to the screen's horizontal
 * midpoint, for the single-finger edge-swipe-down gesture (left half →
 * system notification shade, right half → system quick settings). The whole
 * left/right half counts, not just a thin strip at the physical edge — a
 * user-requested widening after the original 32dp-strip version proved too
 * narrow to hit reliably.
 */
internal fun edgeZoneFor(startX: Float, screenWidthPx: Float): EdgeZone = when {
    screenWidthPx <= 0f -> EdgeZone.NONE
    startX < screenWidthPx / 2f -> EdgeZone.LEFT
    else -> EdgeZone.RIGHT
}

/**
 * True once a single-finger swipe has travelled far enough, and is more
 * vertical than horizontal, to trigger. Mirrors [isQuickSearchSwipe]'s shape
 * but for one pointer instead of two averaged ones — kept as its own function
 * since it recognizes a different gesture.
 */
internal fun isEdgeSwipeDown(dy: Float, dx: Float, thresholdPx: Float): Boolean =
    dy > thresholdPx && dy > abs(dx)
