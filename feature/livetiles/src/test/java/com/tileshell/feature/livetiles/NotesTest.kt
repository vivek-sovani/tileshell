package com.tileshell.feature.livetiles

import com.tileshell.core.data.NoteItem
import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesSummaryTest {

    @Test
    fun `no notes summarizes to zero count and an empty preview`() {
        val summary = notesSummary(emptyList())
        assertEquals(0, summary.count)
        assertTrue(summary.preview.isEmpty())
    }

    @Test
    fun `preview is most-recent-first, since the repository already orders that way`() {
        val notes = listOf(
            NoteItem(id = 1, text = "newer note", updatedAt = 200L),
            NoteItem(id = 2, text = "older note", updatedAt = 100L),
        )
        val summary = notesSummary(notes, maxPreview = 2)
        assertEquals(2, summary.count)
        assertEquals(listOf("newer note", "older note"), summary.preview.map { it.title })
    }

    @Test
    fun `preview is capped at maxPreview even when more notes exist`() {
        val notes = (1..5).map { NoteItem(id = it.toLong(), text = "note $it", updatedAt = it.toLong()) }
        val summary = notesSummary(notes, maxPreview = 2)
        assertEquals(5, summary.count)
        assertEquals(2, summary.preview.size)
    }

    @Test
    fun `notepad icon key maps to the notes face at medium and up`() {
        assertEquals(LiveFace.NOTES, LiveFace.forIconKey("notepad", TileSize.MEDIUM))
        assertEquals(LiveFace.NOTES, LiveFace.forIconKey("notepad", TileSize.WIDE))
    }

    @Test
    fun `notes tile stays static at small`() {
        assertNull(LiveFace.forIconKey("notepad", TileSize.SMALL))
    }

    @Test
    fun `notes face flips`() {
        assertTrue(LiveFace.NOTES.flips)
    }
}

class NotesLayoutTest {

    @Test
    fun `bigger tiles show more separate notes, not just a longer snippet of one`() {
        assertTrue(maxPreviewForNotes(TileSize.MEDIUM) > maxPreviewForNotes(TileSize.SMALL))
        assertTrue(maxPreviewForNotes(TileSize.LARGE) > maxPreviewForNotes(TileSize.MEDIUM))
        assertTrue(maxPreviewForNotes(TileSize.XLARGE) > maxPreviewForNotes(TileSize.LARGE))
    }

    @Test
    fun `wide tiles show at least two notes to justify the two-column layout`() {
        assertTrue(maxPreviewForNotes(TileSize.WIDE) >= 2)
    }

    @Test
    fun `taller tiles get progressively more snippet lines`() {
        assertTrue(snippetLinesFor(TileSize.MEDIUM) < snippetLinesFor(TileSize.LARGE))
        assertTrue(snippetLinesFor(TileSize.LARGE) < snippetLinesFor(TileSize.XLARGE))
    }

    @Test
    fun `every note-capable size gets more than a single clipped line`() {
        assertTrue(snippetLinesFor(TileSize.MEDIUM) > 1)
        assertTrue(snippetLinesFor(TileSize.WIDE) > 1)
    }
}
