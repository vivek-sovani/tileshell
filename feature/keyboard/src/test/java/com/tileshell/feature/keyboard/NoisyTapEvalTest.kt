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
        fun choose(boxes: List<List<KeyBox>>, x: Float, y: Float, prefix: String): Pair<Int, Int>?
    }

    private val nearest = Chooser { boxes, x, y, _ -> KeyGeometry.hit(boxes, x, y + BIAS_PX) }

    private val english: WordList by lazy {
        val f = File("src/main/assets/keyboard/en_words.txt")
        f.bufferedReader().useLines { WordList.parse(it) }
    }
    private val priors by lazy { PrefixLetterModel(english) }

    private fun smart(model: TouchModel = TouchModel()) =
        Chooser { boxes, x, y, prefix -> model.choose(boxes, rows, x, y + BIAS_PX, priors.logPriors(prefix)) }

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

    private fun ranked(): List<String> =
        File("src/main/assets/keyboard/en_words.txt").readLines()
            .mapNotNull { line ->
                val parts = line.split('\t')
                val w = parts[0]
                val f = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                if (w.length >= 3 && w.all { it in 'a'..'z' }) w to f else null
            }
            .sortedByDescending { it.second }
            .map { it.first }

    private fun common() = ranked().take(3000)
    private fun rare() = ranked().drop(3000).take(3000)

    /** Not words at all (names, codes, slang): the prior must not hurt these much. */
    private fun reversed() = ranked().take(1000).map { it.reversed() }

    private fun run(chooser: Chooser, s: Scenario, list: List<String>): Result {
        val rnd = Random(7)
        var letters = 0
        var wrongLetters = 0
        var wrongWords = 0
        for (word in list) {
            var bad = false
            val typed = StringBuilder()
            for (ch in word) {
                val c = centres.getValue(ch)
                val x = c.x + (s.dx + rnd.nextGaussian().toFloat() * s.sigma) * keyWidth
                val y = c.y + (s.dy + rnd.nextGaussian().toFloat() * s.sigma) * keyWidth
                val (r, i) = chooser.choose(boxes, x, y, typed.toString()) ?: continue
                letters++
                typed.append(rows[r].keys[i].label)
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

    private fun line(name: String, set: String, sc: Scenario, r: Result) =
        "%-12s %-9s %-42s wrong keys %5.2f%%  words with a wrong key %5.1f%%".format(name, set, sc.name, r.letterRate * 100, r.wordRate * 100)

    @Test
    fun `nearest box baseline`() {
        for (s in scenarios) {
            println("BASELINE " + line("nearest-box", "common", s, run(nearest, s, common())))
        }
        val careful = run(nearest, scenarios[0], common())
        val normal = run(nearest, scenarios[1], common())
        val fast = run(nearest, scenarios[2], common())
        // Floors set just above the measured baseline (see docs/KEYBOARD-ACCURACY-PLAN.md).
        assertTrue("careful ${careful.letterRate}", careful.letterRate < CAREFUL_FLOOR)
        assertTrue("normal ${normal.letterRate}", normal.letterRate < NORMAL_FLOOR)
        assertTrue("fast ${fast.letterRate}", fast.letterRate < FAST_FLOOR)
    }

    @Test
    fun `touch model against nearest box`() {
        val sets = listOf("common" to common(), "rare" to rare(), "reversed" to reversed())
        val chooser = smart()
        val results = HashMap<String, Result>()
        for ((name, list) in sets) {
            for (s in scenarios) {
                val base = run(nearest, s, list)
                val model = run(chooser, s, list)
                results["$name/${s.sigma}/nearest"] = base
                results["$name/${s.sigma}/model"] = model
                println("COMPARE " + line("nearest-box", name, s, base))
                println("COMPARE " + line("touch model", name, s, model))
            }
        }
        fun rate(set: String, sc: Scenario, who: String) = results.getValue("$set/${sc.sigma}/$who").letterRate
        // On real words the model at least halves the wrong keys of a normal and a fast typist.
        for (set in listOf("common", "rare")) {
            assertTrue("$set normal", rate(set, scenarios[1], "model") < rate(set, scenarios[1], "nearest") * 0.55f)
            assertTrue("$set fast", rate(set, scenarios[2], "model") < rate(set, scenarios[2], "nearest") * 0.60f)
        }
        // A careful typist is never made worse.
        assertTrue(rate("common", scenarios[0], "model") <= rate("common", scenarios[0], "nearest") + 0.0005f)
        // Strings that are not words (names, codes) are barely affected either way.
        for (sc in scenarios) {
            assertTrue("reversed ${sc.sigma}", rate("reversed", sc, "model") <= rate("reversed", sc, "nearest") * 1.15f + 0.0005f)
        }
    }

    @Test
    fun `parameter sweep`() {
        if (System.getProperty("sweep") == null && System.getenv("TOUCH_SWEEP") == null) return
        for (sigma in listOf(0.2f, 0.25f, 0.3f)) {
            for (weight in listOf(0.5f, 1f, 1.5f)) {
                for (inset in listOf(0.15f, 0.22f, 0.3f)) {
                    val c = smart(TouchModel(sigma = sigma, priorWeight = weight, coreInset = inset))
                    val out = StringBuilder("SWEEP sigma $sigma weight $weight inset $inset:")
                    for (s in scenarios.drop(1)) {
                        out.append("  ${"%.2f".format(run(c, s, common()).letterRate * 100)}%")
                    }
                    out.append("  | reversed")
                    for (s in scenarios.drop(1)) {
                        out.append("  ${"%.2f".format(run(c, s, reversed()).letterRate * 100)}%")
                    }
                    println(out)
                }
            }
        }
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
