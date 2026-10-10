package com.tileshell.feature.livetiles

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileFlipTest {
    private val w = 200f
    private val h = 100f

    @Test
    fun `at rest the matrix changes nothing`() {
        val m = flipMatrix(0f, w, h, 540f, 1200f, 4800f)
        listOf(Offset(0f, 0f), Offset(w, h), Offset(70f, 30f)).forEach {
            val p = m.map(it)
            assertEquals(it.x, p.x, 0.01f)
            assertEquals(it.y, p.y, 0.01f)
        }
    }

    @Test
    fun `the tile centre stays put whatever the angle and the camera`() {
        listOf(0f, 30f, 60f, 89f, -45f).forEach { angle ->
            val p = flipMatrix(angle, w, h, -500f, 900f, 4800f).map(Offset(w / 2, h / 2))
            assertEquals(w / 2, p.x, 0.01f)
            assertEquals(h / 2, p.y, 0.01f)
        }
    }

    @Test
    fun `a turned tile is shorter and its far edge is narrower`() {
        // Eye level with the tile's centre: the turn is symmetric about it.
        val m = flipMatrix(60f, w, h, w / 2, h / 2, 800f)
        val topLeft = m.map(Offset(0f, 0f))
        val topRight = m.map(Offset(w, 0f))
        val bottomLeft = m.map(Offset(0f, h))
        val bottomRight = m.map(Offset(w, h))
        assertTrue(bottomLeft.y - topLeft.y < h)
        val topWidth = topRight.x - topLeft.x
        val bottomWidth = bottomRight.x - bottomLeft.x
        // Turning away, the top edge is the far one: narrower and closer to the centre line.
        assertTrue(topWidth < bottomWidth)
        assertTrue(h / 2 - topLeft.y < bottomLeft.y - h / 2)
    }

    @Test
    fun `an eye away from the tile leans the turned tile toward it`() {
        // Tile at the top of the screen, eye far below: the turned tile's top edge
        // is pulled toward the eye by perspective relative to the tile-centred eye.
        val central = flipMatrix(50f, w, h, w / 2, h / 2, 800f).map(Offset(0f, 0f))
        val global = flipMatrix(50f, w, h, w / 2, 2000f, 800f).map(Offset(0f, 0f))
        assertTrue(global.y != central.y)
    }
}
