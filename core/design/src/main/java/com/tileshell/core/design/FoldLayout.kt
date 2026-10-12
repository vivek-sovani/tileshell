package com.tileshell.core.design

import androidx.compose.runtime.compositionLocalOf

/**
 * A hinge (or fold) that splits the window into two screens, in window pixels
 * (user-requested: dual-screen devices such as the Surface Duo and Galaxy Z
 * Fold). Only a hinge that really separates the window is ever provided:
 * a hinge that occludes part of the display (Duo), or a fold that is half open
 * (a Fold in book or tabletop posture). A flat, unfolded display has none.
 *
 * [vertical] means the hinge line is vertical, so the screens sit left and
 * right of it; a horizontal hinge puts them above and below. [occluding] is
 * true when the hinge hides pixels (the gap between the screens is dead space).
 */
data class Hinge(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val vertical: Boolean,
    val occluding: Boolean,
)

/**
 * The window cut at a hinge along one axis: [first] is the first screen's
 * extent (left or top), [gap] the hinge itself (0 for a fold that hides
 * nothing), [second] the other screen. All in the same unit as the extent it
 * was made from.
 */
data class PanelSplit(val first: Float, val gap: Float, val second: Float) {
    /** The same split in another unit (pixels to dp: pass 1 / density). */
    fun scaled(factor: Float) = PanelSplit(first * factor, gap * factor, second * factor)
}

/**
 * The hinge in the window, or null when the window is not split. Provided once
 * by the activity; [SheetStage] re-provides null to anything it confines to a
 * single screen, so a composable only sees a hinge when it spans it.
 */
val LocalHinge = compositionLocalOf<Hinge?> { null }

/**
 * Cuts a window of [extent] pixels along the axis that [vertical] names, where
 * the window starts [origin] pixels from the window's own origin. Null when the
 * hinge runs the other way, or lies outside the window (a sheet docked to one
 * screen has no hinge inside it).
 */
fun Hinge.splitOf(vertical: Boolean, extent: Float, origin: Float = 0f): PanelSplit? {
    if (this.vertical != vertical) return null
    val start = (if (vertical) left else top) - origin
    val end = (if (vertical) right else bottom) - origin
    val first = start
    val gap = (end - start).coerceAtLeast(0f)
    val second = extent - end
    if (first < MIN_SCREEN_PX || second < MIN_SCREEN_PX) return null
    return PanelSplit(first, gap, second)
}

private const val MIN_SCREEN_PX = 1f

/** Windows this wide (dp) get Start and the glance page side by side, whichever way the phone is held. */
const val TWO_PANEL_MIN_WIDTH_DP = 600

/**
 * Whether Start and the glance page show side by side. Landscape always did
 * (phones included, unchanged); now any window at least [TWO_PANEL_MIN_WIDTH_DP]
 * wide does too, so an unfolded Fold, a tablet held upright, or a Duo spanned
 * across both screens does not stretch a portrait layout over a wide window.
 */
fun useTwoPanel(landscape: Boolean, widthDp: Int): Boolean = landscape || widthDp >= TWO_PANEL_MIN_WIDTH_DP

/** One section's width and the space between sections in a hub panorama (dp). */
data class PanoramaGeometry(val sectionDp: Float, val spacingDp: Float)

/** How much of the next section shows at the right edge of a single, unsplit screen. */
const val PANORAMA_PEEK_DP = 56f

/** Widest a section gets on an unsplit screen. */
const val PANORAMA_MAX_SECTION_DP = 400f

/** A section on a screen of a split window is at most about this wide before a second one is added beside it. */
private const val SPLIT_SECTION_MAX_DP = 700f

/**
 * Section width and spacing for a hub panorama [widthDp] wide. On an unsplit
 * window sections are a phone's width with the next one peeking in. On a
 * window split at a hinge they are sized so a whole number of them fills each
 * screen and the spacing equals the hinge, so every section lands on one
 * screen as the pager moves page by page and none sits under the hinge. There
 * is no peek then: the other screen shows the next section.
 */
fun panoramaGeometry(widthDp: Float, split: PanelSplit?): PanoramaGeometry {
    if (split == null) {
        return PanoramaGeometry(minOf(widthDp - PANORAMA_PEEK_DP, PANORAMA_MAX_SECTION_DP), 0f)
    }
    val screen = minOf(split.first, split.second)
    val perScreen = kotlin.math.ceil(screen / SPLIT_SECTION_MAX_DP).toInt().coerceAtLeast(1)
    val section = (screen - split.gap * (perScreen - 1)) / perScreen
    return PanoramaGeometry(section, split.gap)
}
