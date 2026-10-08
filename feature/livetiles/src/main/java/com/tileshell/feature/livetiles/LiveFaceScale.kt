package com.tileshell.feature.livetiles

import androidx.compose.runtime.compositionLocalOf

/**
 * How much the Start screen has enlarged the live face being drawn (1 = not at all): a tile bigger than the 2x2 its
 * text was sized for is drawn at a larger density so the text fills it. A face whose layout is already built for the
 * whole tile (the panchang month grid) divides this back out.
 */
val LocalLiveFaceScale = compositionLocalOf { 1f }

/** Draws [content] at the tile's own size again, undoing the enlargement for a face that already sizes itself to the tile. */
@androidx.compose.runtime.Composable
fun CancelLiveFaceScale(keep: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
    val k = (LocalLiveFaceScale.current / keep).coerceAtLeast(1f)
    if (k == 1f) {
        content()
        return
    }
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density / k, d.fontScale),
        LocalLiveFaceScale provides (LocalLiveFaceScale.current / k),
    ) { content() }
}
