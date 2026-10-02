package com.tileshell.feature.keyboard

/**
 * Key rows for the Metro keyboard, from the design canvas's "Build spec" and
 * `Kb` component. Pure data — no Android — so the layouts are unit-tested.
 *
 * Widths are in key units: 1 unit = (width − 8 − 9 × 6) ÷ 10, i.e. ten letter
 * keys plus nine 6dp gaps fill the row inside the 4dp side padding.
 */
enum class KeyKind {
    /** A letter; shift turns it upper case. */
    CHAR,

    /** A symbol, digit or punctuation mark typed as is. */
    SYMBOL,
    SHIFT,
    BACKSPACE,
    SPACE,
    ENTER,

    /** &123 / abcd: letters ↔ symbols. */
    LAYER,

    /** 1/2 ↔ 2/2: the two symbol pages. */
    PAGE,
    EMOJI,

    /** Globe: the next typing language. Only there when more than one is on. */
    LANGUAGE,
}

data class Key(
    val kind: KeyKind,
    val label: String,
    val units: Float = 1f,
    /** Small text under the label (the number pad's ABC, DEF…). */
    val sub: String? = null,
    /** Shown instead of [label] (the number pad's "*#" types *). */
    val display: String? = null,
    /** The darker function-key colour for a key that isn't a function kind. */
    val functionColour: Boolean = false,
    /** Long-press choices, instead of [KeyPopups]' defaults. */
    val popup: List<String>? = null,
    /**
     * The smart vowel row: this key is the vowel sign [label] (ा) after a
     * consonant, and the full vowel [independent] (आ) anywhere else.
     */
    val independent: String? = null,
) {
    /** Grey "function" key colour rather than the lighter letter-key colour. */
    val isFunction: Boolean
        get() = functionColour || kind in FUNCTION_KINDS

    private companion object {
        val FUNCTION_KINDS = setOf(
            KeyKind.SHIFT, KeyKind.BACKSPACE, KeyKind.ENTER,
            KeyKind.LAYER, KeyKind.PAGE, KeyKind.EMOJI, KeyKind.LANGUAGE,
        )
    }
}

/** One row; [inset] centres a short row by half a key on each side (row 2). */
data class KeyRow(val keys: List<Key>, val inset: Boolean = false) {
    /** Total width in units, gaps excluded. */
    val units: Float get() = keys.sumOf { it.units.toDouble() }.toFloat()
}

enum class KeyboardLayer {
    LETTERS, SYMBOLS_1, SYMBOLS_2, EMOJI,

    /** Number and phone fields (canvas "Number & phone keypad"). */
    NUMPAD,
    CLIPBOARD,

    /** Voice typing (canvas "Voice typing"). */
    VOICE,
}

object KeyboardLayouts {
    const val SPACE_LABEL = "English"

    private fun chars(s: String) = s.map { Key(KeyKind.CHAR, it.toString()) }
    private fun symbols(s: String) = s.map { Key(KeyKind.SYMBOL, it.toString()) }

    /** The space key's label is the current language, filled in when drawn. */
    private fun bottomRow(switchLabel: String, stop: String = ".") = KeyRow(
        listOf(
            Key(KeyKind.LAYER, switchLabel, 1.5f),
            Key(KeyKind.EMOJI, "emoji"),
            Key(KeyKind.SYMBOL, ","),
            Key(KeyKind.SPACE, SPACE_LABEL, 4.5f),
            Key(KeyKind.SYMBOL, stop),
            Key(KeyKind.ENTER, "enter", 1.5f),
        ),
    )

    /** One Devanagari key per character string (क्ष, ज्ञ are one key each), with its hold choices. */
    private fun deva(vararg keys: String) = keys.map { Key(KeyKind.CHAR, it, popup = DEVANAGARI_HOLD[it]) }

