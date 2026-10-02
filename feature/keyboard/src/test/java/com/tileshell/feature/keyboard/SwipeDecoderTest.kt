package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.sin

class SwipeDecoderTest {

    companion object {
        private lateinit var words: WordList
        private const val W = 100f

        /** Qwerty key centres, 100 px keys, rows offset as on the keyboard. */
        private val centres: Map<Char, Pt> = buildMap {
            listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { r, (row, off) ->
                row.forEachIndexed { c, ch -> put(ch, Pt((c + off) * W + W / 2, r * W * 1.2f + W / 2)) }
            }
        }

        @BeforeClass
        @JvmStatic
        fun load() {
            val extra = File("src/main/assets/keyboard/en_in_extra.txt").readLines() +
                File("src/main/assets/keyboard/en_chat_extra.txt").readLines()
            words = File("src/main/assets/keyboard/en_words.txt").bufferedReader().useLines {
                WordList.parse(it + extra.asSequence())
            }
        }


        /** A finger path through the word's keys: 6 samples per stroke, a little wobble. */
        fun pathFor(word: String, wobble: Float = 18f): List<Pt> {
            val keys = word.map { centres.getValue(it) }
            val out = ArrayList<Pt>()
            for ((a, b) in keys.zipWithNext()) {
                for (s in 0 until 6) {
                    val t = s / 6f
                    out += Pt(a.x + (b.x - a.x) * t + wobble * sin(out.size * 1.7f), a.y + (b.y - a.y) * t)
                }
            }
            out += keys.last()
            return out
        }
    }

    private val decoder = SwipeDecoder(centres, W)

    /**
     * A sloppier, finger-like path: corners cut (each point averaged with its
     * neighbours), a sideways wobble and a drift off the key centres.
     */
    private fun sloppy(word: String): List<Pt> {
        val raw = pathFor(word, wobble = 22f).map { Pt(it.x + 12f, it.y - 15f) }
        return raw.indices.map { i ->
            val a = raw[maxOf(0, i - 2)]
            val b = raw[i]
            val c = raw[minOf(raw.lastIndex, i + 2)]
            Pt((a.x + 2 * b.x + c.x) / 4, (a.y + 2 * b.y + c.y) / 4)
        }
    }

    private fun top(word: String) = decoder.decode(pathFor(word), words).map { it.word }

    @Test
    fun `common words come out first`() {
        for (w in listOf("home", "the", "you", "meeting", "office", "thanks", "today", "where")) {
            assertEquals(w, w, top(w).firstOrNull()?.lowercase())
        }
    }

    @Test
    fun `chat words that the formal list ranks low still come out`() {
        for (w in listOf("congrats", "okay", "gonna", "lol")) {
            assertEquals(w, w, top(w).firstOrNull()?.lowercase())
        }
    }

    @Test
    fun `sloppy finger paths still give the word`() {
        for (w in listOf("congrats", "hello", "thanks", "birthday", "tomorrow", "awesome", "meeting", "reaching")) {
            assertEquals(w, w, decoder.decode(sloppy(w), words).firstOrNull()?.word?.lowercase())
        }
    }

    @Test
    fun `a learned word comes first once picked`() {
        val learned = LearnedWords(null)
        val lex = CombinedLexicon(words, learned)
        learned.learn("congrats", strong = true)
        assertEquals("congrats", decoder.decode(sloppy("congrats"), lex).first().word.lowercase())
    }

    @Test
    fun `alternatives follow`() {
        val results = decoder.decode(pathFor("home"), words)
        assertTrue(results.size in 2..4)
        assertEquals(results.size, results.map { it.word.lowercase() }.toSet().size)
    }

    @Test
    fun `quick enough to place a word on lift`() {
        val path = pathFor("everything")
        val start = System.nanoTime()
        repeat(10) { decoder.decode(path, words) }
        val ms = (System.nanoTime() - start) / 10 / 1_000_000.0
        assertTrue("took $ms ms", ms < 80)
    }

    @Test
    fun `a slip isn't a swipe`() {
        assertFalse(SwipeDecoder.isSwipe(Pt(0f, 0f), Pt(30f, 10f), W))
        assertTrue(SwipeDecoder.isSwipe(Pt(0f, 0f), Pt(120f, 0f), W))
    }

    @Test
    fun `resample spaces points evenly`() {
        val r = SwipeDecoder.resample(listOf(Pt(0f, 0f), Pt(90f, 0f)), 4)
        assertEquals(listOf(0f, 30f, 60f, 90f), r.map { Math.round(it.x).toFloat() })
    }
}
