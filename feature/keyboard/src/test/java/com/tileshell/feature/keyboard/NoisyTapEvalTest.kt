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
        fun endWord() {}
    }

    private val nearest = Chooser { boxes, x, y, _ -> KeyGeometry.hit(boxes, x, y + BIAS_PX) }

    private val english: WordList by lazy {
        val f = File("src/main/assets/keyboard/en_words.txt")
        f.bufferedReader().useLines { WordList.parse(it) }
    }
    private val priors by lazy { PrefixLetterModel(english) }

    /** The touch model with the keyboard's calibration learning as it goes (density 3 px per dp). */
    private inner class Adaptive(private val model: TouchModel = TouchModel()) : Chooser {
        val calibration = TouchCalibration()
        private val learner = TapLearner(calibration)
        private var clock = 0L

        override fun choose(boxes: List<List<KeyBox>>, x: Float, y: Float, prefix: String): Pair<Int, Int>? {
            val row = (y / (keyHeight + 24f)).toInt()
            val zone = TouchCalibration.zoneOf(x, width, row)
            val (bx, by) = calibration.bias(zone)
            val hit = model.choose(boxes, rows, x + bx * DENSITY, y + by * DENSITY, priors.logPriors(prefix)) ?: return null
            val box = boxes[hit.first][hit.second]
            if (rows[hit.first].keys[hit.second].kind == KeyKind.CHAR) {
                learner.onLetter(
                    zone, Pt(x / DENSITY, y / DENSITY), Pt(box.centerX / DENSITY, box.centerY / DENSITY), box.width / DENSITY, clock++ * 150,
                )
            }
            return hit
        }

        override fun endWord() = learner.onOther()
    }

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
            chooser.endWord()
            if (bad) wrongWords++
        }
        return Result(letters, wrongLetters, list.size, wrongWords)
    }

    private val scenarios = listOf(
        Scenario("careful (sigma 0.15, centred)", 0.15f, 0f, 0f),
        Scenario("normal (sigma 0.22, a little high)", 0.22f, 0f, -0.10f),
        Scenario("fast (sigma 0.30, high and to the side)", 0.30f, 0.05f, -0.15f),
        // A grip the default 4dp does not suit: lands well above and to the right of where it aims.
        Scenario("own grip (sigma 0.25, dx +0.12, dy -0.28)", 0.25f, 0.12f, -0.28f),
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
    fun `calibration learns an unusual grip`() {
        val grip = scenarios[3]
        val plain = run(smart(), grip, common())
        val adaptive = Adaptive()
        val learned = run(adaptive, grip, common())
        println("CALIBRATE ${line("smart touch", "common", grip, plain)}")
        println("CALIBRATE ${line("+ learning", "common", grip, learned)}")
        val s = adaptive.calibration.summary()!!
        println("CALIBRATE learned mean landing ${"%.1f".format(s.meanDx)} dp across, ${"%.1f".format(s.meanDy)} dp down from ${s.touches} touches")
        // The simulated thumb lands 0.12 / -0.28 key widths (3.7 / -8.6 dp at this layout) from the centre.
        assertTrue("dy ${s.meanDy}", s.meanDy < -6.5f && s.meanDy > -10f)
        assertTrue("dx ${s.meanDx}", s.meanDx > 1.5f && s.meanDx < 5.5f)
        assertTrue("learning helps: ${learned.letterRate} vs ${plain.letterRate}", learned.letterRate < plain.letterRate * 0.8f)
        // Someone the default suits is not made worse by learning.
        for (i in 0..2) {
            val base = run(smart(), scenarios[i], common())
            val withLearning = run(Adaptive(), scenarios[i], common())
            assertTrue("scenario $i: ${withLearning.letterRate} vs ${base.letterRate}", withLearning.letterRate <= base.letterRate * 1.1f + 0.0005f)
        }
    }

    private class Typed(val text: String, val evidence: List<DoubleArray>)

    private fun typeWord(chooser: Chooser, s: Scenario, word: String, rnd: Random): Typed {
        val text = StringBuilder()
        val evidence = ArrayList<DoubleArray>()
        val tm = TouchModel()
        for (ch in word) {
            val c = centres.getValue(ch)
            val x = c.x + (s.dx + rnd.nextGaussian().toFloat() * s.sigma) * keyWidth
            val y = c.y + (s.dy + rnd.nextGaussian().toFloat() * s.sigma) * keyWidth
            val (r, i) = chooser.choose(boxes, x, y, text.toString()) ?: continue
            if (rows[r].keys[i].kind != KeyKind.CHAR) continue
            text.append(rows[r].keys[i].label)
            evidence += tm.evidence(boxes, rows, x, y + BIAS_PX)
        }
        chooser.endWord()
        return Typed(text.toString(), evidence)
    }

    @Test
    fun `spatial autocorrect against plain`() {
        val chooser = smart()
        for (s in scenarios.drop(1).take(2)) {
            val rnd = Random(11)
            var mistyped = 0
            var plainFixed = 0
            var spatialFixed = 0
            for (word in common().filter { it.length >= 4 }) {
                val t = typeWord(chooser, s, word, rnd)
                if (t.text == word || english.lookup(t.text) != null) continue
                mistyped++
                fun top(ev: List<DoubleArray>?) =
                    Suggester.candidates(t.text, english, AutocorrectLevel.BALANCED, secondEdits = false, evidence = ev)
                        .firstOrNull()?.entry?.word?.lowercase()
                if (top(null) == word) plainFixed++
                if (top(t.evidence) == word) spatialFixed++
            }
            // Names, codes and slang typed correctly: how often the keyboard wrongly changes them.
            var typedRight = 0
            var plainWrong = 0
            var spatialWrong = 0
            val rnd2 = Random(5)
            for (word in reversed()) {
                val t = typeWord(chooser, s, word, rnd2)
                if (t.text != word || english.lookup(word) != null) continue
                typedRight++
                if (Suggester.correction(t.text, english, AutocorrectLevel.BALANCED, secondEdits = false) != null) plainWrong++
                if (Suggester.correction(t.text, english, AutocorrectLevel.BALANCED, secondEdits = false, evidence = t.evidence) != null) spatialWrong++
            }
            println(
                "SPATIAL ${s.name}: mistyped words fixed to the right word — plain ${"%.1f".format(plainFixed * 100.0 / mistyped)}%, " +
                    "with touch evidence ${"%.1f".format(spatialFixed * 100.0 / mistyped)}% (of $mistyped);  " +
                    "correct non-words wrongly changed — plain ${"%.1f".format(plainWrong * 100.0 / typedRight)}%, " +
                    "with touch evidence ${"%.1f".format(spatialWrong * 100.0 / typedRight)}% (of $typedRight)",
            )
            // The touch evidence never makes corrections worse than plain edit distance does.
            assertTrue("${s.name}: fixed $spatialFixed vs $plainFixed", spatialFixed >= plainFixed)
            assertTrue("${s.name}: wrongly changed $spatialWrong vs $plainWrong", spatialWrong <= plainWrong)
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
        const val DENSITY = 3f
        // Measured 2026-11-02: 0.00% / 0.56% / 4.35% wrong keys.
        const val CAREFUL_FLOOR = 0.002f
        const val NORMAL_FLOOR = 0.012f
        const val FAST_FLOOR = 0.06f
    }
}
