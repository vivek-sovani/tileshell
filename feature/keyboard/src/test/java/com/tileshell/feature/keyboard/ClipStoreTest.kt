package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ClipStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `clips are newest first and gone after an hour unless pinned`() {
        val s = ClipStore(null)
        s.add("Shaniwar Peth, Pune 411030", now = 0)
        s.add("Meeting moved to 4 pm", now = 1_000)
        assertEquals(listOf("Meeting moved to 4 pm", "Shaniwar Peth, Pune 411030"), s.list(2_000).map { it.text })
        s.togglePin("Shaniwar Peth, Pune 411030")
        val later = ClipStore.KEEP_MS + 5_000
        assertEquals(listOf("Shaniwar Peth, Pune 411030"), s.list(later).map { it.text })
    }

    @Test
    fun `copying the same text again moves it up, keeping its pin`() {
        val s = ClipStore(null)
        s.add("a", 0)
        s.add("b", 1)
        s.togglePin("a")
        s.add("a", 2)
        val list = s.list(3)
        assertEquals("a", list.first().text)
        assertTrue(list.first().pinned)
        assertEquals(2, list.size)
    }

    @Test
    fun `clear keeps pinned clips`() {
        val s = ClipStore(null)
        s.add("x", 0)
        s.add("y", 0)
        s.togglePin("y")
        s.clearUnpinned()
        assertEquals(listOf("y"), s.list(1).map { it.text })
    }

    @Test
    fun `clips with new lines and tabs survive a restart`() {
        val f = tmp.newFile("clips.txt")
        ClipStore(f).add("line one\nline\ttwo \\ end", 10)
        assertEquals("line one\nline\ttwo \\ end", ClipStore(f).list(20).single().text)
    }

    @Test
    fun `blank copies are ignored and the cap holds`() {
        val s = ClipStore(null)
        s.add("   ", 0)
        assertTrue(s.list(1).isEmpty())
        repeat(40) { s.add("c$it", it.toLong()) }
        assertEquals(ClipStore.MAX, s.list(100).size)
    }
}
