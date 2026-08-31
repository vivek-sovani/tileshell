package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class TithiFromElongationTest {

    @Test
    fun `elongation just past 0 is shukla pratipada`() {
        val tithi = HinduPanchang.tithiFromElongation(0.5)
        assertEquals(Paksha.SHUKLA, tithi.paksha)
        assertEquals(1, tithi.tithiInPaksha)
        assertEquals("pratipada", tithi.name)
    }

    @Test
    fun `elongation just under 180 is purnima, the 15th shukla tithi`() {
        val tithi = HinduPanchang.tithiFromElongation(179.9)
        assertEquals(Paksha.SHUKLA, tithi.paksha)
        assertEquals(15, tithi.tithiInPaksha)
        assertEquals("purnima", tithi.name)
    }

    @Test
    fun `elongation just past 180 rolls to krishna paksha, dwitiya`() {
        val tithi = HinduPanchang.tithiFromElongation(192.5)
        assertEquals(Paksha.KRISHNA, tithi.paksha)
        assertEquals(2, tithi.tithiInPaksha)
        assertEquals("dwitiya", tithi.name)
    }

    @Test
    fun `elongation just under 360 is amavasya, the 15th krishna tithi`() {
        val tithi = HinduPanchang.tithiFromElongation(359.9)
        assertEquals(Paksha.KRISHNA, tithi.paksha)
        assertEquals(15, tithi.tithiInPaksha)
        assertEquals("amavasya", tithi.name)
    }

    @Test
    fun `a negative or over-360 elongation wraps the same as its normalized value`() {
        assertEquals(HinduPanchang.tithiFromElongation(200.0), HinduPanchang.tithiFromElongation(200.0 - 360.0))
        assertEquals(HinduPanchang.tithiFromElongation(10.0), HinduPanchang.tithiFromElongation(370.0))
    }

    @Test
    fun `ekadashi is the 11th tithi of either paksha`() {
        assertEquals("ekadashi", HinduPanchang.tithiFromElongation(125.0).name)
        assertEquals("ekadashi", HinduPanchang.tithiFromElongation(305.0).name)
    }
}

class TithiForRealDateTest {

    // 2026-08-30, Asia/Kolkata (IST, UTC+5:30) — a real reference point the user
    // confirmed by hand: "today is krishna dvitiya" (this file's `currentDate`
    // was 2026-08-30). Checked across the day since a tithi can roll over
    // partway through — the whole morning is Krishna Dvitiya, confirming the
    // low-precision Sun/Moon formulas are wired correctly, not just plausible.
    @Test
    fun `2026-08-30 is krishna paksha dwitiya for most of the day at IST`() {
        val midnightIst = 1788028200000L // 2026-08-30 00:00 IST
        val nineAmIst = 1788060600000L // 2026-08-30 09:00 IST
        listOf(midnightIst, nineAmIst).forEach { epochMillis ->
            val tithi = HinduPanchang.tithiFor(epochMillis)
            assertEquals(Paksha.KRISHNA, tithi.paksha)
            assertEquals(2, tithi.tithiInPaksha)
            assertEquals("dwitiya", tithi.name)
        }
    }
}

class PanchangForRealDateTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    // Same 2026-08-30 reference day, but this time checked against the full
    // Panchang (tithi + month + nakshatra + vara) the user confirmed by hand
    // after the first cut shipped with the wrong month and nakshatra:
    // "today is dwitiya" / "month sharavan" / "nakshatra uttarabhandrapada".
    // Using local midnight as the day's reference instant (the first cut's
    // choice) got the tithi right but the nakshatra wrong — 2026-08-30 sits
    // only ~2° before a nakshatra boundary at midnight IST, crossed by 6 am;
    // using "today's" own solar position for the month (rather than the
    // Sun's position at the new moon that actually starts this lunar month)
    // got the month wrong too, a full sign off. [panchangFor]'s 6 am-local
    // sunrise proxy + amanta new-moon-based month rule reproduces all three
    // at once, checked at three different hours of the same calendar day to
    // confirm a Panchang is a stable whole-day value, not a continuously
    // live-updating one.
    @Test
    fun `2026-08-30 matches the real Panchang at every hour of the day at IST`() {
        val oneAm = 1788031800000L
        val sixAm = 1788049800000L
        val twoPm = 1788078600000L
        val elevenPm = 1788111000000L
        listOf(oneAm, sixAm, twoPm, elevenPm).forEach { epochMillis ->
            val panchang = HinduPanchang.panchangFor(epochMillis, ist)
            assertEquals(Paksha.KRISHNA, panchang.tithi.paksha)
            assertEquals(2, panchang.tithi.tithiInPaksha)
            assertEquals("dwitiya", panchang.tithi.name)
            assertEquals("shravana", panchang.month)
            assertEquals("uttara bhadrapada", panchang.nakshatra)
            assertEquals("ravivara", panchang.vara)
            // Chaitra (the Hindu new year's own month) for this cycle fell in
            // March 2026 — Shaka epoch 78 CE, Vikram epoch 57 BCE.
            assertEquals(1948, panchang.shakaSamvat)
            assertEquals(2083, panchang.vikramSamvat)
        }
    }
}
