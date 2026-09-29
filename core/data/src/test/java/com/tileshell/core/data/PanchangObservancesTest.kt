package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Checked against published 2026 Indian panchang dates (IST, Pune). */
class PanchangObservancesTest {
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val pune = 18.52 to 73.86
    private val moonrise: (Long) -> Long? = { MoonTimes.nextMoonriseMoonset(it, pune.first, pune.second).moonriseMillis }
    private val all = ObservanceSettings(highlights = PanchangObservances.HIGHLIGHTS.map { it.id }.toSet(), festivals = true)

    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance(ist).apply {
        clear(); set(y, m - 1, d, 10, 0)
    }.timeInMillis

    private fun names(y: Int, m: Int, d: Int, settings: ObservanceSettings = all) =
        PanchangObservances.on(day(y, m, d), settings, ist, moonrise).map { it.english }

    private fun firstDayOf(english: String, y: Int, m: Int, fromDay: Int, span: Int = 6): Int? =
        (fromDay until fromDay + span).firstOrNull { english in names(y, m, it) }

    // 29 Sep 2026 is a Tuesday: Angaraki.
    @Test fun sankashtiToday() = assertTrue(names(2026, 9, 29).toString(), "angaraki sankashti chaturthi" in names(2026, 9, 29))

    @Test fun plainSankashtiOnOtherDays() {
        val oct = (27..31).first { d -> names(2026, 10, d).any { "sankashti" in it } }
        assertTrue(names(2026, 10, oct).toString(), "sankashti chaturthi" in names(2026, 10, oct))
    }

    @Test fun ekadashisHaveNames() {
        // Bhadrapada krishna ekadashi is Indira; Ashwin shukla is Papankusha.
        val oct = PanchangObservances.upcoming(day(2026, 10, 1), 31, ObservanceSettings(highlights = setOf("ekadashi"), festivals = false), ist)
        assertEquals(listOf("indira", "papankusha"), oct.map { it.second.single().english.substringBefore(" ekadashi") }.distinct())
    }

    @Test fun grahanOnItsDay() {
        val s = ObservanceSettings(highlights = emptySet(), festivals = false)
        val march3 = PanchangObservances.on(day(2026, 3, 3), s, ist, location = pune)
        assertTrue(march3.toString(), march3.any { it.grahan && "lunar" in it.english })
        val aug12 = PanchangObservances.upcoming(day(2026, 8, 12), 2, s, ist, location = pune).flatMap { it.second }
        assertTrue(aug12.single().grahan)
        assertFalse(aug12.single().visibleHere)
    }

    @Test fun ganeshChaturthi() = assertEquals(14, firstDayOf("ganesh chaturthi", 2026, 9, 11))

    @Test fun mahashivaratri() = assertEquals(15, firstDayOf("mahashivaratri", 2026, 2, 12))

    @Test fun lakshmiPujan() = assertEquals(8, firstDayOf("lakshmi pujan · diwali", 2026, 11, 5))

    @Test fun gudiPadwa() = assertEquals(19, firstDayOf("gudi padwa", 2026, 3, 16))

    @Test fun rakshaBandhan() = assertEquals(28, firstDayOf("raksha bandhan", 2026, 8, 25))

    @Test fun janmashtami() = assertEquals(4, firstDayOf("janmashtami", 2026, 9, 1))

    @Test fun dussehra() = assertEquals(20, firstDayOf("dussehra", 2026, 10, 17))

    @Test fun makarSankranti() = assertEquals(14, firstDayOf("makar sankranti", 2026, 1, 12))

    @Test fun adhikJyeshthaIn2026() {
        assertTrue(HinduPanchang.lunarMonthAt(day(2026, 6, 1)).second)
        assertFalse(HinduPanchang.lunarMonthAt(day(2026, 9, 29)).second)
    }

    @Test fun eachFestivalOnceAYear() {
        val year = PanchangObservances.upcoming(day(2026, 1, 1), 365, ObservanceSettings(highlights = emptySet()), ist, moonrise)
        val counts = year.flatMap { it.second }.groupingBy { it.english }.eachCount()
        PanchangObservances.FESTIVALS.forEach { f -> assertEquals(f.english, 1, counts[f.english] ?: 0) }
    }

    @Test fun ekadashiTwiceAMonth() {
        val month = PanchangObservances.upcoming(day(2026, 10, 1), 30, ObservanceSettings(highlights = setOf("ekadashi"), festivals = false), ist)
        assertEquals(2, month.size)
    }

    @Test fun offHighlightsAndFestivalsShowNothing() =
        assertTrue(names(2026, 9, 14, ObservanceSettings(highlights = emptySet(), festivals = false)).isEmpty())

    @Test fun customTithi() {
        val s = ObservanceSettings(highlights = emptySet(), festivals = false, customTithis = setOf("k4"))
        // Chaturthi begins after sunrise on the 29th, so by the sunrise rule it is the 30th.
        assertEquals(listOf("krishna chaturthi"), names(2026, 9, 30, s))
        assertEquals(null to 11, PanchangObservances.parseCustomKey("b11"))
    }

    @Test fun festivalReplacesItsPlainTithi() {
        val kartiki = (18..24).first { "kartiki ekadashi" in names(2026, 11, it) }
        assertFalse("ekadashi" in names(2026, 11, kartiki))
    }

    @Test fun smartaAndVaishnavaEkadashi() {
        // Over a year, an ekadashi appears either once (both keep the same day)
        // or as a smarta day followed directly by a vaishnava day.
        val s = ObservanceSettings(highlights = setOf("ekadashi"), festivals = false)
        val year = PanchangObservances.upcoming(day(2026, 1, 1), 365, s, ist)
        val entries = year.map { it.first to it.second.single().english }
        entries.forEachIndexed { i, (d, e) ->
            if ("(smarta)" in e) {
                val next = entries.getOrNull(i + 1)
                assertTrue("$e has no vaishnava day after it", next != null && "(vaishnava)" in next.second && next.first - d < 26 * 3_600_000L)
            }
            if ("(vaishnava)" in e) assertTrue("(smarta)" in entries[i - 1].second)
        }
        // About two a month either way, and at least one split in the year.
        val keptDays = entries.count { "(vaishnava)" !in it.second }
        assertTrue("$keptDays", keptDays in 23..26)
        assertTrue(entries.any { "(smarta)" in it.second })
    }
}