    /** Hold a letter for its relatives (क → क्ष, न → ण ञ ङ); the rest of the alphabet stays in plain sight. */
    private val DEVANAGARI_HOLD: Map<String, List<String>> = mapOf(
        "क" to listOf("क्ष", "क़"), "ख" to listOf("ख़"), "ग" to listOf("ग़"),
        "ज" to listOf("ज्ञ", "ज़"), "ड" to listOf("ड़"), "ढ" to listOf("ढ़"), "फ" to listOf("फ़"),
        "न" to listOf("ण", "ञ", "ङ"), "त" to listOf("त्र", "त्त"), "द" to listOf("द्य", "द्ध", "द्व"),
        "र" to listOf("ऋ", "ृ", "र्\u200D"), "ल" to listOf("ळ"), "श" to listOf("श्र", "ष"),
        "स" to listOf("स्त", "स्व"), "ह" to listOf("ह्म", "ह्य"),
        "ं" to listOf("ँ", "ः"), "्" to listOf("ऽ"),
    )

    /**
     * The smart vowel row: vowel signs after a consonant, full vowels anywhere
     * else (at the start of a word, after a vowel) — so there's one row to look
     * at for any vowel.
     */
    private val vowelRow = KeyRow(
        listOf("ा" to "आ", "ि" to "इ", "ी" to "ई", "ु" to "उ", "ू" to "ऊ", "े" to "ए", "ै" to "ऐ",
            "ो" to "ओ", "ौ" to "औ", "ं" to "अं", "्" to "अ")
            .map { (sign, full) -> Key(KeyKind.CHAR, sign, independent = full, popup = DEVANAGARI_HOLD[sign]) },
    )

    /**
     * मराठी / हिन्दी keys in वर्णमाला order (user-chosen over InScript, which was
     * hard to find letters on and lacked क्ष, ज्ञ, ऋ): the smart vowel row, the
     * consonants क…ह as the alphabet chart runs, then ळ and the joined letters.
     */
    val devanagari: List<KeyRow> = listOf(
        vowelRow,
        KeyRow(deva("क", "ख", "ग", "घ", "ङ", "च", "छ", "ज", "झ", "ञ", "ट")),
        KeyRow(deva("ठ", "ड", "ढ", "ण", "त", "थ", "द", "ध", "न", "प", "फ")),
        KeyRow(deva("ब", "भ", "म", "य", "र", "ल", "व", "श", "ष", "स", "ह")),
        KeyRow(
            listOf(Key(KeyKind.SHIFT, "shift", 1.5f)) + deva("ळ", "क्ष", "ज्ञ", "त्र", "श्र", "ऋ", "ृ", "ः", "ँ") +
                Key(KeyKind.BACKSPACE, "backspace", 1.5f),
        ),
        bottomRow("&123", stop = "।"),
    )

    /** Shift: Devanagari digits on top, the rarer signs below; the consonants stay put. */
    val devanagariShift: List<KeyRow> = listOf(
        KeyRow(deva("०", "१", "२", "३", "४", "५", "६", "७", "८", "९", "ॐ")),
        devanagari[1],
        devanagari[2],
        devanagari[3],
        KeyRow(
            listOf(Key(KeyKind.SHIFT, "shift", 1.5f)) + deva("ॅ", "ॉ", "ऑ", "ॲ", "द्य", "र्\u200D", "़", "ऽ", "॰") +
                Key(KeyKind.BACKSPACE, "backspace", 1.5f),
        ),
        bottomRow("&123", stop = "।"),
    )

    val letters: List<KeyRow> = listOf(
        KeyRow(chars("qwertyuiop")),
        KeyRow(chars("asdfghjkl"), inset = true),
        KeyRow(
            listOf(Key(KeyKind.SHIFT, "shift", 1.5f)) + chars("zxcvbnm") +
                Key(KeyKind.BACKSPACE, "backspace", 1.5f),
        ),
        bottomRow("&123"),
    )

