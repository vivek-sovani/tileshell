package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyGeometryTest {

    // 1080 px wide (after side padding), 6 px gaps → 1 unit = (1080 − 54) / 10 = 102.6.
    private val boxes = KeyGeometry.layout(KeyboardLayouts.letters, 1080f, keyHeight = 130f, rowGap = 20f, keyGap = 6f)

    @Test
    fun `top row is ten one-unit keys edge to edge`() {
        val top = boxes[0]
        assertEquals(10, top.size)
        assertEquals(0f, top.first().left, 0.01f)
        assertEquals(1080f, top.last().right, 0.01f)
        assertEquals(102.6f, top[0].width, 0.01f)
    }

    @Test
    fun `row two is inset by half a unit and half a gap`() {
        assertEquals((102.6f + 6f) / 2, boxes[1].first().left, 0.01f)
        assertEquals(102.6f, boxes[1][0].width, 0.01f)
    }

    @Test
    fun `rows stack with the row gap`() {
        assertEquals(150f, boxes[1][0].top, 0.01f)
        assertEquals(450f, boxes[3][0].top, 0.01f)
    }

    @Test
    fun `hits the key under the finger, or the nearest from a gap`() {
        // Centre of "q" and of "s".
        assertEquals(0 to 0, KeyGeometry.hit(boxes, 50f, 65f))
        assertEquals(1 to 1, KeyGeometry.hit(boxes, boxes[1][1].centerX, boxes[1][1].centerY))
        // In the gap between q and w, nearer q.
        assertEquals(0 to 0, KeyGeometry.hit(boxes, 103.5f, 65f))
        // Between rows: the nearer row.
        assertEquals(0, KeyGeometry.hit(boxes, 50f, 135f)!!.first)
    }

    @Test
    fun `the layout with the globe still fills the width`() {
        val withGlobe = KeyGeometry.layout(KeyboardLayouts.rowsFor(KeyboardLayer.LETTERS, true), 1080f, 130f, 20f, 6f)
        assertEquals(1080f, withGlobe[3].last().right, 0.01f)
        assertEquals(7, withGlobe[3].size)
    }
}
