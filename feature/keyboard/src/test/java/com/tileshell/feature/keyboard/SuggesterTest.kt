package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Corrections against the real bundled word list. */
class SuggesterTest {

    companion object {
        private lateinit var words: WordList

        @BeforeClass
        @JvmStatic
        fun load() {
            val extra = File("src/main/assets/keyboard/en_in_extra.txt").readLines()
            words = File("src/main/assets/keyboard/en_words.txt").bufferedReader().useLines {
                WordList.parse(it + extra.asSequence())
            }
        }
    }

    private fun fix(typed: String, level: AutocorrectLevel = AutocorrectLevel.BALANCED) =
        Suggester.correction(typed, words, level)

    @Test
    fun `common typos are corrected`() {
        assertEquals("the", fix("teh"))
        assertEquals("meeting", fix("meetign"))
        assertEquals("don't", fix("dont"))
        assertEquals("I'm", fix("im"))
        assertEquals("tomorrow", fix("tommorow"))
        assertEquals("thanks", fix("thanls"))
    }

    @Test
    fun `real words are left alone`() {
        for (w in listOf("hello", "meeting", "office", "review", "leaving", "the", "a", "at")) {
            assertNull(w, fix(w))
        }
    }

    @Test
    fun `indian places, food and everyday words are known`() {
        for (w in listOf("pune", "lakh", "crore", "paneer", "yaar", "diwali", "Deshpande")) {
            assertTrue(w, words.lookup(w.lowercase()) != null)
        }
        assertEquals("Pune", fix("pune"))
        assertNull(fix("lakh"))
    }

    @Test
    fun `rare words aren't offered as completions`() {
        val strip = Suggester.strip("teh", words, AutocorrectLevel.BALANCED, autocorrect = true)
        assertFalse(strip.any { it.text == "tehuantepec" || it.text == "Tehuantepec" })
    }

    @Test
    fun `usual capitals come back`() {
        assertEquals("I", fix("i"))
        assertEquals("London", fix("london"))
    }

    @Test
    fun `typed case is kept`() {
        assertEquals("The", fix("Teh"))
        assertEquals("THE", fix("TEH"))
        assertEquals("Tomorrow", Suggester.applyCase("Tom", "tomorrow"))
    }

    @Test
    fun `numbers and single letters aren't corrected`() {
        assertNull(fix("4pm"))
        assertNull(fix("x"))
    }

    @Test
    fun `mild corrects less than aggressive`() {
        // Two edits away: only the higher levels reach it.
        assertNull(fix("thnaks", AutocorrectLevel.MILD) ?: fix("tahnsk", AutocorrectLevel.MILD))
        assertEquals("thanks", fix("tahnsk", AutocorrectLevel.AGGRESSIVE))
    }

    @Test
    fun `strip shows the typed word, the best guess underlined, then completions`() {
        val strip = Suggester.strip("mee", words, AutocorrectLevel.BALANCED, autocorrect = true)
        assertEquals(StripWord.Kind.TYPED, strip[0].kind)
        assertTrue(strip.size in 2..Suggester.STRIP_SIZE)
        assertTrue(strip.any { it.text == "meet" || it.text == "meeting" })
        assertEquals(1, strip.count { it.best })
    }

    @Test
    fun `a real word being typed is its own best guess`() {
        val strip = Suggester.strip("meet", words, AutocorrectLevel.BALANCED, autocorrect = true)
        assertTrue(strip[0].best)
    }

    @Test
    fun `a misspelling underlines the correction and dims the typed word`() {
        val strip = Suggester.strip("teh", words, AutocorrectLevel.BALANCED, autocorrect = true)
        assertFalse(strip[0].best)
        assertEquals("the", strip.first { it.best }.text)
    }

    @Test
    fun `undo strip quotes the original first`() {
        val strip = Suggester.undoStrip("teh", "the", words)
        assertEquals("“teh”", strip[0].text)
        assertEquals(StripWord.Kind.UNDO, strip[0].kind)
        assertEquals("the", strip[1].text)
        assertTrue(strip[1].best)
    }

    @Test
    fun `corrections are quick enough for every space`() {
        val start = System.nanoTime()
        repeat(20) { fix("acknowledgemnet"); fix("teh"); fix("abcdefgh") }
        val perCall = (System.nanoTime() - start) / 60 / 1_000_000.0
        assertTrue("took $perCall ms per correction", perCall < 60)
    }

    @Test
    fun `neighbouring keys`() {
        assertTrue(KeyProximity.near('q', 'w'))
        assertTrue(KeyProximity.near('s', 'e'))
        assertFalse(KeyProximity.near('q', 'p'))
    }
}
