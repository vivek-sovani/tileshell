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

    @Test
    fun areaMatchesFootprint() {
        assertEquals(1, TileSize.SMALL.area)
        assertEquals(4, TileSize.MEDIUM.area)
        assertEquals(8, TileSize.WIDE.area)
        assertEquals(9, TileSize.LARGE.area)
    }

    @Test
    fun nextIsLargerDefaultCycle() {
        // MEDIUM(4) → SMALL(1): shrink
        assertEquals(false, TileSize.MEDIUM.nextIsLarger())
        // SMALL(1) → WIDE(8): grow
        assertEquals(true, TileSize.SMALL.nextIsLarger())
        // WIDE(8) → MEDIUM(4): shrink
        assertEquals(false, TileSize.WIDE.nextIsLarger())
    }

    @Test
    fun nextIsLargerWithLargeAllowed() {
        // WIDE(8) → LARGE(9): grow when large is allowed
        assertEquals(true, TileSize.WIDE.nextIsLarger(largeAllowed = true))
        // LARGE(9) → MEDIUM(4): shrink
        assertEquals(false, TileSize.LARGE.nextIsLarger(largeAllowed = true))
    }

    @Test
    fun folderChildTogglesSmallMediumOnFourColumns() {
        // A 4-column grid keeps folder children to a tight two-size toggle,
        // unlike a top-level tile's medium→small→wide cycle.
        assertEquals(TileSize.MEDIUM, TileSize.SMALL.nextForFolderChild(largeAllowed = false))
        assertEquals(TileSize.SMALL, TileSize.MEDIUM.nextForFolderChild(largeAllowed = false))
        // Anything else (shouldn't normally occur at 4 columns) degrades to small.
        assertEquals(TileSize.SMALL, TileSize.WIDE.nextForFolderChild(largeAllowed = false))
    }

    @Test
    fun folderChildGetsFullCycleOnFiveOrSixColumns() {
        var size = TileSize.MEDIUM
        val seen = mutableListOf(size)
        repeat(4) { size = size.nextForFolderChild(largeAllowed = true); seen.add(size) }
        assertEquals(
            listOf(TileSize.MEDIUM, TileSize.SMALL, TileSize.WIDE, TileSize.LARGE, TileSize.MEDIUM),
            seen,
        )
    }
}
