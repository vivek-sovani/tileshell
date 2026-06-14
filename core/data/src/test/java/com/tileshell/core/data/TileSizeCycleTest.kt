package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the FR-3.4 resize cycle ([TileSize.next]). */
class TileSizeCycleTest {

    @Test
    fun cyclesMediumSmallWideAndWraps() {
        assertEquals(TileSize.SMALL, TileSize.MEDIUM.next())
        assertEquals(TileSize.WIDE, TileSize.SMALL.next())
        assertEquals(TileSize.MEDIUM, TileSize.WIDE.next()) // wraps back to medium
    }

    @Test
    fun threeStepsReturnToStart() {
        var size = TileSize.MEDIUM
        repeat(3) { size = size.next() }
        assertEquals(TileSize.MEDIUM, size)
    }
}
