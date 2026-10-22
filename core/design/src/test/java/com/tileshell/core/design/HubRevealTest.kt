package com.tileshell.core.design

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HubRevealTest {
    private val origin = HubOriginData(Rect(100f, 300f, 300f, 500f), Color.Blue, 12f)
    private val w = 1000f
    private val h = 2000f

    @Test fun startsAsTheTile() {
        val f = revealFrame(origin, w, h, 0f)
        // On screen the clip is exactly the tile's rectangle.
        val left = f.translation.x + f.scale * f.clip.left
        val top = f.translation.y + f.scale * f.clip.top
        assertEquals(100f, left, 0.01f)
        assertEquals(300f, top, 0.01f)
        assertEquals(200f, f.scale * f.clip.width, 0.01f)
        assertEquals(200f, f.scale * f.clip.height, 0.01f)
        assertEquals(12f, f.corner * f.scale, 0.01f)
    }

    @Test fun endsAsTheFullPanel() {
        val f = revealFrame(origin, w, h, 1f)
        assertEquals(1f, f.scale, 0.0001f)
        assertEquals(0f, f.translation.x, 0.01f)
        assertEquals(0f, f.translation.y, 0.01f)
        assertEquals(w, f.clip.width, 0.01f)
        assertEquals(h, f.clip.height, 0.01f)
        assertEquals(0f, f.corner, 0.0001f)
    }

    @Test fun clipGrowsMonotonically() {
        var last = 0f
        for (i in 0..10) {
            val f = revealFrame(origin, w, h, i / 10f)
            val area = f.scale * f.clip.width * f.scale * f.clip.height
            assert(area >= last - 0.01f)
            last = area
        }
    }

    @Test fun originIsTakenOnce() {
        HubOrigin.set(Rect(0f, 0f, 10f, 10f), Color.Red, 0f)
        assertEquals(Color.Red, HubOrigin.take()?.color)
        assertNull(HubOrigin.take())
    }

    @Test fun wallpaperPlateReplacesTheTileColour() {
        HubOrigin.wallpaperPlate = Color.Green
        HubOrigin.set(Rect(0f, 0f, 10f, 10f), Color.Red, 0f)
        assertEquals(Color.Green, HubOrigin.take()?.color)
        HubOrigin.wallpaperPlate = Color.Unspecified
        HubOrigin.set(Rect(0f, 0f, 10f, 10f), Color.Red, 0f)
        assertEquals(Color.Red, HubOrigin.take()?.color)
    }
}
