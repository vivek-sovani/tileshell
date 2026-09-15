package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the section-header ↑/↓ reorder control ([swapSectionOrder]). */
class SectionTest {

    private fun sections(vararg labels: String) =
        labels.mapIndexed { index, label -> Section(id = label, label = label, order = index) }

    @Test
    fun movesUpSwapsWithPreviousNeighbor() {
        val result = swapSectionOrder(sections("work", "games", "travel"), id = "games", direction = -1)
        assertEquals(listOf("games", "work", "travel"), result.sortedBy { it.order }.map { it.id })
    }

    @Test
    fun movesDownSwapsWithNextNeighbor() {
        val result = swapSectionOrder(sections("work", "games", "travel"), id = "games", direction = 1)
        assertEquals(listOf("work", "travel", "games"), result.sortedBy { it.order }.map { it.id })
    }

    @Test
    fun noOpAtTopOfList() {
        val original = sections("work", "games", "travel")
        val result = swapSectionOrder(original, id = "work", direction = -1)
        assertEquals(original, result)
    }

    @Test
    fun noOpAtBottomOfList() {
        val original = sections("work", "games", "travel")
        val result = swapSectionOrder(original, id = "travel", direction = 1)
        assertEquals(original, result)
    }

    @Test
    fun noOpWhenIdNotFound() {
        val original = sections("work", "games")
        val result = swapSectionOrder(original, id = "missing", direction = 1)
        assertEquals(original, result)
    }

    @Test
    fun onlyTheTwoSwappedSectionsChange() {
        // a=0, b=1, c=2, d=3; swapping c up trades orders with b only.
        val result = swapSectionOrder(sections("a", "b", "c", "d"), id = "c", direction = -1)
        val byId = result.associateBy { it.id }
        assertEquals(0, byId.getValue("a").order) // untouched
        assertEquals(2, byId.getValue("b").order) // took c's old order
        assertEquals(1, byId.getValue("c").order) // took b's old order
        assertEquals(3, byId.getValue("d").order) // untouched
    }
}
