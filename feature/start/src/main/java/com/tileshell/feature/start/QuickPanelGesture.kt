package com.tileshell.feature.start

import kotlin.math.abs

/**
 * True once a two-finger swipe-**down** (the quick-panel gesture) has travelled
 * far enough, and is more vertical than horizontal, to trigger. Mirrors
 * [isQuickSearchSwipe] with the vertical sign flipped — quick search is
 * two-finger swipe-up, this is swipe-down, so the two can never both fire for
 * the same gesture and there is no ambiguity between them.
 *
 * Was swipe-**up** originally; flipped to swipe-down per explicit user
 * request — the panel already docks to and slides down from the top of the
 * screen (see `QuickPanelOverlay`'s `dockTop`), so a downward swipe pulling it
 * down reads more naturally than the reverse, and frees up swipe-up for quick
 * search's own bottom-anchored redesign.
 */
internal fun isQuickPanelSwipe(avgDy: Float, avgDx: Float, thresholdPx: Float): Boolean =
    avgDy > thresholdPx && avgDy > abs(avgDx)
