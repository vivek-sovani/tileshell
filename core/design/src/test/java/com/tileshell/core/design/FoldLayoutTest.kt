package com.tileshell.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldLayoutTest {
    // A Duo-like hinge: 1800 px wide window, 84 px hinge in the middle.
    private val duo = Hinge(left = 858f, top = 0f, right = 942f, bottom = 1350f, vertical = true, occluding = true)

    @Test fun `vertical hinge splits a window into two screens and the hinge`() {
        val split = duo.splitOf(vertical = true, extent = 1800f)!!
        assertEquals(858f, split.first, 0.01f)
        assertEquals(84f, split.gap, 0.01f)
        assertEquals(858f, split.second, 0.01f)
    }

    @Test fun `a hinge running the other way does not split this axis`() {
        assertNull(duo.splitOf(vertical = false, extent = 1350f))
    }

    @Test fun `a hinge outside the window gives no split`() {
        // A sheet docked to the right screen starts at the second screen: the hinge is behind it.
        assertNull(duo.splitOf(vertical = true, extent = 858f, origin = 942f))
    }

    @Test fun `a window that starts inside the first screen sees the hinge nearer`() {
        val split = duo.splitOf(vertical = true, extent = 1000f, origin = 100f)!!
        assertEquals(758f, split.first, 0.01f)
        assertEquals(84f, split.gap, 0.01f)
        assertEquals(158f, split.second, 0.01f)
    }

    @Test fun `a half open fold has no gap`() {
        val fold = Hinge(1100f, 0f, 1100f, 1800f, vertical = true, occluding = false)
        val split = fold.splitOf(true, 2200f)!!
        assertEquals(0f, split.gap, 0f)
        assertEquals(1100f, split.second, 0.01f)
    }

    @Test fun `horizontal hinge splits top and bottom`() {
        val h = Hinge(0f, 900f, 1800f, 984f, vertical = false, occluding = true)
        val split = h.splitOf(vertical = false, extent = 1884f)!!
        assertEquals(900f, split.first, 0.01f)
        assertEquals(900f, split.second, 0.01f)
    }

    @Test fun `portrait phones keep one panel and landscape keeps two`() {
        assertFalse(useTwoPanel(landscape = false, widthDp = 393))
        assertTrue(useTwoPanel(landscape = true, widthDp = 393))
        assertTrue(useTwoPanel(landscape = true, widthDp = 851))
    }

    @Test fun `wide windows use two panels even upright`() {
        assertTrue(useTwoPanel(landscape = false, widthDp = 600))
        assertTrue(useTwoPanel(landscape = false, widthDp = 840))
        assertFalse(useTwoPanel(landscape = false, widthDp = 599))
    }

    @Test fun `unsplit panorama keeps a phone width with a peek`() {
        val phone = panoramaGeometry(393f, null)
        assertEquals(337f, phone.sectionDp, 0.01f)
        assertEquals(0f, phone.spacingDp, 0f)
        val wide = panoramaGeometry(900f, null)
        assertEquals(400f, wide.sectionDp, 0.01f)
    }

    @Test fun `split panorama gives each screen one section the spacing of the hinge`() {
        // Duo screens of about 540 dp with a 30 dp hinge.
        val g = panoramaGeometry(1110f, PanelSplit(540f, 30f, 540f))
        assertEquals(540f, g.sectionDp, 0.01f)
        assertEquals(30f, g.spacingDp, 0.01f)
        // The second section starts exactly where the second screen does.
        assertEquals(540f + 30f, g.sectionDp + g.spacingDp, 0.01f)
    }

    @Test fun `wide screens of a split window get two sections each, aligned to the hinge`() {
        val g = panoramaGeometry(1500f, PanelSplit(720f, 60f, 720f))
        // Two sections and one spacing fill a screen exactly...
        assertEquals(720f, g.sectionDp * 2 + g.spacingDp, 0.01f)
        // ...and the third section begins on the second screen.
        assertEquals(720f + 60f, 2 * (g.sectionDp + g.spacingDp), 0.01f)
    }

    @Test fun `a fold with no gap tiles sections edge to edge`() {
        val g = panoramaGeometry(1000f, PanelSplit(500f, 0f, 500f))
        assertEquals(500f, g.sectionDp, 0.01f)
        assertEquals(0f, g.spacingDp, 0f)
    }
}
