package com.tileshell.feature.start

import kotlin.math.abs

/**
 * True once a two-finger swipe-**up** (the quick-panel gesture) has travelled far
 * enough, and is more vertical than horizontal, to trigger. Mirrors
 * [isQuickSearchSwipe] with the vertical sign flipped — quick search is
 * two-finger swipe-down, this is swipe-up, so the two can never both fire for
 * the same gesture and there is no ambiguity between them.
 */
internal fun isQuickPanelSwipe(avgDy: Float, avgDx: Float, thresholdPx: Float): Boolean =
    avgDy < -thresholdPx && abs(avgDy) > abs(avgDx)
