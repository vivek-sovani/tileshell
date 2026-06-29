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

    @Test
    fun cyclesThroughLargeWhenAllowed() {
        assertEquals(TileSize.SMALL, TileSize.MEDIUM.next(largeAllowed = true))
        assertEquals(TileSize.WIDE, TileSize.SMALL.next(largeAllowed = true))
        assertEquals(TileSize.LARGE, TileSize.WIDE.next(largeAllowed = true)) // wide steps up
        assertEquals(TileSize.MEDIUM, TileSize.LARGE.next(largeAllowed = true)) // wraps to medium
    }

    @Test
    fun fourStepsReturnToStartWhenLargeAllowed() {
        var size = TileSize.MEDIUM
        repeat(4) { size = size.next(largeAllowed = true) }
        assertEquals(TileSize.MEDIUM, size)
    }

    @Test
    fun largeShrinksToMediumWhenNotAllowed() {
        // A tile already large (e.g. grid dropped to 4 cols mid-cycle) returns to
        // medium rather than getting stuck.
        assertEquals(TileSize.MEDIUM, TileSize.LARGE.next(largeAllowed = false))
    }
}
