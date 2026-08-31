package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickyNoteLayoutTest {

    @Test
    fun `bigger tiles get progressively more lines`() {
        assertTrue(maxLinesForStickyNote(TileSize.MEDIUM) > maxLinesForStickyNote(TileSize.SMALL))
        assertTrue(maxLinesForStickyNote(TileSize.LARGE) > maxLinesForStickyNote(TileSize.MEDIUM))
        assertTrue(maxLinesForStickyNote(TileSize.XLARGE) > maxLinesForStickyNote(TileSize.LARGE))
    }

    @Test
    fun `stickynote icon key maps to the sticky note face at medium and up`() {
        assertEquals(LiveFace.STICKYNOTE, LiveFace.forIconKey("stickynote", TileSize.MEDIUM))
        assertEquals(LiveFace.STICKYNOTE, LiveFace.forIconKey("stickynote", TileSize.WIDE))
    }

    @Test
    fun `sticky note tile stays static at small`() {
        assertNull(LiveFace.forIconKey("stickynote", TileSize.SMALL))
    }

    @Test
    fun `sticky note never flips`() {
        assertEquals(false, LiveFace.STICKYNOTE.flips)
    }
}
