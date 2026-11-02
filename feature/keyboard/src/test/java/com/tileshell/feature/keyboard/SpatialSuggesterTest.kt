package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialSuggesterTest {

    private val lexicon = WordList.parse(sequenceOf("hat\t100", "fat\t100", "cat\t90", "gas\t40"))

    /** Evidence for one touch: log-likelihood [fit] for the listed letters, far (-30) for the rest. */
    private fun ev(vararg fit: Pair<Char, Double>): DoubleArray {
        val out = DoubleArray(26) { -30.0 }
        fit.forEach { (c, v) -> out[c - 'a'] = v }
        return out
    }

    private fun top(typed: String, evidence: List<DoubleArray>?) =
        Suggester.candidates(typed, lexicon, AutocorrectLevel.BALANCED, secondEdits = false, evidence = evidence)
            .first().entry.word

    @Test
    fun `a touch dead on its key is costly to change, one near the boundary is cheap`() {
        val clean = ev('g' to 0.0, 'h' to -8.0)
        val boundary = ev('g' to -0.2, 'h' to -0.1)
        assertEquals(32, Suggester.spatialPenalty(clean, 'g', 'h'))
        assertEquals(3, Suggester.spatialPenalty(boundary, 'g', 'h'))
        assertEquals(50, Suggester.spatialPenalty(ev('g' to 0.0), 'g', 'p'))
    }

    @Test
    fun `without evidence two neighbouring fixes tie on key distance and frequency decides`() {
        // "gat": hat and fat are both one neighbouring-key slip away, same frequency: either may lead.
        assertTrue(top("gat", null) in setOf("hat", "fat"))
    }

    @Test
    fun `the touch that nearly landed on a neighbour picks the fix`() {
        val towardsH = listOf(ev('g' to -0.3, 'h' to -0.1, 'f' to -3.0), ev('a' to 0.0), ev('t' to 0.0))
        val towardsF = listOf(ev('g' to -0.3, 'f' to -0.1, 'h' to -3.0), ev('a' to 0.0), ev('t' to 0.0))
        assertEquals("hat", top("gat", towardsH))
        assertEquals("fat", top("gat", towardsF))
    }

    @Test
    fun `evidence of the wrong length is ignored`() {
        val tooShort = listOf(ev('g' to 0.0))
        assertTrue(top("gat", tooShort) in setOf("hat", "fat"))
    }

    @Test
    fun `a clean touch makes a rare unknown word harder to change`() {
        // "cax" is not a word; "cat" (freq 90) is a far-ish slip from x. With every touch dead on its key the
        // change costs more than the word's frequency, so it is left alone; with a touch near the boundary it goes.
        val clean = listOf(ev('c' to 0.0), ev('a' to 0.0), ev('x' to 0.0, 't' to -9.0))
        val unsure = listOf(ev('c' to 0.0), ev('a' to 0.0), ev('x' to -0.2, 't' to -0.1))
        assertEquals(null, Suggester.correction("cax", lexicon, AutocorrectLevel.BALANCED, secondEdits = false, evidence = clean))
        assertEquals("cat", Suggester.correction("cax", lexicon, AutocorrectLevel.BALANCED, secondEdits = false, evidence = unsure))
    }
}
