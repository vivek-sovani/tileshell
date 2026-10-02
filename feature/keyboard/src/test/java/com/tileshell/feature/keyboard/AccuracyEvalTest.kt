package com.tileshell.feature.keyboard

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.sin

/**
 * Accuracy over the most common words, not hand-picked examples: every word is
 * typed (Marathi / Hindi in casual English letters, as [Romanizer] spells them)
 * or swiped along a sloppy, finger-like path, and the keyboard's answer is
 * checked. Floors fail the build if accuracy drops; the misses are printed so
 * they can be looked at (run with `-i`).
 */
class AccuracyEvalTest {

    companion object {
        private lateinit var marathi: WordList
        private lateinit var hindi: WordList
        private lateinit var english: WordList
        private const val W = 100f

        val centres: Map<Char, Pt> = buildMap {
            listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { r, (row, off) ->
                row.forEachIndexed { c, ch -> put(ch, Pt((c + off) * W + W / 2, r * W * 1.2f + W / 2)) }
            }
        }

        private fun list(vararg names: String): WordList {
            val files = names.map { File("src/main/assets/keyboard/$it") }
            val extra = files.drop(1).flatMap { it.readLines() }
            return files.first().bufferedReader().useLines { WordList.parse(it + extra.asSequence()) }
        }

        @BeforeClass
        @JvmStatic
        fun load() {
            marathi = list("mr_words.txt", "mr_extra.txt")
            hindi = list("hi_words.txt", "hi_extra.txt")
            english = list("en_words.txt", "en_in_extra.txt", "en_chat_extra.txt")
        }

        /** A finger-like path: strokes between key centres, corners cut, a slow wobble, a drift. */
        fun sloppyPath(word: String, seed: Int): List<Pt> {
            val keys = word.mapNotNull { centres[it] }
            if (keys.isEmpty()) return emptyList()
            val raw = ArrayList<Pt>()
            for ((a, b) in keys.zipWithNext()) {
                for (s in 0 until 6) {
                    val t = s / 6f
                    raw += Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                }
            }
            raw += keys.last()
            val dx = ((seed * 37) % 31 - 15).toFloat()
            val dy = ((seed * 53) % 25 - 12).toFloat()
            return raw.indices.map { i ->
                val p = raw[maxOf(0, i - 1)]
                val c = raw[i]
                val n = raw[minOf(raw.lastIndex, i + 1)]
                Pt((p.x + 2 * c.x + n.x) / 4 + dx + 10 * sin(i * 0.5f + seed), (p.y + 2 * c.y + n.y) / 4 + dy)
            }
        }
    }

    private data class Score(val total: Int, val top1: Int, val top3: Int, val misses: List<String>) {
        val top1Rate get() = top1.toDouble() / total
        val top3Rate get() = top3.toDouble() / total
        override fun toString() =
            "top1 %.1f%% top3 %.1f%% of $total; misses: ${misses.take(25)}".format(top1Rate * 100, top3Rate * 100)
    }

    private fun score(words: List<String>, answer: (String) -> List<String>): Score {
        var t1 = 0
        var t3 = 0
        val misses = ArrayList<String>()
        for (w in words) {
            val got = answer(w)
            if (got.firstOrNull() == w) t1++
            if (w in got.take(3)) t3++ else misses += "$w→${got.take(2)}"
        }
        return Score(words.size, t1, t3, misses)
    }

    /** The [n] most common real words of [list] that romanise (skipping one-letter ones). */
    private fun common(list: WordList, lang: KeyboardLanguage, n: Int): List<String> =
        // Half-words ending in a virama (स्, दिल्) are sentence-splitting leftovers in the data.
        list.entries().filter { it.word.length >= 2 && !it.word.endsWith('\u094D') && Romanizer.romanize(it.word, lang) != null }
            .sortedByDescending { it.freq }.take(n).map { it.word }.toList()

    @Test
    fun `marathi typed in english letters`() {
        val words = common(marathi, KeyboardLanguage.MARATHI, 1500)
        val s = score(words) { w ->
            Transliterator.candidates(Romanizer.romanize(w, KeyboardLanguage.MARATHI)!!, KeyboardLanguage.MARATHI, marathi)
        }
        println("marathi typing: $s")
        assertTrue("marathi typing $s", s.top3Rate >= MARATHI_TYPING_TOP3 && s.top1Rate >= MARATHI_TOP1)
    }

