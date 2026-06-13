package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the FR-3.4 resize cycle ([TileSize.next]). */
class TileSizeCycleTest {

    @Test
    fun cyclesSmallMediumWideLargeAndWraps() {
        assertEquals(TileSize.MEDIUM, TileSize.SMALL.next())
        assertEquals(TileSize.WIDE, TileSize.MEDIUM.next())
        assertEquals(TileSize.LARGE, TileSize.WIDE.next())
        assertEquals(TileSize.SMALL, TileSize.LARGE.next()) // wraps
    }

    @Test
    fun fourStepsReturnToStart() {
        var size = TileSize.SMALL
        repeat(4) { size = size.next() }
        assertEquals(TileSize.SMALL, size)
    }
}
