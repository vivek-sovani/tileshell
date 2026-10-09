package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileControlBarTest {
    @Test
    fun `buttons depend on whether the tile is a folder`() {
        assertEquals(listOf(BarAction.UNPIN, BarAction.COLOUR, BarAction.SIZE), barActionsFor(false))
        assertEquals(listOf(BarAction.OPEN_FOLDER, BarAction.COLOUR, BarAction.SIZE), barActionsFor(true))
    }

    @Test
    fun `the bar sits above the tile, centred`() {
        val r = controlBarRect(tileLeft = 100f, tileTop = 300f, tileRight = 190f, tileBottom = 390f, gridWidth = 400f, barWidth = 180f, barHeight = 34f, gap = 6f)
        assertEquals(260f, r.top, 0.01f)
        assertEquals(145f - 90f, r.left, 0.01f)
    }

    @Test
    fun `the bar stays inside the grid`() {
        val atLeft = controlBarRect(0f, 300f, 90f, 390f, 400f, 180f, 34f, 6f)
        assertEquals(0f, atLeft.left, 0.01f)
        val atRight = controlBarRect(310f, 300f, 400f, 390f, 400f, 180f, 34f, 6f)
        assertEquals(220f, atRight.left, 0.01f)
        assertEquals(400f, atRight.right, 0.01f)
    }

    @Test
    fun `a first row tile gets the bar below`() {
        val r = controlBarRect(100f, 0f, 190f, 90f, 400f, 180f, 34f, 6f)
        assertEquals(96f, r.top, 0.01f)
        assertEquals(130f, r.bottom, 0.01f)
    }

    @Test
    fun `a point finds its button`() {
        val r = BarRect(100f, 0f, 280f, 34f)
        assertEquals(0, barButtonIndexAt(110f, r, 3))
        assertEquals(1, barButtonIndexAt(190f, r, 3))
        assertEquals(2, barButtonIndexAt(279f, r, 3))
        assertTrue(r.contains(100f, 0f))
        assertFalse(r.contains(280f, 10f))
    }
}
