package com.tileshell.feature.start

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileQuickMenuLayoutTest {
    private val item = 50f
    private val gap = 3f
    private val margin = 10f

    private fun slots(tile: Rect, count: Int, w: Float = 400f, h: Float = 800f) =
        quickMenuSlots(tile, count, w, h, item, gap, margin)

    @Test
    fun `a medium tile gets a side column and a row under it`() {
        val tile = Rect(20f, 200f, 190f, 370f)
        val s = slots(tile, 5)
        assertEquals(5, s.size)
        // Side column on the right, from the tile's top: three fit its 170px height.
        assertEquals(Offset(193f, 200f), s[0])
        assertEquals(Offset(193f, 253f), s[1])
        assertEquals(Offset(193f, 306f), s[2])
        // The rest in a row under the tile, starting at its left edge.
        assertEquals(Offset(20f, 373f), s[3])
        assertEquals(Offset(73f, 373f), s[4])
    }

    @Test
    fun `no room on the right puts the column on the left`() {
        val tile = Rect(220f, 200f, 380f, 360f)
        val s = slots(tile, 2)
        assertEquals(167f, s[0].x, 0.01f)
    }

    @Test
    fun `a tile at the bottom puts the row above it`() {
        val tile = Rect(20f, 640f, 190f, 780f)
        val s = slots(tile, 6)
        val rowY = s.drop(3).map { it.y }.distinct().single()
        assertTrue(rowY + item < tile.top)
    }

    @Test
    fun `everything stays on the screen`() {
        val tile = Rect(300f, 700f, 395f, 795f)
        slots(tile, 6).forEach {
            assertTrue(it.x >= margin && it.x + item <= 400f - margin)
            assertTrue(it.y >= margin && it.y + item <= 800f - margin)
        }
    }

    @Test
    fun `a full-width tile has no side column`() {
        val tile = Rect(5f, 200f, 395f, 360f)
        val s = slots(tile, 3)
        assertEquals(1, s.map { it.y }.distinct().size)
    }
}
