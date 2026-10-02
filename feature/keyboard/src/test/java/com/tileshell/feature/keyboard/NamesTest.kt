package com.tileshell.feature.keyboard

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Names and compounds: the words the user found failing, typed and swiped. */
class NamesTest {

    companion object {
        private lateinit var marathi: WordList

        /** Marathi as the keyboard loads it: its own lists, names, and Hindi's words a little weaker. */
        @BeforeClass
        @JvmStatic
        fun load() {
            fun lines(name: String) = File("src/main/assets/keyboard/$name").readLines()
            val borrowed = (lines("hi_words.txt") + lines("hi_extra.txt")).map(KeyboardDictionary::weaken)
            marathi = WordList.parse(
                lines("mr_words.txt").asSequence() + lines("mr_extra.txt") + lines("names.txt") + borrowed,
            )
        }
    }

    private fun typed(latin: String) = Transliterator.candidates(latin, KeyboardLanguage.MARATHI, marathi)

    @Test
    fun `the names that failed come out when typed`() {
        val cases = listOf(
            "natwarlal" to "नटवरलाल", "natvarlal" to "नटवरलाल",
            "laxmikant" to "लक्ष्मीकांत", "lakshmikant" to "लक्ष्मीकांत",
            "pyarelal" to "प्यारेलाल", "rahuldev" to "राहुलदेव",
            "barman" to "बर्मन", "burman" to "बर्मन", "ghoshal" to "घोषाल", "hareram" to "हरेराम",
            "vividhatene" to "विविधतेने", "jyeshth" to "ज्येष्ठ",
        )
        val misses = cases.filter { (latin, deva) -> deva !in typed(latin) }.map { (l, d) -> "$l→$d got ${typed(l)}" }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun `compound names not in any list are put together from their parts`() {
        val cases = listOf(
            "shivprasad" to "शिवप्रसाद", "gopalkrishna" to "गोपालकृष्ण", "ramprasad" to "रामप्रसाद",
            "ganeshkumar" to "गणेशकुमार", "anandrao" to "आनंदराव",
        )
        val misses = cases.filter { (latin, deva) -> deva !in typed(latin) }.map { (l, d) -> "$l→$d got ${typed(l)}" }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun `the names swipe too`() {
        val swipe = RomanizedLexicon(marathi.entries(), KeyboardLanguage.MARATHI)
        val decoder = SwipeDecoder(AccuracyEvalTest.centres, 100f)
        val names = listOf("नटवरलाल", "लक्ष्मीकांत", "प्यारेलाल", "राहुलदेव", "बर्मन", "घोषाल", "हरेराम")
        val misses = names.mapIndexedNotNull { i, deva ->
            val latin = Romanizer.variants(deva, KeyboardLanguage.MARATHI).last()
            val got = decoder.decode(AccuracyEvalTest.sloppyPath(latin, i), swipe).flatMap { swipe.devanagariFor(it.word) }.distinct()
            if (deva in got.take(3)) null else "$deva ($latin) got ${got.take(3)}"
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun `hindi words borrowed into marathi rank a little lower`() {
        assertTrue(KeyboardDictionary.weaken("नटवरलाल\t27") == "नटवरलाल\t21")
        assertTrue(KeyboardDictionary.weaken("x\t100\tx") == "x\t80\tx")
    }
}