    @Test
    fun `hindi typed in english letters`() {
        val words = common(hindi, KeyboardLanguage.HINDI, 1500)
        val s = score(words) { w ->
            Transliterator.candidates(Romanizer.romanize(w, KeyboardLanguage.HINDI)!!, KeyboardLanguage.HINDI, hindi)
        }
        println("hindi typing: $s")
        assertTrue("hindi typing $s", s.top3Rate >= HINDI_TYPING_TOP3 && s.top1Rate >= HINDI_TOP1)
    }

    @Test
    fun `marathi typed the casual way, silent a left out`() {
        val words = common(marathi, KeyboardLanguage.MARATHI, 1500)
        val s = score(words) { w ->
            Transliterator.candidates(Romanizer.variants(w, KeyboardLanguage.MARATHI).last(), KeyboardLanguage.MARATHI, marathi)
        }
        println("marathi casual typing: $s")
        assertTrue("marathi casual typing $s", s.top3Rate >= MARATHI_TYPING_TOP3 && s.top1Rate >= MARATHI_TOP1)
    }

    @Test
    fun `hindi typed the casual way, silent a left out`() {
        val words = common(hindi, KeyboardLanguage.HINDI, 1500)
        val s = score(words) { w ->
            Transliterator.candidates(Romanizer.variants(w, KeyboardLanguage.HINDI).last(), KeyboardLanguage.HINDI, hindi)
        }
        println("hindi casual typing: $s")
        assertTrue("hindi casual typing $s", s.top3Rate >= HINDI_TYPING_TOP3 && s.top1Rate >= HINDI_TOP1)
    }

    @Test
    fun `marathi swiped the casual way`() {
        val swipe = RomanizedLexicon(marathi.entries(), KeyboardLanguage.MARATHI)
        val decoder = SwipeDecoder(centres, W)
        val words = common(marathi, KeyboardLanguage.MARATHI, 600)
        var seed = 0
        val s = score(words) { w ->
            val path = sloppyPath(Romanizer.variants(w, KeyboardLanguage.MARATHI).last(), seed++)
            decoder.decode(path, swipe).flatMap { swipe.devanagariFor(it.word) }.distinct()
        }
        println("marathi casual swipe: $s")
        assertTrue("marathi casual swipe $s", s.top3Rate >= MARATHI_SWIPE_TOP3 && s.top1Rate >= SWIPE_TOP1)
    }

    @Test
    fun `marathi swiped`() {
        val swipe = RomanizedLexicon(marathi.entries(), KeyboardLanguage.MARATHI)
        val decoder = SwipeDecoder(centres, W)
        val words = common(marathi, KeyboardLanguage.MARATHI, 600)
        var seed = 0
        val s = score(words) { w ->
            val path = sloppyPath(Romanizer.romanize(w, KeyboardLanguage.MARATHI)!!, seed++)
            decoder.decode(path, swipe).flatMap { swipe.devanagariFor(it.word) }.distinct()
        }
        println("marathi swipe: $s")
        assertTrue("marathi swipe $s", s.top3Rate >= MARATHI_SWIPE_TOP3 && s.top1Rate >= SWIPE_TOP1)
    }

    @Test
    fun `english swiped`() {
        val decoder = SwipeDecoder(centres, W)
        val words = english.entries()
            .filter { e -> e.word.length >= 3 && e.word.all { it in 'a'..'z' } && !e.blocked }
            .sortedByDescending { it.freq }.take(600).map { it.word }.toList()
        var seed = 0
        val s = score(words) { w -> decoder.decode(sloppyPath(w, seed++), english).map { it.word.lowercase() } }
        println("english swipe: $s")
        assertTrue("english swipe $s", s.top3Rate >= ENGLISH_SWIPE_TOP3 && s.top1Rate >= SWIPE_TOP1)
    }

    // Floors just under what the keyboard reaches today (2026-10-02: Marathi typing
    // 98.3 / 100, casual 98.2 / 99.9; Hindi 95.9 / 100, casual 95.7 / 99.9; Marathi
    // swipe 98.8 / 99.7; English swipe 98.5 / 100 — first / top three, %), so a
    // change can't quietly make it worse. Raise them as it improves.
    private val MARATHI_TYPING_TOP3 = 0.99
    private val HINDI_TYPING_TOP3 = 0.99
    private val MARATHI_SWIPE_TOP3 = 0.985
    private val ENGLISH_SWIPE_TOP3 = 0.99
    private val MARATHI_TOP1 = 0.97
    private val HINDI_TOP1 = 0.94
    private val SWIPE_TOP1 = 0.97
}
