package com.tileshell.feature.livetiles

import com.tileshell.core.data.Observance
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservanceStripToneTest {
    private fun highlight(id: String) = Observance(id, id, id, festival = false)
    private fun festival(name: String) = Observance("festival:$name", name, name, festival = true)

    @Test
    fun `amavasya is black and purnima is white`() {
        assertEquals(StripTone.BLACK, observanceStripTone(listOf(highlight("amavasya")), 30))
        assertEquals(StripTone.WHITE, observanceStripTone(listOf(highlight("purnima")), 15))
    }

    @Test
    fun `ekadashi and the other highlights stay amber`() {
        assertEquals(StripTone.AMBER, observanceStripTone(listOf(highlight("ekadashi")), 11))
        assertEquals(StripTone.AMBER, observanceStripTone(listOf(highlight("sankashti")), 4))
        assertEquals(StripTone.AMBER, observanceStripTone(listOf(highlight("pradosh")), 13))
    }

    @Test
    fun `a festival on the new or full moon takes the same colours`() {
        assertEquals(StripTone.WHITE, observanceStripTone(listOf(festival("guru purnima")), 15))
        assertEquals(StripTone.BLACK, observanceStripTone(listOf(festival("lakshmi pujan")), 30))
        assertEquals(StripTone.AMBER, observanceStripTone(listOf(festival("ram navami")), 9))
    }

    @Test
    fun `an unseen grahan does not decide the colour`() {
        val grahan = Observance("grahan", "grahan", "grahan", festival = false, grahan = true, visibleHere = false)
        assertEquals(StripTone.AMBER, observanceStripTone(listOf(grahan, highlight("ekadashi")), 11))
    }
}
