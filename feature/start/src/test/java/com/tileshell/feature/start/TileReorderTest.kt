package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests for [reorderTiles] (FR-3.2 edit-mode drag). The function mirrors the
 * prototype reorder: splice the dragged id out, then re-insert at the target's
 * *original* index, so the landing side depends on the drag direction.
 */
class TileReorderTest {

    private val base = listOf("a", "b", "c", "d", "e")

    @Test
    fun forwardDrag_landsAfterTarget() {
        // a dragged onto d: removing a leaves [b,c,d,e]; inserting at index 3
        // (d's original index) puts a directly after d.
        assertEquals(listOf("b", "c", "d", "a", "e"), reorderTiles(base, "a", "d"))
    }

    @Test
    fun backwardDrag_landsBeforeTarget() {
        // e dragged onto b: removing e leaves [a,b,c,d]; inserting at index 1
        // (b's original index) puts e directly before c, i.e. after b.
        assertEquals(listOf("a", "e", "b", "c", "d"), reorderTiles(base, "e", "b"))
    }

    @Test
    fun adjacentForward_swapsNeighbours() {
        assertEquals(listOf("b", "a", "c", "d", "e"), reorderTiles(base, "a", "b"))
    }

    @Test
    fun adjacentBackward_swapsNeighbours() {
        assertEquals(listOf("a", "c", "b", "d", "e"), reorderTiles(base, "c", "b"))
    }

    @Test
    fun dragToFirst_movesToFront() {
        assertEquals(listOf("d", "a", "b", "c", "e"), reorderTiles(base, "d", "a"))
    }

    @Test
    fun dragToLast_movesToEnd() {
        assertEquals(listOf("a", "c", "d", "e", "b"), reorderTiles(base, "b", "e"))
    }

    @Test
    fun sameTile_isNoOp_andReturnsInput() {
        assertSame(base, reorderTiles(base, "c", "c"))
    }

    @Test
    fun missingDragId_isNoOp() {
        assertSame(base, reorderTiles(base, "zzz", "c"))
    }

    @Test
    fun missingTargetId_isNoOp() {
        assertSame(base, reorderTiles(base, "c", "zzz"))
    }

    @Test
    fun doesNotMutateInput() {
        val input = base.toList()
        reorderTiles(input, "a", "e")
        assertEquals(listOf("a", "b", "c", "d", "e"), input)
    }
}
