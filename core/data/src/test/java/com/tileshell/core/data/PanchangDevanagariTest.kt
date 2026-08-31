package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PanchangDevanagariTest {

    @Test
    fun `every vara has a Devanagari translation, not just the English fallback`() {
        VARA_NAMES.forEach { assertNotEquals(it, PanchangDevanagari.vara(it)) }
    }

    @Test
    fun `every tithi name has a Devanagari translation, not just the English fallback`() {
        (TITHI_NAMES_1_TO_14 + listOf("purnima", "amavasya")).forEach {
            assertNotEquals(it, PanchangDevanagari.tithiName(it))
        }
    }

    @Test
    fun `every month has a Devanagari translation, not just the English fallback`() {
        MONTH_NAMES.forEach { assertNotEquals(it, PanchangDevanagari.month(it)) }
    }

    @Test
    fun `every nakshatra has a Devanagari translation, not just the English fallback`() {
        NAKSHATRA_NAMES.forEach { assertNotEquals(it, PanchangDevanagari.nakshatra(it)) }
    }

    @Test
    fun `an unknown value falls back to itself, not a crash`() {
        assertEquals("no-such-vara", PanchangDevanagari.vara("no-such-vara"))
    }

    // Spot-checks against the same real reference day already calibrated in
    // HinduPanchangTest: 2026-08-30 IST = Krishna Paksha Dwitiya, Shravana, Uttara Bhadrapada, Ravivara.
    @Test
    fun `matches the real reference day in Devanagari`() {
        assertEquals("रविवार", PanchangDevanagari.vara("ravivara"))
        assertEquals("कृष्ण पक्ष", PanchangDevanagari.paksha(Paksha.KRISHNA))
        assertEquals("द्वितीया", PanchangDevanagari.tithiName("dwitiya"))
        assertEquals("श्रावण", PanchangDevanagari.month("shravana"))
        assertEquals("उत्तराभाद्रपदा", PanchangDevanagari.nakshatra("uttara bhadrapada"))
    }
}
