package com.tileshell.feature.start

import kotlin.math.abs

/** Which physical screen edge a touch started in, per [edgeZoneFor]. */
internal enum class EdgeZone { LEFT, RIGHT, NONE }

/**
 * Classifies where a touch went down relative to the left/right physical screen
 * edges, for the single-finger edge-swipe-down gesture (left → system
 * notification shade, right → system quick settings). [zonePx] is the width of
 * the edge strip that counts as "the edge" on each side.
 */
internal fun edgeZoneFor(startX: Float, screenWidthPx: Float, zonePx: Float): EdgeZone = when {
    startX <= zonePx -> EdgeZone.LEFT
    startX >= screenWidthPx - zonePx -> EdgeZone.RIGHT
    else -> EdgeZone.NONE
}

/**
 * True once a single-finger swipe starting at a screen edge has travelled far
 * enough, and is more vertical than horizontal, to trigger. Mirrors
 * [isQuickSearchSwipe]'s shape but for one pointer instead of two averaged
 * ones — kept as its own function since it recognizes a different gesture.
 */
internal fun isEdgeSwipeDown(dy: Float, dx: Float, thresholdPx: Float): Boolean =
    dy > thresholdPx && dy > abs(dx)

/**
 * The upward sibling of [isEdgeSwipeDown] — a single-finger swipe up starting
 * at either screen edge opens the in-app Quick Panel, an additional/easier-to-
 * discover path alongside the existing two-finger swipe-up gesture
 * ([isQuickPanelSwipe]). Left and right edges trigger the same action here
 * (unlike the down-swipe, which differs by edge), so callers don't need to
 * distinguish [EdgeZone.LEFT] from [EdgeZone.RIGHT] for this direction.
 */
internal fun isEdgeSwipeUp(dy: Float, dx: Float, thresholdPx: Float): Boolean =
    dy < -thresholdPx && abs(dy) > abs(dx)
