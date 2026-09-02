package com.tileshell.feature.livetiles.widget

/**
 * Below this width a widget in this batch switches to its `_compact` layout
 * (no icon, fewer lines) — pure so the size-bucket choice is unit-testable
 * without a real [android.appwidget.AppWidgetManager]/host. Shared by every
 * widget kind's refresh worker, not just weather's.
 */
private const val COMPACT_WIDTH_THRESHOLD_DP = 180

/** True when the host has given the widget less than [COMPACT_WIDTH_THRESHOLD_DP] to work with. */
fun isCompactWidget(minWidthDp: Int): Boolean = minWidthDp < COMPACT_WIDTH_THRESHOLD_DP

/**
 * How many list rows a resizable list-style widget (tasks, notes) shows for a
 * given resized height — user-reported: the tasks widget stayed capped at 3
 * rows no matter how tall it was dragged, wasting the extra space a resize is
 * meant to reveal. Pure/threshold-based, not exact per-dp arithmetic, since a
 * "cell" is a different real dp height on every launcher; the ceiling of 6
 * matches the Start tile's own LARGE-size row cap ([maxPreviewFor] in
 * `TasksTile.kt`), and both the tasks and notes widget layouts must declare
 * at least this many row view slots for [maxRows] rows to ever actually show.
 */
fun listWidgetRowsForHeight(minHeightDp: Int): Int = when {
    minHeightDp < 150 -> 3
    minHeightDp < 200 -> 4
    minHeightDp < 260 -> 5
    else -> 6
}

/**
 * Perceived-luminance check on a resolved accent colour, deciding whether the
 * widget's text should be dark (light accent) or white (dark/mid accent) —
 * same rationale as [com.tileshell.core.design.Glass.faceTextColor] elsewhere
 * in the app, reimplemented on a plain ARGB int since RemoteViews has no
 * Compose [androidx.compose.ui.graphics.Color] on the other side of the push.
 */
fun isLightAccent(argb: Int): Boolean {
    val r = (argb shr 16 and 0xFF) / 255.0
    val g = (argb shr 8 and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return luminance > 0.6
}
