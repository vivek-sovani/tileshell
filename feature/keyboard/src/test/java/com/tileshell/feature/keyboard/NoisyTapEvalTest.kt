package com.tileshell.feature.keyboard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * The baseline for touch accuracy: the most common English words are "typed" with
 * noisy taps (a Gaussian scatter around each key's centre plus a habitual offset,
 * in key widths) on the real letter layout, and the key chooser's answer is
 * checked. The wrong-key rate and the share of words with at least one wrong key
 * are what each touch-model change has to lower and never raise. Numbers are
 * printed (run with `-i`); floors fail the build if the chooser gets worse.
 */
class NoisyTapEvalTest {

    private class Scenario(val name: String, val sigma: Float, val dx: Float, val dy: Float)

    private class Result(val letters: Int, val wrongLetters: Int, val words: Int, val wrongWords: Int) {
        val letterRate get() = wrongLetters.toFloat() / letters
        val wordRate get() = wrongWords.toFloat() / words
    }

    /** What decides the key from a tap: today the nearest box, the touch shifted down by the bias. */
    private fun interface Chooser {
        fun choose(boxes: List<List<KeyBox>>, x: Float, y: Float): Pair<Int, Int>?
    }

    private val nearest = Chooser { boxes, x, y -> KeyGeometry.hit(boxes, x, y + BIAS_PX) }

    private val width = 1080f
    private val keyHeight = 150f
    private val rows = KeyboardLayouts.letters
    private val boxes = KeyGeometry.layout(rows, width, keyHeight, 24f, 18f)

    private val centres: Map<Char, Pt> = buildMap {
        rows.forEachIndexed { r, row ->
            row.keys.forEachIndexed { i, k ->
                if (k.kind == KeyKind.CHAR) put(k.label[0], Pt(boxes[r][i].centerX, boxes[r][i].centerY))
            }
        }
    }

    private val keyWidth = boxes[0][0].width

    private fun words(): List<String> =
        File("src/main/assets/keyboard/en_words.txt").readLines()
            .mapNotNull { line ->
                val parts = line.split('\t')
                val w = parts[0]
                val f = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                if (w.length >= 3 && w.all { it in 'a'..'z' }) w to f else null
            }
            .sortedByDescending { it.second }
            .take(3000)
            .map { it.first }

    private fun run(chooser: Chooser, s: Scenario): Result {
        val rnd = Random(7)
        var letters = 0
        var wrongLetters = 0
        var wrongWords = 0
        val list = words()
        for (word in list) {
            var bad = false
            for (ch in word) {
                val c = centres.getValue(ch)
                val x = c.x + (s.dx + rnd.nextGaussian().toFloat() * s.sigma) * keyWidth
                val y = c.y + (s.dy + rnd.nextGaussian().toFloat() * s.sigma) * keyWidth
                val (r, i) = chooser.choose(boxes, x, y) ?: continue
                letters++
                if (rows[r].keys[i].label != ch.toString()) {
                    wrongLetters++
                    bad = true
                }
            }
            if (bad) wrongWords++
        }
        return Result(letters, wrongLetters, list.size, wrongWords)
    }

    private val scenarios = listOf(
        Scenario("careful (sigma 0.15, centred)", 0.15f, 0f, 0f),
        Scenario("normal (sigma 0.22, a little high)", 0.22f, 0f, -0.10f),
        Scenario("fast (sigma 0.30, high and to the side)", 0.30f, 0.05f, -0.15f),
    )

    @Test
    fun `nearest box baseline`() {
        for (s in scenarios) {
            val r = run(nearest, s)
            println("BASELINE nearest-box  ${s.name}: wrong keys ${"%.2f".format(r.letterRate * 100)}%  words with a wrong key ${"%.1f".format(r.wordRate * 100)}%")
        }
        val careful = run(nearest, scenarios[0])
        val normal = run(nearest, scenarios[1])
        val fast = run(nearest, scenarios[2])
        // Floors set just above the measured baseline (see docs/KEYBOARD-ACCURACY-PLAN.md).
        assertTrue("careful ${careful.letterRate}", careful.letterRate < CAREFUL_FLOOR)
        assertTrue("normal ${normal.letterRate}", normal.letterRate < NORMAL_FLOOR)
        assertTrue("fast ${fast.letterRate}", fast.letterRate < FAST_FLOOR)
    }

    private companion object {
        /** The 4dp touch bias in pixels at the 3x density the layout above stands for. */
        const val BIAS_PX = 12f
        // Measured 2026-11-02: 0.00% / 0.56% / 4.35% wrong keys.
        const val CAREFUL_FLOOR = 0.002f
        const val NORMAL_FLOOR = 0.012f
        const val FAST_FLOOR = 0.06f
    }
}
