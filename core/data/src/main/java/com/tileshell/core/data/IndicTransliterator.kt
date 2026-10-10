package com.tileshell.core.data

/**
 * Devanagari to the other Panchang scripts, for names that are the same word everywhere ("एकादशी", "गणेश
 * चतुर्थी") and only differ in the script they are written in. Gujarati, Kannada, Telugu and Malayalam share
 * Devanagari's letter order, so their letters are a fixed offset away; Tamil has fewer letters and gets a table.
 * Names that a language spells in its own way ("વિનાયક ચોથ", "விநாயகர் சதுர்த்தி") are written out in
 * [PanchangFestivals] instead of going through here.
 */
object IndicTransliterator {
    private const val DEVANAGARI_START = 0x0900
    private const val DEVANAGARI_END = 0x097F

    private val BLOCK_START = mapOf(
        PanchangLanguage.GUJARATI to 0x0A80,
        PanchangLanguage.TELUGU to 0x0C00,
        PanchangLanguage.KANNADA to 0x0C80,
        PanchangLanguage.MALAYALAM to 0x0D00,
    )

    /** [text] (Devanagari) written in [language]'s script; unchanged for Devanagari and English. */
    fun fromDevanagari(text: String, language: PanchangLanguage): String = when (language) {
        PanchangLanguage.ENGLISH, PanchangLanguage.HINDI, PanchangLanguage.MARATHI -> text
        PanchangLanguage.TAMIL -> shortFinalVowels(tamil(text), TAMIL_LONG_I, TAMIL_SHORT_I)
        PanchangLanguage.GUJARATI -> byOffset(text, language)
        else -> shortFinalI(byOffset(text, language), language)
    }

    private fun byOffset(text: String, language: PanchangLanguage): String {
        val start = BLOCK_START.getValue(language)
        return buildString {
            text.forEach { c ->
                val code = c.code
                if (code in DEVANAGARI_START..DEVANAGARI_END) append((start + code - DEVANAGARI_START).toChar()) else append(c)
            }
        }
    }

    /**
     * Hindi's "चतुर्थी" is "చతుర్థి" / "ಚತುರ್ಥಿ" / "ചതുർത്ഥി" in the south: a word-final long ī is written short.
     */
    private fun shortFinalI(text: String, language: PanchangLanguage): String {
        val start = BLOCK_START.getValue(language)
        val long = (start + 0x40).toChar() // ी
        val short = (start + 0x3F).toChar() // ि
        return shortFinalVowels(text, long, short)
    }

    private fun shortFinalVowels(text: String, long: Char, short: Char): String =
        text.split(' ').joinToString(" ") { w -> if (w.endsWith(long)) w.dropLast(1) + short else w }

    private const val TAMIL_LONG_I = 'ீ' // ீ
    private const val TAMIL_SHORT_I = 'ி' // ி

    private val TAMIL_CONSONANT = mapOf(
        'क' to "க", 'ख' to "க", 'ग' to "க", 'घ' to "க", 'ङ' to "ங",
        'च' to "ச", 'छ' to "ச", 'ज' to "ஜ", 'झ' to "ஜ", 'ञ' to "ஞ",
        'ट' to "ட", 'ठ' to "ட", 'ड' to "ட", 'ढ' to "ட", 'ण' to "ண",
        'त' to "த", 'थ' to "த", 'द' to "த", 'ध' to "த", 'न' to "ந",
        'प' to "ப", 'फ' to "ப", 'ब' to "ப", 'भ' to "ப", 'म' to "ம",
        'य' to "ய", 'र' to "ர", 'ल' to "ல", 'व' to "வ", 'ळ' to "ள", 'ऱ' to "ற",
        'श' to "ஶ", 'ष' to "ஷ", 'स' to "ஸ", 'ह' to "ஹ",
    )

    private val TAMIL_VOWEL = mapOf(
        'अ' to "அ", 'आ' to "ஆ", 'इ' to "இ", 'ई' to "ஈ", 'उ' to "உ", 'ऊ' to "ஊ", 'ऋ' to "ரு",
        'ए' to "ஏ", 'ऐ' to "ஐ", 'ओ' to "ஓ", 'औ' to "ஔ",
    )

    private val TAMIL_MATRA = mapOf(
        'ा' to "ா", 'ि' to "ி", 'ी' to "ீ", 'ु' to "ு", 'ू' to "ூ", 'ृ' to "ிரு",
        'े' to "ே", 'ै' to "ை", 'ो' to "ோ", 'ौ' to "ௌ",
    )

    private fun tamil(text: String): String {
        // Pulli (்) after a consonant marks it as having no vowel — Devanagari's virama; a consonant with no matra after
        // it carries the inherent "a", which Tamil leaves unwritten, so only the virama changes anything.
        return buildString {
            text.forEach { c ->
                when {
                    c in TAMIL_CONSONANT -> append(TAMIL_CONSONANT.getValue(c))
                    c in TAMIL_VOWEL -> append(TAMIL_VOWEL.getValue(c))
                    c in TAMIL_MATRA -> append(TAMIL_MATRA.getValue(c))
                    c == '्' -> append('்')
                    c == 'ं' || c == 'ँ' -> append("ம்")
                    c == 'ः' -> append('ஃ')
                    c == '़' -> Unit
                    else -> append(c)
                }
            }
        }
    }
}
