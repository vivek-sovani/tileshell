package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NextWordsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    companion object {
        private lateinit var en: NextWords
        private lateinit var mr: NextWords
        private lateinit var hi: NextWords

        @BeforeClass
        @JvmStatic
        fun load() {
            fun table(code: String) = NextWords.parse(File("src/main/assets/keyboard/${code}_next.txt").readText())
            en = table("en")
            mr = table("mr")
            hi = table("hi")
        }
    }

    private fun predict(before: String, table: NextWords = en, lang: KeyboardLanguage = KeyboardLanguage.ENGLISH, hour: Int = 10, learned: LearnedPairs? = null) =
        NextWords.predict(NextWords.context(before), table, learned, lang, hour, 4)

    @Test
    fun `context is the last words of the phrase, or a sentence start`() {
        assertEquals(listOf(NextWords.START), NextWords.context(""))
        assertEquals(listOf(NextWords.START), NextWords.context("Hi there. "))
        assertEquals(listOf("see", "you"), NextWords.context("I will see you "))
        assertEquals(listOf("good"), NextWords.context("Good "))
        assertEquals(listOf("then"), NextWords.context("ok, then "))
        assertEquals(listOf("now"), NextWords.context("call 9876 now "))
        // Mid-word, or after something that isn't a word: nothing.
        assertEquals(emptyList<String>(), NextWords.context("I will se"))
        assertEquals(emptyList<String>(), NextWords.context("call 9876 "))
        assertEquals(listOf("मला", "माहीत"), NextWords.context("मला माहीत "))
    }

    @Test
    fun `usual next words`() {
        assertEquals("you", predict("Thank ").first())
        assertTrue(predict("I will see you ").contains("tomorrow"))
        assertTrue(predict("How are ").contains("you"))
        assertTrue(predict("").contains("i"))
        assertTrue(predict("मला ", mr, KeyboardLanguage.MARATHI).isNotEmpty())
        assertTrue(predict("क्या ", hi, KeyboardLanguage.HINDI).isNotEmpty())
        assertEquals(emptyList<String>(), en["no such context here"])
    }

    @Test
    fun `good is followed by the greeting for the time of day`() {
        assertEquals(listOf("morning", "afternoon", "evening", "night"), predict("Good ", hour = 8))
        assertEquals("afternoon", predict("Good ", hour = 14).first())
        assertEquals("evening", predict("good ", hour = 18).first())
        assertEquals("night", predict("Good ", hour = 23).first())
        assertEquals("night", predict("Good ", hour = 2).first())
        assertEquals("सकाळ", predict("शुभ ", mr, KeyboardLanguage.MARATHI, hour = 7).first())
        assertEquals("रात्रि", predict("शुभ ", hi, KeyboardLanguage.HINDI, hour = 22).first())
        // Not a greeting mid-sentence.
        assertTrue("morning" !in predict("That is a good ", hour = 8))
    }

    @Test
    fun `the user's own pairs lead once seen twice, and persist`() {
        val file = tmp.newFile("pairs.txt")
        val pairs = LearnedPairs(file)
        pairs.learn("see", "Pune")
        assertTrue("Pune" !in predict("I will see ", learned = pairs))
        pairs.learn("see", "Pune")
        assertEquals("Pune", predict("I will see ", learned = pairs).first())
        assertEquals(listOf("Pune"), LearnedPairs(file).after("see", 3))
        pairs.clear()
        assertEquals(emptyList<String>(), LearnedPairs(file).after("see", 3))
    }

    @Test
    fun `numbers and blanks are never learned`() {
        val pairs = LearnedPairs(null)
        repeat(2) { pairs.learn("call", "9876") }
        repeat(2) { pairs.learn("", "x") }
        assertEquals(0, pairs.size)
    }

    @Test
    fun `the context lifts the likely word while it is typed`() {
        val lex = WordList.parse(sequenceOf("more\t200", "morning\t120", "mortgage\t110"))
        val plain = Suggester.strip("mor", lex, AutocorrectLevel.BALANCED, autocorrect = false)
        val withContext = Suggester.strip("mor", lex, AutocorrectLevel.BALANCED, autocorrect = false, next = listOf("morning"))
        assertEquals("more", plain[1].text)
        assertEquals("morning", withContext[1].text)
    }
}
