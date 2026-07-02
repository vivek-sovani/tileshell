package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [TileModel.Folder.isStack]/[TileModel.Folder.stackSize]: a
 * folder renders as a widget stack when every member is uniformly WIDE or
 * LARGE — from a large-onto-large merge, individual per-child resizes, or the
 * folder overlay's "make stack" action.
 */
class TileModelStackTest {

    private fun child(size: TileSize) = FolderChild(
        packageName = "pkg.$size",
        activityName = ".Main",
        label = size.name,
        size = size,
    )

    private fun folder(children: List<FolderChild>) = TileModel.Folder(
        id = "g",
        position = 0,
        size = TileSize.WIDE,
        colorId = "blue",
        name = "folder",
        children = children,
    )

    @Test
    fun uniformLargeIsAStack() {
        val f = folder(listOf(child(TileSize.LARGE), child(TileSize.LARGE)))
        assertEquals(true, f.isStack)
        assertEquals(TileSize.LARGE, f.stackSize)
    }

    @Test
    fun uniformWideIsAStack() {
        val f = folder(listOf(child(TileSize.WIDE), child(TileSize.WIDE), child(TileSize.WIDE)))
        assertEquals(true, f.isStack)
        assertEquals(TileSize.WIDE, f.stackSize)
    }

    @Test
    fun mixedSizesAreNotAStack() {
        val f = folder(listOf(child(TileSize.WIDE), child(TileSize.MEDIUM)))
        assertEquals(false, f.isStack)
        assertNull(f.stackSize)
    }

    @Test
    fun uniformSmallOrMediumIsNotAStack() {
        // Only WIDE/LARGE uniformity forms a stack — small/medium folders stay
        // the normal mini-grid regardless of how uniform their sizes are.
        assertEquals(false, folder(listOf(child(TileSize.SMALL), child(TileSize.SMALL))).isStack)
        assertEquals(false, folder(listOf(child(TileSize.MEDIUM), child(TileSize.MEDIUM))).isStack)
    }

    @Test
    fun emptyFolderIsNotAStack() {
        assertEquals(false, folder(emptyList()).isStack)
        assertNull(folder(emptyList()).stackSize)
    }
}