    /** Page 1 is the design's symbol layer exactly. */
    val symbols1: List<KeyRow> = listOf(
        KeyRow(symbols("1234567890")),
        KeyRow(symbols("@#\$%&*-+()")),
        KeyRow(
            listOf(Key(KeyKind.PAGE, "1/2", 1.5f)) + symbols("!\"':;/?") +
                Key(KeyKind.BACKSPACE, "backspace", 1.5f),
        ),
        bottomRow("abcd"),
    )

    /**
     * Page 2 isn't drawn on the canvas; the less common symbols, with ₹ and the
     * other currency signs together on the middle row.
     */
    val symbols2: List<KeyRow> = listOf(
        KeyRow(symbols("~`|•√π÷×¶∆")),
        KeyRow(symbols("₹€£¥¢^°={}")),
        KeyRow(
            listOf(Key(KeyKind.PAGE, "2/2", 1.5f)) + symbols("\\©®™✓[]") +
                Key(KeyKind.BACKSPACE, "backspace", 1.5f),
        ),
        bottomRow("abcd"),
    )

    /**
     * Number and phone fields: the canvas's 4 × 4 pad — digits with their phone
     * letters, then − / space / backspace / enter down the right; *# types *
     * (hold for #, +, pauses), hold 0 for +.
     */
    val numpad: List<KeyRow> = listOf(
        KeyRow(
            listOf(
                Key(KeyKind.SYMBOL, "1"), Key(KeyKind.SYMBOL, "2", sub = "ABC"), Key(KeyKind.SYMBOL, "3", sub = "DEF"),
                Key(KeyKind.SYMBOL, "-", display = "−", functionColour = true, popup = emptyList()),
            ),
        ),
        KeyRow(
            listOf(
                Key(KeyKind.SYMBOL, "4", sub = "GHI"), Key(KeyKind.SYMBOL, "5", sub = "JKL"), Key(KeyKind.SYMBOL, "6", sub = "MNO"),
                Key(KeyKind.SPACE, "space", functionColour = true),
            ),
        ),
        KeyRow(
            listOf(
                Key(KeyKind.SYMBOL, "7", sub = "PQRS"), Key(KeyKind.SYMBOL, "8", sub = "TUV"), Key(KeyKind.SYMBOL, "9", sub = "WXYZ"),
                Key(KeyKind.BACKSPACE, "backspace"),
            ),
        ),
        KeyRow(
            listOf(
                Key(KeyKind.SYMBOL, "*", display = "*#", functionColour = true, popup = listOf("#", "+", "(", ")", ",", ";", "/", "N")),
                Key(KeyKind.SYMBOL, "0", sub = "+", popup = listOf("+")),
                Key(KeyKind.SYMBOL, ".", functionColour = true, popup = listOf(",")),
                Key(KeyKind.ENTER, "enter"),
            ),
        ),
    )

    /**
     * The rows for [layer]. With [languageKey] (more than one language on) the
     * bottom row gains the globe after emoji, and space gives it one unit.
     */
    fun rowsFor(
        layer: KeyboardLayer,
        languageKey: Boolean = false,
        devanagariKeys: Boolean = false,
        shifted: Boolean = false,
    ): List<KeyRow> {
        val rows = when (layer) {
            KeyboardLayer.LETTERS -> if (!devanagariKeys) letters else if (shifted) devanagariShift else devanagari
            KeyboardLayer.SYMBOLS_1 -> symbols1
            KeyboardLayer.SYMBOLS_2 -> symbols2
            KeyboardLayer.NUMPAD -> return numpad
            KeyboardLayer.EMOJI, KeyboardLayer.CLIPBOARD, KeyboardLayer.VOICE -> return emptyList()
        }
        if (!languageKey) return rows
        val bottom = rows.last().keys.flatMap { key ->
            when (key.kind) {
                KeyKind.EMOJI -> listOf(key, Key(KeyKind.LANGUAGE, "language"))
                KeyKind.SPACE -> listOf(key.copy(units = key.units - 1f))
                else -> listOf(key)
            }
        }
        return rows.dropLast(1) + KeyRow(bottom)
    }
}
