package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class TouchModelTest {

    private val rows = KeyboardLayouts.letters
    private val boxes = KeyGeometry.layout(rows, 1080f, 150f, 24f, 18f)
    private val model = TouchModel()

    private fun at(label: String): Pair<Int, Int> {
        rows.forEachIndexed { r, row -> row.keys.forEachIndexed { i, k -> if (k.label == label) return r to i } }
        error(label)
    }

    private fun box(label: String) = at(label).let { boxes[it.first][it.second] }

    /** Every letter equally likely except [likely], which is [ratio] times as likely. */
    private fun priors(likely: Char, ratio: Double): DoubleArray {
        val total = 25 + ratio
        return DoubleArray(26) { ln((if ('a' + it == likely) ratio else 1.0) / total) }
    }

    @Test
    fun `without priors it is the nearest key`() {
        val b = box("r")
        val x = b.right + 5f
        assertEquals(KeyGeometry.hit(boxes, x, b.centerY), model.choose(boxes, rows, x, b.centerY, null))
    }

    @Test
    fun `a touch in the middle of a key is always that key, however unlikely the letter`() {
        val b = box("r")
        assertEquals(at("r"), model.choose(boxes, rows, b.centerX, b.centerY, priors('t', 1e6)))
        assertEquals(at("r"), model.choose(boxes, rows, b.centerX + b.width * 0.25f, b.centerY, priors('t', 1e6)))
    }

    @Test
    fun `near the edge a likely neighbour takes the touch`() {
        // Just inside "r"'s right edge, nearer r than t: plain nearest says r, "t" being likely says t.
        val r = box("r")
        val x = r.right - 4f
        assertEquals(at("r"), model.choose(boxes, rows, x, r.centerY, null))
        assertEquals(at("t"), model.choose(boxes, rows, x, r.centerY, priors('t', 50.0)))
    }

    @Test
    fun `an unlikely neighbour loses a touch on the boundary`() {
        val r = box("r")
        val x = r.right + 6f   // in the gap, nearer r
        assertEquals(at("r"), model.choose(boxes, rows, x, r.centerY, priors('r', 20.0)))
    }

    @Test
    fun `the prior is floored so a rare letter stays typable`() {
        val r = box("r")
        val tiny = DoubleArray(26) { if ('a' + it == 'r') ln(1e-9) else ln(0.04) }
        // A letter with next to no probability is still reachable: in its middle, and a little in from its own edge.
        assertEquals(at("r"), model.choose(boxes, rows, r.centerX, r.centerY, tiny))
        assertEquals(at("r"), model.choose(boxes, rows, r.left + 0.15f * r.width, r.centerY, tiny))
    }

    @Test
    fun `function keys are never taken by a letter and never take a touch from the prior`() {
        val back = rows[2].keys.indexOfFirst { it.kind == KeyKind.BACKSPACE }
        val b = boxes[2][back]
        assertEquals(2 to back, model.choose(boxes, rows, b.centerX, b.centerY, priors('m', 1e6)))
        val space = rows[3].keys.indexOfFirst { it.kind == KeyKind.SPACE }
        val s = boxes[3][space]
        assertEquals(3 to space, model.choose(boxes, rows, s.centerX, s.centerY, priors('m', 1e6)))
    }

    @Test
    fun `row above can win over a touch low in the key when its letter is likely`() {
        // A touch at the very top of "d" (row 1): "e" above is very likely, so the touch goes to e.
        val d = box("d")
        val y = d.top + 3f
        val priors = priors('e', 200.0)
        assertTrue(model.choose(boxes, rows, d.centerX, y, priors)!!.first <= 1)
    }

    @Test
    fun `prefix letter model gives likely letters after a prefix and nothing for non-words`() {
        val list = WordList.parse(sequenceOf("the\t200", "then\t150", "there\t150", "thx\t5", "tap\t100"))
        val m = PrefixLetterModel(list)
        val th = m.logPriors("th")!!
        assertTrue(th['e' - 'a'] > th['x' - 'a'])
        assertTrue(th['e' - 'a'] > ln(0.5))
        assertNull(m.logPriors("zz"))
        assertNull(m.logPriors("it's"))
        // Every letter keeps a little probability, so the floor in the model never sees minus infinity.
        assertTrue(th.all { it > ln(0.04 / 26) - 1e-9 })
    }

    @Test
    fun `next letter weights follow frequency`() {
        val list = WordList.parse(sequenceOf("tea\t200", "ten\t100", "tin\t100"))
        val w = list.nextLetterWeights("t")!!
        assertTrue(w['e' - 'a'] > w['i' - 'a'])
        assertEquals(0.0, w['a' - 'a'], 0.0)
        assertNull(list.nextLetterWeights("q"))
    }

    @Test
    fun `smart touch is for ordinary text only`() {
        val text = android.text.InputType.TYPE_CLASS_TEXT
        assertTrue(TypingRules.smartTouchAllowed(text))
        assertTrue(TypingRules.smartTouchAllowed(text or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        for (v in listOf(
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            android.text.InputType.TYPE_TEXT_VARIATION_URI,
        )) assertTrue(!TypingRules.smartTouchAllowed(text or v))
        assertTrue(!TypingRules.smartTouchAllowed(android.text.InputType.TYPE_CLASS_NUMBER))
    }
}
