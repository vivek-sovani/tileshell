package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LearnedWordsTest {

    @get:Rule val tmp = TemporaryFolder()

    private val bundled = WordList.parse(sequenceOf("rahu\t100", "the\t222"))

    @Test
    fun `a word is known after two uses, or at once after an undo`() {
        val learned = LearnedWords(null)
        learned.learn("Deshpande")
        assertFalse(learned.isKnown("deshpande"))
        learned.learn("Deshpande")
        assertTrue(learned.isKnown("deshpande"))
        learned.learn("teh", strong = true)
        assertTrue(learned.isKnown("teh"))
    }

    @Test
    fun `learned words survive a restart and keep their capital`() {
        val file = tmp.newFile("learned.txt")
        LearnedWords(file).apply { learn("Rahul", strong = true) }
        val again = LearnedWords(file)
        assertEquals("Rahul", again.entry("rahul")?.word)
    }

    @Test
    fun `numbers, spaces and single letters are never learned`() {
        val learned = LearnedWords(null)
        listOf("4pm", "a", "two words").forEach { learned.learn(it, strong = true) }
        assertEquals(0, learned.size)
    }

    @Test
    fun `learned words join the bundled list and stop corrections`() {
        val learned = LearnedWords(null)
        val lex = CombinedLexicon(bundled, learned)
        assertEquals("rahu", Suggester.correction("rahul", lex, AutocorrectLevel.AGGRESSIVE))
        learned.learn("Rahul", strong = true)
        assertEquals("Rahul", lex.lookup("rahul")?.word)
        // Typed lower case, it comes back with its capital.
        assertEquals("Rahul", Suggester.correction("rahul", lex, AutocorrectLevel.AGGRESSIVE))
        assertNull(Suggester.correction("Rahul", lex, AutocorrectLevel.AGGRESSIVE))
    }

    @Test
    fun `clear forgets everything`() {
        val learned = LearnedWords(null)
        learned.learn("Pune", strong = true)
        learned.clear()
        assertNull(learned.entry("pune"))
    }
}
