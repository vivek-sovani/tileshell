package com.tileshell.feature.livetiles

import androidx.compose.runtime.compositionLocalOf

/**
 * How much the Start screen has enlarged the live face being drawn (1 = not at all): a tile bigger than the 2x2 its
 * text was sized for is drawn at a larger density so the text fills it. A face whose layout is already built for the
 * whole tile (the panchang month grid) divides this back out.
 */
val LocalLiveFaceScale = compositionLocalOf { 1f }
