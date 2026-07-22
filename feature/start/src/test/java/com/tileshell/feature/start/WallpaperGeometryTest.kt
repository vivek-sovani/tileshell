package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [wallpaperCropGeometry] — the shared crop/zoom/align math. */
class WallpaperGeometryTest {

    @Test
    fun `at zoom 1 the tight axis has zero overflow regardless of alignment`() {
        // A 1000x1000 square photo cover-fit into a 2000x1000 wide box: the
        // chosen cover scale (2) is driven by width (2000/1000), so width is
        // the tight axis (dstWidth exactly matches box width, zero slack).
        val top = wallpaperCropGeometry(1000f, 1000f, 2000f, 1000f, alignX = 0f, alignY = 0.5f, zoom = 1f)
        val bottom = wallpaperCropGeometry(1000f, 1000f, 2000f, 1000f, alignX = 1f, alignY = 0.5f, zoom = 1f)
        assertEquals(top.left, bottom.left, 0.001f)
        assertEquals(0f, top.left, 0.001f)
    }

    @Test
    fun `zooming in creates overflow on the previously-tight axis`() {
        val geo = wallpaperCropGeometry(1000f, 1000f, 1000f, 2000f, alignX = 1f, alignY = 0.5f, zoom = 1.5f)
        // scale = coverScale(2) * zoom(1.5) = 3; dstWidth = 3000, overflow = 2000.
        assertEquals(-2000f, geo.left, 0.001f)
    }

    @Test
    fun `alignY 0 shows the top, alignY 1 shows the bottom, once zoomed`() {
        // A 1000x1000 square photo in a 2000x1000 wide box: width is tight
        // (drives the cover scale), so height is the overflow axis — the one
        // zooming in has to open up before alignY can do anything.
        // coverScale=2, scale=2*zoom(2)=4, dstHeight=4000, slackY=3000.
        val topAligned = wallpaperCropGeometry(1000f, 1000f, 2000f, 1000f, alignX = 0.5f, alignY = 0f, zoom = 2f)
        val bottomAligned = wallpaperCropGeometry(1000f, 1000f, 2000f, 1000f, alignX = 0.5f, alignY = 1f, zoom = 2f)
        assertEquals(0f, topAligned.top, 0.001f)
        assertEquals(-3000f, bottomAligned.top, 0.001f)
        assert(topAligned.top != bottomAligned.top)
    }

    @Test
    fun `alignment is coerced into 0 to 1`() {
        val geo = wallpaperCropGeometry(1000f, 1000f, 1000f, 2000f, alignX = 1.5f, alignY = -0.5f, zoom = 2f)
        val clamped = wallpaperCropGeometry(1000f, 1000f, 1000f, 2000f, alignX = 1f, alignY = 0f, zoom = 2f)
        assertEquals(clamped.left, geo.left, 0.001f)
        assertEquals(clamped.top, geo.top, 0.001f)
    }

    @Test
    fun `degenerate dimensions fall back to an unscaled centred box`() {
        val geo = wallpaperCropGeometry(0f, 0f, 1000f, 2000f, alignX = 0.3f, alignY = 0.7f, zoom = 2f)
        assertEquals(1f, geo.scale, 0.001f)
        assertEquals(1000f, geo.dstWidth, 0.001f)
        assertEquals(2000f, geo.dstHeight, 0.001f)
        assertEquals(0f, geo.left, 0.001f)
        assertEquals(0f, geo.top, 0.001f)
    }
}
