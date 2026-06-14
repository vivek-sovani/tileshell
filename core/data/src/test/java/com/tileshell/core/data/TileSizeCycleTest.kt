package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the FR-3.4 resize cycle ([TileSize.next]). */
class TileSizeCycleTest {

    @Test
    fun cyclesSmallMediumWideAndWraps() {
        assertEquals(TileSize.MEDIUM, TileSize.SMALL.next())
        assertEquals(TileSize.WIDE, TileSize.MEDIUM.next())
        assertEquals(TileSize.SMALL, TileSize.WIDE.next()) // wraps (large dropped)
    }

    @Test
    fun threeStepsReturnToStart() {
        var size = TileSize.SMALL
        repeat(3) { size = size.next() }
        assertEquals(TileSize.SMALL, size)
    }
}
