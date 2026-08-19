package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [TileModel.Folder.isStack]/[TileModel.Folder.stackSize]. A
 * folder is only ever rendered as a widget stack when BOTH are true: the
 * explicit [TileModel.Folder.showAsStack] toggle is on, AND every member is
 * currently uniformly one [TileSize.stackable] size (both dimensions > 1 —
 * excludes SMALL, WIDE_SMALL, TALL, BANNER, COLUMN). Eligibility
 * ([stackSize]) and the toggle ([showAsStack]) are independent —
 * eligible-but-toggled-off and toggled-on-but-not-eligible both read as a
 * plain folder.
 */
class TileModelStackTest {

    private fun child(size: TileSize) = FolderChild(
        packageName = "pkg.$size",
        activityName = ".Main",
        label = size.name,
        size = size,
    )

    private fun folder(children: List<FolderChild>, showAsStack: Boolean = false) = TileModel.Folder(
        id = "g",
        position = 0,
        size = TileSize.WIDE,
        colorId = "blue",
        name = "folder",
        children = children,
        showAsStack = showAsStack,
    )

    @Test
    fun uniformLargeIsEligibleButNeedsTheToggle() {
        val children = listOf(child(TileSize.LARGE), child(TileSize.LARGE))
        assertEquals(TileSize.LARGE, folder(children).stackSize)
        assertEquals(false, folder(children, showAsStack = false).isStack)
        assertEquals(true, folder(children, showAsStack = true).isStack)
    }

    @Test
    fun uniformWideIsEligibleButNeedsTheToggle() {
        val children = listOf(child(TileSize.WIDE), child(TileSize.WIDE), child(TileSize.WIDE))
        assertEquals(TileSize.WIDE, folder(children).stackSize)
        assertEquals(false, folder(children, showAsStack = false).isStack)
        assertEquals(true, folder(children, showAsStack = true).isStack)
    }

    @Test
    fun uniformMediumIsNowEligible() {
        // TileSize.stackable widened past WIDE/LARGE-only to cover most sizes,
        // including the default MEDIUM — but only the explicit toggle turns
        // that eligibility into an actual stack, so an ordinary untouched
        // folder (showAsStack defaults false) still renders as a plain folder.
        val children = listOf(child(TileSize.MEDIUM), child(TileSize.MEDIUM))
        assertEquals(TileSize.MEDIUM, folder(children).stackSize)
        assertEquals(false, folder(children, showAsStack = false).isStack)
        assertEquals(true, folder(children, showAsStack = true).isStack)
    }

    @Test
    fun mixedSizesAreNeverAStackRegardlessOfTheToggle() {
        val children = listOf(child(TileSize.WIDE), child(TileSize.MEDIUM))
        assertNull(folder(children).stackSize)
        assertEquals(false, folder(children, showAsStack = false).isStack)
        assertEquals(false, folder(children, showAsStack = true).isStack)
    }

    @Test
    fun theFiveExcludedSizesAreNeverEligibleEvenWhenUniformAndToggledOn() {
        listOf(
            TileSize.SMALL, TileSize.WIDE_SMALL, TileSize.TALL, TileSize.BANNER, TileSize.COLUMN,
        ).forEach { size ->
            val children = listOf(child(size), child(size))
            assertNull("$size should not be stack-eligible", folder(children).stackSize)
            assertEquals(
                "$size should never be a stack even with the toggle on",
                false,
                folder(children, showAsStack = true).isStack,
            )
        }
    }

    @Test
    fun everyOtherSizeIsEligibleWhenUniformAndToggledOn() {
        listOf(
            TileSize.MEDIUM, TileSize.WIDE, TileSize.LARGE, TileSize.WIDE_MEDIUM,
            TileSize.TALL_MEDIUM, TileSize.XLARGE,
        ).forEach { size ->
            val children = listOf(child(size), child(size))
            assertEquals("$size should be stack-eligible", size, folder(children).stackSize)
            assertEquals(
                "$size should be a stack once toggled on",
                true,
                folder(children, showAsStack = true).isStack,
            )
        }
    }

    @Test
    fun emptyFolderIsNotAStack() {
        assertEquals(false, folder(emptyList()).isStack)
        assertEquals(false, folder(emptyList(), showAsStack = true).isStack)
        assertNull(folder(emptyList()).stackSize)
    }
}
