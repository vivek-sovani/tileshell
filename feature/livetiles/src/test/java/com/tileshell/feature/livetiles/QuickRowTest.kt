package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickRowTest {

    @Test
    fun `nothing stored gives the four built-ins`() {
        assertEquals(QuickItem.BUILT_INS, decodeQuickItems(null))
    }

    @Test
    fun `round-trips pinned lists, notes and apps`() {
        val items = listOf(QuickItem.NewTask, QuickItem.TaskList("list-1"), QuickItem.Note(7), QuickItem.App("us.zoom.videomeetings"))
        assertEquals(items, decodeQuickItems(encodeQuickItems(items)))
    }

    @Test
    fun `an emptied row stays empty, and junk lines are skipped`() {
        assertEquals(emptyList<QuickItem>(), decodeQuickItems(""))
        assertEquals(listOf(QuickItem.Timer), decodeQuickItems("bogus\ntimer\nopennote:x\nlist:"))
    }

    @Test
    fun `adding is de-duplicated and appends, removing drops it`() {
        val base = listOf(QuickItem.NewNote)
        assertEquals(listOf(QuickItem.NewNote, QuickItem.App("a")), addQuickItem(base, QuickItem.App("a")))
        assertEquals(base, addQuickItem(base, QuickItem.NewNote))
        assertEquals(emptyList<QuickItem>(), removeQuickItem(base, QuickItem.NewNote))
    }
}
