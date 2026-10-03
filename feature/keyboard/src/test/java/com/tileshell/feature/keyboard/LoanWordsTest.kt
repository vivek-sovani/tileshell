package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** English words typed in मराठी / हिन्दी: their Devanagari spelling, else the English word. */
class LoanWordsTest {

    companion object {
        private lateinit var marathi: WordList
        private lateinit var hindi: WordList
        private lateinit var english: WordList
        private lateinit var mrLoans: LoanWords
        private lateinit var hiLoans: LoanWords

        @BeforeClass
        @JvmStatic
        fun load() {
            fun lines(name: String) = File("src/main/assets/keyboard/$name").readLines().asSequence()
            marathi = WordList.parse(lines("mr_words.txt") + lines("mr_extra.txt"))
            hindi = WordList.parse(lines("hi_words.txt") + lines("hi_extra.txt"))
            english = WordList.parse(lines("en_words.txt") + lines("en_in_extra.txt"))
            mrLoans = LoanWords.parse(lines("mr_loan.txt"), lines("hi_loan.txt"))
            hiLoans = LoanWords.parse(lines("hi_loan.txt"))
        }
    }

    private fun mr(latin: String) =
        Transliterator.candidates(latin, KeyboardLanguage.MARATHI, marathi, loans = mrLoans, english = english)

    private fun hi(latin: String) =
        Transliterator.candidates(latin, KeyboardLanguage.HINDI, hindi, loans = hiLoans, english = english)

    @Test
    fun `english words spelled in hindi`() {
        assertEquals("एनर्जी", hi("energy").first())
        assertEquals("पॉजिटिव", hi("positive").first())
        assertTrue(hi("negative").contains("निगेटिव"))
        assertEquals("मोबाइल", hi("mobile").first())
        assertEquals("कंप्यूटर", hi("computer").first())
        assertEquals("मॉर्निंग", hi("morning").first())
    }

    @Test
    fun `english words spelled in marathi, with hindi's spelling too`() {
        assertEquals("एनर्जी", mr("energy").first())
        assertEquals("पॉझिटिव्ह", mr("positive").first())
        assertTrue(mr("positive").contains("पॉजिटिव"))
        assertEquals("निगेटिव्ह", mr("negative").first())
        assertEquals("कॅमेरा", mr("camera").first())
    }

    @Test
    fun `a common native word still wins over an english one`() {
        assertEquals("हम", hi("ham").first())
        assertEquals("मैं", hi("main").first())
        assertEquals("नमस्कार", mr("namaskar").first())
        assertEquals("आहे", mr("ahe").first())
    }

    @Test
    fun `an english word with no devanagari spelling stays english`() {
        // In the English list, not in the pronouncing dictionary.
        val word = "footballer"
        assertTrue(hiLoans[word].isEmpty())
        assertEquals(word, hi(word).first())
    }

    @Test
    fun `completions of an english word being typed`() {
        assertTrue(hiLoans.completions("ener", 3).contains("एनर्जी"))
        assertTrue(hiLoans["energy"].isNotEmpty())
        assertTrue(hiLoans["xyzzy"].isEmpty())
    }
}
