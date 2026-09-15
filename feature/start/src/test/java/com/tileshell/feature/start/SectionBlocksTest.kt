package com.tileshell.feature.start

import com.tileshell.core.data.Section
import com.tileshell.core.data.TileModel
import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [blocksFor], the Start-screen "sections" grouping. */
class SectionBlocksTest {

    private fun app(id: String, sectionId: String? = null) = TileModel.App(
        id = id, position = 0, size = TileSize.MEDIUM, colorId = "blue",
        packageName = "com.$id", activityName = "A", label = id, sectionId = sectionId,
    )

    @Test
    fun `no sections yields one unsectioned block holding every tile`() {
        val byId = mapOf("a" to app("a"), "b" to app("b"))
        val blocks = blocksFor(listOf("a", "b"), byId, sections = emptyList())
        assertEquals(1, blocks.size)
        assertEquals(null, blocks[0].sectionId)
        assertEquals(listOf("a", "b"), blocks[0].ids)
    }

    @Test
    fun `unsectioned renders first, then sections in their own order`() {
        val byId = mapOf(
            "a" to app("a", "work"),
            "b" to app("b"), // unsectioned
            "c" to app("c", "games"),
        )
        val sections = listOf(
            Section(id = "games", label = "games", order = 1),
            Section(id = "work", label = "work", order = 0),
        )
        val blocks = blocksFor(listOf("a", "b", "c"), byId, sections)
        assertEquals(listOf(null, "work", "games"), blocks.map { it.sectionId })
        assertEquals(listOf("b"), blocks[0].ids)
        assertEquals(listOf("a"), blocks[1].ids)
        assertEquals(listOf("c"), blocks[2].ids)
    }

    @Test
    fun `a section's members keep their relative order from the working list`() {
        val byId = mapOf(
            "a" to app("a", "work"),
            "b" to app("b", "work"),
            "c" to app("c", "work"),
        )
        val sections = listOf(Section(id = "work", label = "work", order = 0))
        val blocks = blocksFor(listOf("c", "a", "b"), byId, sections)
        assertEquals(listOf("c", "a", "b"), blocks.first { it.sectionId == "work" }.ids)
    }

    @Test
    fun `empty section still renders its own block with no ids`() {
        val byId = mapOf("a" to app("a"))
        val sections = listOf(Section(id = "empty", label = "empty", order = 0))
        val blocks = blocksFor(listOf("a"), byId, sections)
        assertEquals(2, blocks.size)
        assertEquals(emptyList<String>(), blocks[1].ids)
        assertEquals("empty", blocks[1].sectionId)
    }

    @Test
    fun `a tile referencing a section that no longer exists falls back to unsectioned`() {
        val byId = mapOf("a" to app("a", sectionId = "ghost"))
        val blocks = blocksFor(listOf("a"), byId, sections = emptyList())
        assertEquals(listOf("a"), blocks.first().ids)
    }

    @Test
    fun `collapsed flag is carried from the section`() {
        val sections = listOf(Section(id = "work", label = "work", order = 0, collapsed = true))
        val blocks = blocksFor(emptyList(), emptyMap(), sections)
        assertEquals(true, blocks.first { it.sectionId == "work" }.collapsed)
    }

    @Test
    fun `spliceBlockOrder swaps only the block's own members, in place`() {
        // "b" and "d" belong to the block being reordered; "a"/"c"/"e" don't.
        val order = listOf("a", "b", "c", "d", "e")
        val result = spliceBlockOrder(order, blockIds = setOf("b", "d"), newBlockOrder = listOf("d", "b"))
        assertEquals(listOf("a", "d", "c", "b", "e"), result)
    }

    @Test
    fun `spliceBlockOrder is a no-op when the new order matches the old`() {
        val order = listOf("a", "b", "c")
        val result = spliceBlockOrder(order, blockIds = setOf("a", "b", "c"), newBlockOrder = listOf("a", "b", "c"))
        assertEquals(order, result)
    }
}
