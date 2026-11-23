package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickToggleTileTest {

    @Test
    fun `every toggle id round trips through the tile's activity name`() {
        QuickToggleTile.DEFS.forEach { assertEquals(it.id, QuickToggleTile.decode(QuickToggleTile.encode(it.id))) }
    }

    @Test
    fun `unknown, blank and foreign activity names decode to null`() {
        assertNull(QuickToggleTile.decode(null))
        assertNull(QuickToggleTile.decode(""))
        assertNull(QuickToggleTile.decode("qt:nonsense"))
        assertNull(QuickToggleTile.decode("note:12"))
    }

    @Test
    fun `toggle ids are unique and none is the flashlight, which is its own tile`() {
        assertEquals(QuickToggleTile.DEFS.size, QuickToggleTile.DEFS.map { it.id }.toSet().size)
        assertTrue(QuickToggleTile.DEFS.none { it.id == "flashlight" })
    }

    @Test
    fun `the tile's icon key is the live face's key`() {
        assertEquals(LiveFace.QUICKTOGGLE, LiveFace.forIconKey(QuickToggleTile.ICON_KEY, com.tileshell.core.data.TileSize.MEDIUM))
    }
}
