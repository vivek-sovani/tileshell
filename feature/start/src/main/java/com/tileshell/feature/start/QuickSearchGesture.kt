package com.tileshell.feature.start

import kotlin.math.abs

/**
 * True once a two-finger swipe-**up** (the quick-search gesture) has travelled
 * far enough, and is more vertical than horizontal, to trigger. [avgDy]/[avgDx]
 * are the average of the two pointers' travel since they both went down;
 * requiring vertical dominance keeps a two-finger horizontal pan (e.g. panning
 * a wide photo tile) from false-triggering.
 *
 * Was swipe-**down** originally; flipped to swipe-up per explicit user request
 * so quick search's own "content slides up from the bottom, search bar at the
 * bottom" motion matches an upward swipe, and quick panel (now swipe-down)
 * takes over the direction this used to own. See [isQuickPanelSwipe]'s doc for
 * the flip's full rationale.
 */
internal fun isQuickSearchSwipe(avgDy: Float, avgDx: Float, thresholdPx: Float): Boolean =
    avgDy < -thresholdPx && abs(avgDy) > abs(avgDx)
