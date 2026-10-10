package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PanchangLanguageTest {
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val pune = 18.52 to 73.86

    private fun day(y: Int, m: Int, d: Int): Long = Calendar.getInstance(ist).apply { clear(); set(y, m - 1, d, 10, 0) }.timeInMillis

    @Test fun everyTableIsCompleteInEveryLanguage() {
        PanchangNames.allTables().forEach { (name, table) ->
            val size = table.getValue(PanchangLanguage.ENGLISH).size
            PanchangLanguage.entries.forEach { l ->
                val list = table.getValue(l)
                assertEquals("$name ${l.code}", size, list.size)
                list.forEach { assertTrue("$name ${l.code} has a blank", it.isNotBlank()) }
            }
        }
        PanchangNames.tableSizes().forEach { (name, sizes) -> assertEquals(name, sizes.second, sizes.first) }
    }

    @Test fun everyNameDiffersFromItsEnglishInTheOtherScripts() {
        PanchangNames.TITHI_KEYS.forEach { key ->
            PanchangLanguage.entries.filter { it != PanchangLanguage.ENGLISH }.forEach {
                assertNotEquals("${it.code} $key", key, PanchangNames.tithiName(it, key))
            }
        }
        PanchangNames.NAKSHATRA_KEYS.forEach { key ->
            PanchangLanguage.entries.filter { it != PanchangLanguage.ENGLISH }.forEach {
                assertNotEquals("${it.code} $key", key, PanchangNames.nakshatra(it, key))
            }
        }
    }

    @Test fun numeralsFollowTheLanguage() {
        assertEquals("१३", PanchangNames.digits(PanchangLanguage.MARATHI, 13))
        assertEquals("૧૩", PanchangNames.digits(PanchangLanguage.GUJARATI, 13))
        assertEquals("೧೩", PanchangNames.digits(PanchangLanguage.KANNADA, 13))
        assertEquals("౧౩", PanchangNames.digits(PanchangLanguage.TELUGU, 13))
        assertEquals("13", PanchangNames.digits(PanchangLanguage.TAMIL, 13))
        assertEquals("13", PanchangNames.digits(PanchangLanguage.ENGLISH, 13))
    }

    @Test fun marathiMatchesTheOldDevanagariFace() {
        assertEquals("मंगळवार", PanchangNames.vara(PanchangLanguage.MARATHI, "mangalavara"))
        assertEquals("पौर्णिमा", PanchangNames.tithiName(PanchangLanguage.MARATHI, "purnima"))
        assertEquals(PanchangDevanagari.month("shravana"), PanchangNames.month(PanchangLanguage.MARATHI, "shravana"))
    }

    @Test fun transliterationWritesTheSameWordInEachScript() {
        assertEquals("ગણેશ ચતુર્થી", IndicTransliterator.fromDevanagari("गणेश चतुर्थी", PanchangLanguage.GUJARATI))
        assertEquals("గణేశ చతుర్థి", IndicTransliterator.fromDevanagari("गणेश चतुर्थी", PanchangLanguage.TELUGU))
        assertEquals("ಕಾಮಿಕಾ", IndicTransliterator.fromDevanagari("कामिका", PanchangLanguage.KANNADA))
        assertEquals("ഏകാദശി", IndicTransliterator.fromDevanagari("एकादशी", PanchangLanguage.MALAYALAM))
        assertEquals("ஏகாதஶி", IndicTransliterator.fromDevanagari("एकादशी", PanchangLanguage.TAMIL))
        assertEquals("एकादशी", IndicTransliterator.fromDevanagari("एकादशी", PanchangLanguage.HINDI))
    }

    @Test fun everyRegionHasFestivals() {
        PanchangLanguage.entries.forEach { assertTrue(it.code, PanchangFestivals.forLanguage(it).size >= 10) }
    }

    @Test fun everyFestivalHasANameInEveryLanguage() {
        PanchangFestivals.ALL.forEach { f ->
            PanchangLanguage.entries.forEach { l -> assertTrue("${f.english} ${l.code}", f.nameIn(l).isNotBlank()) }
        }
    }

    @Test fun festivalIdsAreUniquePerRegion() {
        PanchangRegion.entries.forEach { r ->
            val list = PanchangFestivals.forRegion(r).map { it.english }
            assertEquals(r.name, list.size, list.distinct().size)
        }
    }

    private fun festivalDays(language: PanchangLanguage, y: Int, m: Int, span: Int): Map<String, Int> {
        val settings = ObservanceSettings(highlights = emptySet(), festivals = true, grahan = false, language = language)
        val cal = Calendar.getInstance(ist)
        return PanchangObservances.upcoming(day(y, m, 1), span, settings, ist).flatMap { (d, list) ->
            cal.timeInMillis = d
            list.map { it.english to cal.get(Calendar.DAY_OF_YEAR) }
        }.toMap()
    }

    @Test fun regionalFestivalsShowOnlyInTheirRegion() {
        val tamilYear = festivalDays(PanchangLanguage.TAMIL, 2026, 1, 365)
        val marathiYear = festivalDays(PanchangLanguage.MARATHI, 2026, 1, 365)
        assertTrue(tamilYear.containsKey("thai poosam"))
        assertFalse(marathiYear.containsKey("thai poosam"))
        assertTrue(marathiYear.containsKey("gudi padwa"))
        assertFalse(tamilYear.containsKey("gudi padwa"))
        assertTrue(festivalDays(PanchangLanguage.MALAYALAM, 2026, 1, 365).containsKey("onam · thiruvonam"))
    }

    @Test fun pongalAndVishuAreTheSunsEntryIntoASign() {
        val tamil = festivalDays(PanchangLanguage.TAMIL, 2026, 1, 365)
        val cal = Calendar.getInstance(ist)
        fun dayOfYear(m: Int, d: Int) = cal.apply { clear(); set(2026, m - 1, d) }.get(Calendar.DAY_OF_YEAR)
        assertEquals(dayOfYear(1, 14), tamil["pongal"])
        assertEquals(dayOfYear(4, 14), tamil["puthandu · tamil new year"])
    }

    @Test fun onamFallsAroundTheEndOfAugust() {
        val onam = festivalDays(PanchangLanguage.MALAYALAM, 2026, 8, 40)["onam · thiruvonam"]!!
        val cal = Calendar.getInstance(ist).apply { clear(); set(2026, 7, 20) }.get(Calendar.DAY_OF_YEAR)
        // Thiruvonam 2026 is on 26 August.
        assertEquals(Calendar.getInstance(ist).apply { clear(); set(2026, 7, 26) }.get(Calendar.DAY_OF_YEAR), onam)
        assertTrue(cal < onam)
    }

    @Test fun observanceNamesFollowTheLanguage() {
        val hindi = ObservanceSettings(highlights = setOf("ekadashi", "purnima", "amavasya", "sankashti"), festivals = false, language = PanchangLanguage.HINDI)
        val names = PanchangObservances.upcoming(day(2026, 10, 1), 31, hindi, ist).flatMap { it.second }.map { it.name }
        assertTrue(names.toString(), names.any { it.contains("एकादशी") })
        assertTrue(names.toString(), names.any { it.contains("पूर्णिमा") })
        val english = hindi.copy(language = PanchangLanguage.ENGLISH)
        val en = PanchangObservances.upcoming(day(2026, 10, 1), 31, english, ist).flatMap { it.second }
        en.forEach { assertEquals(it.english, it.name) }
    }

    @Test fun grahanIsNamedInEveryLanguage() {
        PanchangLanguage.entries.forEach { l ->
            Eclipse.Kind.entries.forEach { k ->
                assertTrue(PanchangObservanceNames.grahan(l, k, true).isNotBlank())
                assertTrue(PanchangObservanceNames.grahan(l, k, false).isNotBlank())
            }
        }
    }

    @Test fun customTithiNames() {
        assertEquals("शुक्ल नवमी", PanchangObservances.customName(Paksha.SHUKLA, 9, PanchangLanguage.MARATHI).first)
        assertEquals("krishna chaturthi", PanchangObservances.customName(Paksha.KRISHNA, 4, PanchangLanguage.ENGLISH).first)
        assertEquals("पौर्णिमा / अमावास्या", PanchangObservances.customName(null, 15, PanchangLanguage.MARATHI).first)
    }

    @Test fun languageCodesRoundTrip() {
        PanchangLanguage.entries.forEach { assertEquals(it, PanchangLanguage.fromCode(it.code)) }
        assertEquals(PanchangLanguage.DEFAULT, PanchangLanguage.fromCode("xx"))
        assertEquals(PanchangLanguage.DEFAULT, PanchangLanguage.fromCode(null))
    }
}
