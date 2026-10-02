package com.tileshell.feature.keyboard

/**
 * What a long press offers (canvas "Long-press accents & numbers"): the top
 * row's digit first, then accented letters; a few symbols have variants.
 * Pure, so it's unit-tested.
 */
object KeyPopups {

    /** The digit in each top-row key's corner (q → 1 … p → 0). */
    val topRowDigits: Map<String, String> =
        "qwertyuiop".zip("1234567890").associate { (k, d) -> k.toString() to d.toString() }

    private val accents: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "į"),
        "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø", "œ", "ō"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "c" to listOf("ç", "ć", "č"),
        "n" to listOf("ñ", "ń"),
        "s" to listOf("ś", "š", "ß"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ź", "ž", "ż"),
    )

    private val symbols: Map<String, List<String>> = mapOf(
        "." to listOf("?", "!", "'", "\"", ":", ";", "-", "@"),
        "-" to listOf("–", "—", "_", "•"),
        "$" to listOf("₹", "€", "£", "¥", "¢"),
        "?" to listOf("¿"),
        "!" to listOf("¡"),
        "'" to listOf("‘", "’", "‚"),
        "\"" to listOf("“", "”", "„", "«", "»"),
        "%" to listOf("‰"),
        "0" to listOf("°"),
    )

    /**
     * The choices for a long press on [key], or empty. [upper] gives capitals;
     * [lettersOnly] (मराठी / हिन्दी typed in English letters) offers just the
     * digit, since accented Latin letters have no Devanagari spelling.
     */
    fun options(key: Key, upper: Boolean, lettersOnly: Boolean = false): List<String> = key.popup ?: when (key.kind) {
        KeyKind.CHAR -> {
            val digit = topRowDigits[key.label]
            val letters = if (lettersOnly) emptyList() else accents[key.label].orEmpty()
                .map { if (upper) it.uppercase() else it }
            listOfNotNull(digit) + letters
        }
        KeyKind.SYMBOL -> symbols[key.label].orEmpty()
        else -> emptyList()
    }
}
