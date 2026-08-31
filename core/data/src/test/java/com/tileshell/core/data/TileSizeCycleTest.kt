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

    @Test
    fun requireTallCycleSkipsSmallEntirely() {
        // medium -> wide -> medium, never touching a 1-row size.
        assertEquals(TileSize.WIDE, TileSize.MEDIUM.next(requireTallCycle = true))
        assertEquals(TileSize.MEDIUM, TileSize.WIDE.next(requireTallCycle = true))
    }

    @Test
    fun requireTallCycleStillOffersLargeWhenAllowed() {
        assertEquals(TileSize.WIDE, TileSize.MEDIUM.next(largeAllowed = true, requireTallCycle = true))
        assertEquals(TileSize.LARGE, TileSize.WIDE.next(largeAllowed = true, requireTallCycle = true))
        assertEquals(TileSize.MEDIUM, TileSize.LARGE.next(largeAllowed = true, requireTallCycle = true))
    }

    @Test
    fun requireTallCycleRecoversFromAnyOneRowPresetToWide() {
        // Should never actually happen (these tiles never reach a 1-row size),
        // but a stray SMALL/WIDE_SMALL/BANNER value still recovers to a 2+ row
        // size instead of reintroducing SMALL.
        assertEquals(TileSize.WIDE, TileSize.SMALL.next(requireTallCycle = true))
        assertEquals(TileSize.WIDE, TileSize.WIDE_SMALL.next(requireTallCycle = true))
        assertEquals(TileSize.WIDE, TileSize.BANNER.next(requireTallCycle = true))
    }

    @Test
    fun requireTallCycleFolderChildNeverReachesSmall() {
        assertEquals(TileSize.MEDIUM, TileSize.MEDIUM.nextForFolderChild(largeAllowed = false, requireTallCycle = true))
        assertEquals(
            TileSize.WIDE,
            TileSize.MEDIUM.nextForFolderChild(largeAllowed = true, requireTallCycle = true),
        )
    }
}
