package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class TransliteratorTest {

    companion object {
        private lateinit var marathi: WordList
        private lateinit var hindi: WordList

        @BeforeClass
        @JvmStatic
        fun load() {
            fun list(code: String): WordList {
                val extra = File("src/main/assets/keyboard/${code}_extra.txt").readLines()
                return File("src/main/assets/keyboard/${code}_words.txt").bufferedReader()
                    .useLines { WordList.parse(it + extra.asSequence()) }
            }
            marathi = list("mr")
            hindi = list("hi")
        }
    }

    private fun mr(latin: String) = Transliterator.candidates(latin, KeyboardLanguage.MARATHI, marathi).first()
    private fun hi(latin: String) = Transliterator.candidates(latin, KeyboardLanguage.HINDI, hindi).first()

    @Test
    fun `marathi everyday words`() {
        assertEquals("नमस्कार", mr("namaskar"))
        assertEquals("धन्यवाद", mr("dhanyavad"))
        assertEquals("माझा", mr("maza"))
        assertEquals("झाला", mr("zala"))
        assertEquals("आहे", mr("aahe"))
        assertEquals("आहे", mr("ahe"))
        assertEquals("मी", mr("mi"))
        assertEquals("नाही", mr("nahi"))
        assertEquals("काय", mr("kay"))
        assertEquals("तू", mr("tu"))
        // The word list knows Pune takes ण.
        assertEquals("पुणे", mr("pune"))
    }

    @Test
    fun `longer and clustered marathi words`() {
        assertEquals("ज्येष्ठ", mr("jyeshth"))
        assertEquals("विविधतेने", mr("vividhatene"))
        assertEquals("राष्ट्र", mr("rashtra"))
        assertEquals("स्पष्ट", mr("spasht"))
        // Rules alone: sh before t / th is ष with ट / ठ.
        assertEquals("ज्येष्ठ", Transliterator.candidates("jyeshth", KeyboardLanguage.MARATHI, null).first())
        assertEquals("कष्ट", Transliterator.candidates("kasht", KeyboardLanguage.MARATHI, null).first())
    }

    @Test
    fun `hindi everyday words`() {
        assertEquals("क्या", hi("kya"))
        assertEquals("नहीं", hi("nahin"))
        assertEquals("धन्यवाद", hi("dhanyavad"))
        assertEquals("मैं", hi("main"))
        assertEquals("है", hi("hai"))
    }

    @Test
    fun `rules alone give a sensible spelling for unknown words`() {
        assertEquals("कमल", Transliterator.candidates("kamal", KeyboardLanguage.HINDI, null).first())
        assertEquals("पुने", Transliterator.candidates("pune", KeyboardLanguage.MARATHI, null).first())
        assertEquals("क्षमा", Transliterator.candidates("kshama", KeyboardLanguage.MARATHI, null).first())
        // Capitals pick the retroflex letters.
        assertEquals("टोपी", Transliterator.candidates("Topi", KeyboardLanguage.HINDI, null).first())
        assertEquals("ठाणे", Transliterator.candidates("ThaaNe", KeyboardLanguage.MARATHI, null).first())
    }

    @Test
    fun `a spelling picked before leads`() {
        val c = Transliterator.candidates("namaskar", KeyboardLanguage.MARATHI, marathi, remembered = "नमस्कर")
        assertEquals("नमस्कर", c.first())
    }

    @Test
    fun `alternatives are distinct and capped`() {
        val c = Transliterator.candidates("dhanyavad", KeyboardLanguage.MARATHI, marathi)
        assertEquals(c.size, c.toSet().size)
        assertTrue(c.size <= 3)
    }

    @Test
    fun `quick enough for every key`() {
        val start = System.nanoTime()
        repeat(50) { mr("dhanyavadaanchya") }
        val ms = (System.nanoTime() - start) / 50 / 1_000_000.0
        assertTrue("took $ms ms", ms < 30)
    }

    @Test
    fun `marathi eyelash ra, ksha said as sha, nasal before a consonant`() {
        // Tatoeba writes दुसऱ्या with a joiner; the list keeps it whole now.
        assertEquals("दुसऱ्याला", mr("dusryala"))
        assertEquals("दुसऱ्या", mr("dusrya"))
        assertEquals("क्षेत्रफळ", mr("shetrafal"))
        assertEquals("क्षेत्रफळ", mr("kshetrafal"))
        assertEquals("लिंगोबा", mr("lingoba"))
        assertEquals("दिनचर्या", mr("dincharya"))
        assertEquals("दिनचर्या", mr("dinacharya"))
        // Still joined before y / m: कन्या, जन्म.
        assertEquals("कन्या", mr("kanya"))
        assertEquals("जन्म", mr("janma"))
    }

    @Test
    fun `marathi words ending in a dot, and double t`() {
        // The dot on the last letter: typed with n / m, or a final a.
        assertEquals("जायचं", mr("jaychan"))
        assertEquals("जायचं", mr("jaychn"))
        assertEquals("करायचं", mr("karaychn"))
        assertTrue(mr3("karayacha").contains("करायचं"))
        assertTrue(mr3("vatayacha").contains("वाटायचं"))
        assertEquals("होतं", mr("hotan"))
        assertTrue(mr3("asa").contains("असं"))
        // tt is ट्ट as well as त्त.
        assertEquals("नट्टापट्टा", mr("nattapatta"))
        assertTrue(mr3("patta").contains("पट्टा"))
        assertEquals("उत्तर", mr("uttar"))
    }

    private fun mr3(latin: String) = Transliterator.candidates(latin, KeyboardLanguage.MARATHI, marathi, limit = 4)

    @Test
    fun `completions of a word being typed come from the likeliest spellings together`() {
        val got = Transliterator.candidates("karay", KeyboardLanguage.MARATHI, marathi, limit = 4)
        assertTrue(got.toString(), got.any { it == "करायचं" || it == "करायला" || it == "करायचा" })
    }

    @Test
    fun `chha, chchha and schcha`() {
        assertEquals("छायागीत", mr("chhayagit"))
        assertEquals("छायागीत", mr("chayagit"))
        assertEquals("अच्छा", mr("achha"))
        assertEquals("इच्छा", mr("ichha"))
        assertEquals("स्वच्छ", mr("swachh"))
        assertEquals("मच्छर", mr("machhar"))
        assertEquals("छत्री", mr("chhatri"))
        assertEquals("पश्चिम", mr("paschim"))
        assertEquals("निश्चय", mr("nischay"))
    }

    @Test
    fun `a sound's letters typed alone give that letter`() {
        val want = mapOf(
            "kh" to "ख", "gh" to "घ", "jh" to "झ", "th" to "थ", "dh" to "ध", "ph" to "फ", "bh" to "भ",
            "sh" to "श", "ksh" to "क्ष", "gy" to "ज्ञ", "dny" to "ज्ञ", "shr" to "श्र", "tt" to "त्त",
            "dhy" to "ध्य", "chh" to "छ", "Th" to "ठ", "Dh" to "ढ", "tr" to "त्र",
        )
        for ((typed, letter) in want) assertEquals(typed, letter, mr(typed))
    }

    @Test
    fun `hindi follows the same sound rules`() {
        val want = mapOf(
            "kh" to "ख", "gh" to "घ", "jh" to "झ", "th" to "थ", "dh" to "ध", "ph" to "फ", "bh" to "भ",
            "sh" to "श", "ksh" to "क्ष", "gy" to "ज्ञ", "dny" to "ज्ञ", "shr" to "श्र", "tt" to "त्त",
            "dhy" to "ध्य", "chh" to "छ", "Th" to "ठ", "Dh" to "ढ", "tr" to "त्र",
        )
        for ((typed, letter) in want) assertEquals(typed, letter, hi(typed))
        assertEquals("अच्छा", hi("achha"))
        assertEquals("इच्छा", hi("ichha"))
        assertEquals("छायागीत", hi("chhayagit"))
        assertEquals("पश्चिम", hi("paschim"))
        assertEquals("में", hi("mein"))
        assertEquals("मैं", hi("main"))
    }
}
