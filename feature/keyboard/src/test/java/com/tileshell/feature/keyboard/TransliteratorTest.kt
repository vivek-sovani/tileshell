package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class TransliteratorTest {

    companion object {
        private lateinit var marathi: WordList
        private lateinit var hindi: WordList

        @BeforeClass
        @JvmStatic
        fun load() {
            fun list(code: String): WordList {
                val extra = File("src/main/assets/keyboard/${code}_extra.txt").readLines()
                return File("src/main/assets/keyboard/${code}_words.txt").bufferedReader()
                    .useLines { WordList.parse(it + extra.asSequence()) }
            }
            marathi = list("mr")
            hindi = list("hi")
        }
    }

    private fun mr(latin: String) = Transliterator.candidates(latin, KeyboardLanguage.MARATHI, marathi).first()
    private fun hi(latin: String) = Transliterator.candidates(latin, KeyboardLanguage.HINDI, hindi).first()

    @Test
    fun `marathi everyday words`() {
        assertEquals("नमस्कार", mr("namaskar"))
        assertEquals("धन्यवाद", mr("dhanyavad"))
        assertEquals("माझा", mr("maza"))
        assertEquals("झाला", mr("zala"))
        assertEquals("आहे", mr("aahe"))
        assertEquals("आहे", mr("ahe"))
        assertEquals("मी", mr("mi"))
        assertEquals("नाही", mr("nahi"))
        assertEquals("काय", mr("kay"))
        assertEquals("तू", mr("tu"))
        // The word list knows Pune takes ण.
        assertEquals("पुणे", mr("pune"))
    }

    @Test
    fun `hindi everyday words`() {
        assertEquals("क्या", hi("kya"))
        assertEquals("नहीं", hi("nahin"))
        assertEquals("धन्यवाद", hi("dhanyavad"))
        assertEquals("मैं", hi("main"))
        assertEquals("है", hi("hai"))
    }

    @Test
    fun `rules alone give a sensible spelling for unknown words`() {
        assertEquals("कमल", Transliterator.candidates("kamal", KeyboardLanguage.HINDI, null).first())
        assertEquals("पुने", Transliterator.candidates("pune", KeyboardLanguage.MARATHI, null).first())
        assertEquals("क्षमा", Transliterator.candidates("kshama", KeyboardLanguage.MARATHI, null).first())
        // Capitals pick the retroflex letters.
        assertEquals("टोपी", Transliterator.candidates("Topi", KeyboardLanguage.HINDI, null).first())
        assertEquals("ठाणे", Transliterator.candidates("ThaaNe", KeyboardLanguage.MARATHI, null).first())
    }

    @Test
    fun `a spelling picked before leads`() {
        val c = Transliterator.candidates("namaskar", KeyboardLanguage.MARATHI, marathi, remembered = "नमस्कर")
        assertEquals("नमस्कर", c.first())
    }

    @Test
    fun `alternatives are distinct and capped`() {
        val c = Transliterator.candidates("dhanyavad", KeyboardLanguage.MARATHI, marathi)
        assertEquals(c.size, c.toSet().size)
        assertTrue(c.size <= 3)
    }

    @Test
    fun `quick enough for every key`() {
        val start = System.nanoTime()
        repeat(50) { mr("dhanyavadaanchya") }
        val ms = (System.nanoTime() - start) / 50 / 1_000_000.0
        assertTrue("took $ms ms", ms < 30)
    }
}
