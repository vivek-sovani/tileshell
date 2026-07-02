package com.tileshell.feature.start

import kotlin.math.abs

/**
 * True once a two-finger swipe-down (the quick-search gesture) has travelled far
 * enough, and is more vertical than horizontal, to trigger. [avgDy]/[avgDx] are
 * the average of the two pointers' travel since they both went down; requiring
 * vertical dominance keeps a two-finger horizontal pan (e.g. panning a wide
 * photo tile) from false-triggering.
 */
internal fun isQuickSearchSwipe(avgDy: Float, avgDx: Float, thresholdPx: Float): Boolean =
    avgDy > thresholdPx && avgDy > abs(avgDx)
