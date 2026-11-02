package com.tileshell.feature.keyboard

import org.junit.Test
import java.io.File
import java.util.Random

/**
 * A harder look at glide typing than AccuracyEvalTest (which swipes only the 600
 * most common words along smooth, tidy paths): rarer words, hurried paths that cut
 * corners, noise on every sample and a thumb that lands off its aim. Prints how
 * often the right word is first / in the top three and what it is mistaken for
 * (run with `-i`); asserts nothing, it is a measuring stick for glide changes.
 */
class SwipeStressTest {

    /** Slow (minutes) and print-only: run with SWIPE_STRESS=1. */
    private fun enabled() = System.getenv("SWIPE_STRESS") != null

    private val w = 100f
    private val centres: Map<Char, Pt> = buildMap {
        listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { r, (row, off) ->
            row.forEachIndexed { c, ch -> put(ch, Pt((c + off) * w + w / 2, r * w * 1.2f + w / 2)) }
        }
    }

    private val english: WordList by lazy {
        File("src/main/assets/keyboard/en_words.txt").bufferedReader().useLines { WordList.parse(it) }
    }

    private class Style(val name: String, val noise: Float, val smooth: Int, val dx: Float, val dy: Float, val perSegment: Int)

    private fun path(word: String, s: Style, rnd: Random): List<Pt> {
        val keys = ArrayList<Char>()
        for (ch in word) if (ch in centres && keys.lastOrNull() != ch) keys += ch
        val pts = keys.map { centres.getValue(it) }
        val raw = ArrayList<Pt>()
        for ((a, b) in pts.zipWithNext()) {
            // Speed varies from stroke to stroke: more samples on a slow stroke.
            val n = (s.perSegment * (0.6f + rnd.nextFloat() * 0.8f)).toInt().coerceAtLeast(3)
            for (i in 0 until n) {
                val t = i / n.toFloat()
                raw += Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        raw += pts.last()
        // Hurried: corners cut by averaging neighbours.
        val smooth = raw.indices.map { i ->
            var x = 0f
            var y = 0f
            var n = 0
            for (k in -s.smooth..s.smooth) {
                val p = raw[(i + k).coerceIn(0, raw.lastIndex)]
                x += p.x
                y += p.y
                n++
            }
            Pt(x / n, y / n)
        }
        return smooth.map { Pt(it.x + (s.dx + rnd.nextGaussian().toFloat() * s.noise) * w, it.y + (s.dy + rnd.nextGaussian().toFloat() * s.noise) * w) }
    }

    private fun words(from: Int, to: Int) = english.entries()
        .filter { it.word.length >= 3 && it.word.all { c -> c in 'a'..'z' } && !it.blocked }
        .sortedByDescending { it.freq }.drop(from).take(to - from).map { it.word }.toList()

    private fun run(
        label: String,
        list: List<String>,
        s: Style,
        corrected: Boolean,
        decoder: SwipeDecoder = SwipeDecoder(centres, w),
        tag: String = "",
    ) {
        val rnd = Random(3)
        var t1 = 0
        var t3 = 0
        val confusions = HashMap<String, Int>()
        for (word in list) {
            var p = path(word, s, rnd)
            if (corrected) p = p.map { Pt(it.x - s.dx * w, it.y - s.dy * w) }
            val got = decoder.decode(p, english).map { it.word.lowercase() }
            if (got.firstOrNull() == word) t1++ else got.firstOrNull()?.let { confusions.merge("$word→$it", 1, Int::plus) }
            if (word in got.take(3)) t3++
        }
        println(
            "SWIPE %-22s %-34s%s$tag top1 %5.1f%% top3 %5.1f%%  (%d words)  e.g. %s".format(
                label, s.name, if (corrected) " [bias removed]" else "", t1 * 100.0 / list.size, t3 * 100.0 / list.size, list.size,
                confusions.entries.take(6).joinToString { it.key },
            ),
        )
    }

    @Test
    fun `glide typing under harder conditions`() {
        if (!enabled()) return
        val tidy = Style("tidy (noise 0.05, no offset)", 0.05f, 1, 0f, 0f, 6)
        val hurried = Style("hurried (noise 0.20, cut corners)", 0.20f, 3, 0f, 0f, 6)
        val offGrip = Style("hurried + thumb lands high/right", 0.20f, 3, 0.10f, -0.25f, 6)
        for ((label, list) in listOf("common 1-1500" to words(0, 1500), "less common 1500-5000" to words(1500, 5000))) {
            run(label, list, tidy, false)
            run(label, list, hurried, false)
            run(label, list, offGrip, false)
            run(label, list, offGrip, true)
        }
    }

    @Test
    fun `which part of a hurried swipe hurts most`() {
        if (!enabled()) return
        val list = words(0, 600)
        run("common 1-600", list, Style("noise 0.20 only", 0.20f, 1, 0f, 0f, 6), false)
        run("common 1-600", list, Style("noise 0.10 only", 0.10f, 1, 0f, 0f, 6), false)
        run("common 1-600", list, Style("corner cutting only (smooth 3)", 0.02f, 3, 0f, 0f, 6), false)
        run("common 1-600", list, Style("corner cutting only (smooth 2)", 0.02f, 2, 0f, 0f, 6), false)
        run("common 1-600", list, Style("noise 0.10 + smooth 2", 0.10f, 2, 0f, 0f, 6), false)
        run("common 1-600", list, Style("thumb offset only (0.10, -0.25)", 0.02f, 1, 0.10f, -0.25f, 6), false)
    }

    @Test
    fun `corner rounding and a looser follow check`() {
        if (!enabled()) return
        val list = words(0, 600)
        val styles = listOf(
            Style("tidy", 0.05f, 1, 0f, 0f, 6),
            Style("cut corners (smooth 2)", 0.02f, 2, 0f, 0f, 6),
            Style("cut corners (smooth 3)", 0.02f, 3, 0f, 0f, 6),
            Style("noise 0.10 + smooth 2", 0.10f, 2, 0f, 0f, 6),
        )
        for ((smoothing, follow, missed) in listOf(Triple(0, 1.3f, 1), Triple(2, 1.3f, 1), Triple(2, 2.0f, 2), Triple(3, 2.0f, 2), Triple(4, 2.5f, 3))) {
            val d = SwipeDecoder(centres, w, smoothing, follow, missed)
            for (st in styles) run("common 1-600", list, st, false, d, " [smooth $smoothing follow $follow missed $missed]")
        }
    }
}
