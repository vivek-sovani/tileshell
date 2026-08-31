package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NotePreviewTest {

    @Test
    fun `blank text becomes the empty-note placeholder`() {
        assertEquals(NotePreview(title = "empty note", snippet = ""), notePreview(""))
        assertEquals(NotePreview(title = "empty note", snippet = ""), notePreview("   \n  "))
    }

    @Test
    fun `single line note is title-only, no snippet`() {
        assertEquals(NotePreview(title = "buy milk", snippet = ""), notePreview("buy milk"))
    }

    @Test
    fun `first line becomes the title, the rest becomes a flattened snippet`() {
        val preview = notePreview("groceries\nmilk\neggs\nbread")
        assertEquals("groceries", preview.title)
        assertEquals("milk eggs bread", preview.snippet)
    }

    @Test
    fun `leading and trailing whitespace is trimmed from both title and snippet`() {
        val preview = notePreview("  title line  \n  body text  ")
        assertEquals("title line", preview.title)
        assertEquals("body text", preview.snippet)
    }
}

class SuggestedNoteFileNameTest {

    @Test
    fun `uses the note's title as the file name`() {
        assertEquals("buy milk.txt", suggestedNoteFileName("buy milk"))
    }

    @Test
    fun `strips characters unsafe for a filename`() {
        assertEquals("groceries milk eggs.txt", suggestedNoteFileName("groceries: milk/eggs?\nmilk"))
    }

    @Test
    fun `an empty note exports under its own placeholder title`() {
        assertEquals("empty note.txt", suggestedNoteFileName(""))
    }

    @Test
    fun `falls back to a generic name when the title is only punctuation`() {
        assertEquals("note.txt", suggestedNoteFileName("???!!!"))
    }

    @Test
    fun `long titles are truncated so the filename stays reasonable`() {
        val longTitle = "a".repeat(80)
        assertEquals("${"a".repeat(40)}.txt", suggestedNoteFileName(longTitle))
    }
}
