package com.tileshell.feature.start

import kotlin.math.max

/** Where and how large to draw a cover-fit, zoomed, aligned photo within a box. */
data class WallpaperCrop(val scale: Float, val dstWidth: Float, val dstHeight: Float, val left: Float, val top: Float)

/**
 * Computes the draw geometry for a wallpaper photo cropped to fully cover a
 * [boxWidth]×[boxHeight] box, zoomed by [zoom] on top of the base cover-fit
 * scale, and positioned so [alignX]/[alignY] (each 0..1) pick where within
 * the resulting overflow the crop sits (0 = left/top edge visible, 0.5 =
 * centred, 1 = right/bottom edge visible).
 *
 * The critical bit: [zoom] is folded into the *same* scale used to compute
 * the pannable overflow (`scale = coverScale * zoom`), not applied as a
 * separate transform afterward. At `zoom = 1` a cover-fit photo has zero
 * overflow on whichever axis was the "tight" fit for that photo/box aspect
 * ratio — alignX/Y on that axis has nowhere to move, so panning to reveal
 * just the top or just the bottom of a photo is geometrically impossible
 * without zooming in a little first. Previously, zoom was applied as an
 * outer transform computed independently of the overflow/alignment math, so
 * even pinch-zooming in didn't actually create pan room on the tight axis —
 * see docs/DECISIONS.md.
 */
fun wallpaperCropGeometry(
    imageWidth: Float,
    imageHeight: Float,
    boxWidth: Float,
    boxHeight: Float,
    alignX: Float,
    alignY: Float,
    zoom: Float,
): WallpaperCrop {
    if (imageWidth <= 0f || imageHeight <= 0f || boxWidth <= 0f || boxHeight <= 0f) {
        return WallpaperCrop(1f, boxWidth, boxHeight, 0f, 0f)
    }
    val coverScale = max(boxWidth / imageWidth, boxHeight / imageHeight)
    val scale = coverScale * zoom.coerceAtLeast(1f)
    val dstWidth = imageWidth * scale
    val dstHeight = imageHeight * scale
    val slackX = max(0f, dstWidth - boxWidth)
    val slackY = max(0f, dstHeight - boxHeight)
    return WallpaperCrop(
        scale = scale,
        dstWidth = dstWidth,
        dstHeight = dstHeight,
        left = -alignX.coerceIn(0f, 1f) * slackX,
        top = -alignY.coerceIn(0f, 1f) * slackY,
    )
}
