package com.tileshell.feature.start

import androidx.compose.ui.geometry.Offset
import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the folder-child drag-out gesture's pure helpers:
 * [isInsideFolderBlock] (the intra-folder-reorder vs. pulled-out-to-top-level
 * boundary check) and [insertBeforeTarget] (dense-mode drop-position
 * computation).
 */
class FolderChildPullOutTest {

    private val geom = GridGeometry.of(totalWidthPx = 400f, columns = 4)

    // A folder tile (MEDIUM, 2x2) at col 0 row 0, with two SMALL children
    // packed right below it (row 2), as GridPacker.expandFolderInline would
    // lay them out.
    private val folderPlacement = TilePlacement("folder-1", TileSize.MEDIUM, col = 0, row = 0)
    private val child0 = TilePlacement("child-0", TileSize.SMALL, col = 0, row = 2)
    private val child1 = TilePlacement("child-1", TileSize.SMALL, col = 1, row = 2)
    private val sibling = TilePlacement("sibling", TileSize.MEDIUM, col = 2, row = 0)
    private val placements = listOf(folderPlacement, child0, child1, sibling)

    private fun isChild(id: String) = id == "child-0" || id == "child-1"

    @Test
    fun `point over the folder tile itself is inside the block`() {
        val centre = geom.rect(folderPlacement).let { Offset((it.left + it.right) / 2, (it.top + it.bottom) / 2) }
        assertTrue(isInsideFolderBlock(placements, geom, "folder-1", centre, ::isChild))
    }

    @Test
    fun `point over a rendered child is inside the block`() {
        val centre = geom.rect(child1).let { Offset((it.left + it.right) / 2, (it.top + it.bottom) / 2) }
        assertTrue(isInsideFolderBlock(placements, geom, "folder-1", centre, ::isChild))
    }

    @Test
    fun `point over an unrelated sibling sharing the folder's own row is outside the block`() {
        // Regression case: a top-level tile beside the folder, in the same row
        // band, must never read as "inside" just because it's vertically level
        // with the folder — only the folder's own cell and its children's
        // cells count.
        val centre = geom.rect(sibling).let { Offset((it.left + it.right) / 2, (it.top + it.bottom) / 2) }
        assertFalse(isInsideFolderBlock(placements, geom, "folder-1", centre, ::isChild))
    }

    @Test
    fun `point in empty space below everything is outside the block`() {
        assertFalse(isInsideFolderBlock(placements, geom, "folder-1", Offset(10f, 10000f), ::isChild))
    }

    @Test
    fun `unknown folder id never reports inside`() {
        val centre = geom.rect(folderPlacement).let { Offset((it.left + it.right) / 2, (it.top + it.bottom) / 2) }
        assertFalse(isInsideFolderBlock(placements, geom, "some-other-folder", centre, ::isChild))
    }

    // ---- insertBeforeTarget ---------------------------------------------

    private val order = listOf("a", "b", "c", "d")

    @Test
    fun `inserts before the target's current index`() {
        assertEquals(listOf("a", "new", "b", "c", "d"), insertBeforeTarget(order, "new", "b"))
    }

    @Test
    fun `inserting before the first tile lands at the front`() {
        assertEquals(listOf("new", "a", "b", "c", "d"), insertBeforeTarget(order, "new", "a"))
    }

    @Test
    fun `null target appends at the end`() {
        assertEquals(listOf("a", "b", "c", "d", "new"), insertBeforeTarget(order, "new", null))
    }

    @Test
    fun `unknown target id appends at the end`() {
        assertEquals(listOf("a", "b", "c", "d", "new"), insertBeforeTarget(order, "new", "zzz"))
    }

    @Test
    fun `does not mutate the input order`() {
        val input = order.toList()
        insertBeforeTarget(input, "new", "b")
        assertEquals(listOf("a", "b", "c", "d"), input)
    }

    // ---- mergeOrder -------------------------------------------------------
    // Regression coverage for the real bug this fix addressed: a folder child
    // dragged out to a specific position landed at the bottom anyway, because
    // the client-side order-reconciliation effect blindly appended any
    // brand-new id instead of respecting where the fresh, DB-ordered list
    // actually placed it.

    @Test
    fun `a brand-new id lands at its own position, not appended at the end`() {
        // "new" was written by the DB right before "c" — mergeOrder must put
        // it there too, even though the client's own `current` never had it.
        val current = listOf("a", "b", "c", "d")
        val fresh = listOf("a", "b", "new", "c", "d")
        assertEquals(listOf("a", "b", "new", "c", "d"), mergeOrder(current, fresh))
    }

    @Test
    fun `a genuinely appended new id still lands at the end`() {
        val current = listOf("a", "b", "c")
        val fresh = listOf("a", "b", "c", "new")
        assertEquals(listOf("a", "b", "c", "new"), mergeOrder(current, fresh))
    }

    @Test
    fun `a removed id is dropped, surviving relative order is preserved`() {
        // current has already applied a live reorder (c before b) that
        // hasn't round-tripped through the DB yet — that arrangement must
        // survive, not be overwritten by fresh's own (different) ordering.
        val current = listOf("a", "c", "b")
        val fresh = listOf("a", "b") // "c" was removed
        assertEquals(listOf("a", "b"), mergeOrder(current, fresh))
    }

    @Test
    fun `empty current just takes the fresh order`() {
        assertEquals(listOf("a", "b", "c"), mergeOrder(emptyList(), listOf("a", "b", "c")))
    }

    @Test
    fun `does not mutate either input list`() {
        val current = listOf("a", "b")
        val fresh = listOf("a", "new", "b")
        mergeOrder(current, fresh)
        assertEquals(listOf("a", "b"), current)
        assertEquals(listOf("a", "new", "b"), fresh)
    }
}
