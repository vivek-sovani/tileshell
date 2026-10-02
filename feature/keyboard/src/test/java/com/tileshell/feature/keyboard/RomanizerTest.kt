package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RomanizerTest {

    private fun mr(w: String) = Romanizer.romanize(w, KeyboardLanguage.MARATHI)
    private fun hi(w: String) = Romanizer.romanize(w, KeyboardLanguage.HINDI)

    @Test
    fun `marathi words romanise the casual way`() {
        assertEquals("namaskar", mr("नमस्कार"))
        assertEquals("dhanyavad", mr("धन्यवाद"))
        assertEquals("maza", mr("माझा"))
        assertEquals("zala", mr("झाला"))
        assertEquals("ahe", mr("आहे"))
        assertEquals("hindi", mr("हिंदी"))
        assertEquals("pune", mr("पुणे"))
        assertEquals("dnyan", mr("ज्ञान"))
        assertEquals("kshama", mr("क्षमा"))
        assertEquals("jyeshth", mr("ज्येष्ठ"))
        assertEquals("vividhatene", mr("विविधतेने"))
    }

    @Test
    fun `hindi words`() {
        assertEquals("kya", hi("क्या"))
        assertEquals("nahin", hi("नहीं"))
        assertEquals("main", hi("मैं"))
        assertEquals("jhanda", hi("झंडा"))
        assertEquals("zara", hi("ज़रा"))
    }

    @Test
    fun `a learned word becomes swipeable`() {
        val swipe = RomanizedLexicon(emptySequence(), KeyboardLanguage.MARATHI)
        swipe.add(LexEntry("ज्येष्ठांना", 120))
        assertEquals(listOf("ज्येष्ठांना"), swipe.devanagariFor("jyeshthanna"))
        assertTrue(swipe.startingWith("j").any { it.word == "jyeshthanna" })
    }

    @Test
    fun `non devanagari words are skipped`() {
        assertNull(mr("abc"))
    }

    @Test
    fun `a marathi swipe gives the devanagari word`() {
        val extra = File("src/main/assets/keyboard/mr_extra.txt").readLines()
        val list = File("src/main/assets/keyboard/mr_words.txt").bufferedReader().useLines { WordList.parse(it + extra.asSequence()) }
        val swipe = RomanizedLexicon(list.entries(), KeyboardLanguage.MARATHI)
        val centres = buildMap {
            listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { r, (row, off) ->
                row.forEachIndexed { c, ch -> put(ch, Pt((c + off) * 100f + 50f, r * 120f + 50f)) }
            }
        }
        val decoder = SwipeDecoder(centres, 100f)
        for ((latin, deva) in listOf(
            "namaskar" to "नमस्कार", "maza" to "माझा", "dhanyavad" to "धन्यवाद", "ahe" to "आहे",
            "vividhatene" to "विविधतेने", "jyeshth" to "ज्येष्ठ",
        )) {
            val path = latin.map { centres.getValue(it) }.zipWithNext().flatMap { (a, b) ->
                (0 until 6).map { s -> Pt(a.x + (b.x - a.x) * s / 6f, a.y + (b.y - a.y) * s / 6f) }
            } + centres.getValue(latin.last())
            val words = decoder.decode(path, swipe).flatMap { swipe.devanagariFor(it.word) }
            assertTrue("$latin → $words", words.take(2).contains(deva))
        }
    }
}
