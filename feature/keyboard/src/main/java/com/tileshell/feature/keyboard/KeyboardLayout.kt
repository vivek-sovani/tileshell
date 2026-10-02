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

data class Key(val kind: KeyKind, val label: String, val units: Float = 1f) {
    /** Grey "function" key colour rather than the lighter letter-key colour. */
    val isFunction: Boolean
        get() = kind in FUNCTION_KINDS

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

enum class KeyboardLayer { LETTERS, SYMBOLS_1, SYMBOLS_2, EMOJI }

object KeyboardLayouts {
    const val SPACE_LABEL = "English"

    private fun chars(s: String) = s.map { Key(KeyKind.CHAR, it.toString()) }
    private fun symbols(s: String) = s.map { Key(KeyKind.SYMBOL, it.toString()) }

    private fun bottomRow(switchLabel: String) = KeyRow(
        listOf(
            Key(KeyKind.LAYER, switchLabel, 1.5f),
            Key(KeyKind.EMOJI, "emoji"),
            Key(KeyKind.SYMBOL, ","),
            Key(KeyKind.SPACE, SPACE_LABEL, 4.5f),
            Key(KeyKind.SYMBOL, "."),
            Key(KeyKind.ENTER, "enter", 1.5f),
        ),
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
     * The rows for [layer]. With [languageKey] (more than one language on) the
     * bottom row gains the globe after emoji, and space gives it one unit.
     */
    fun rowsFor(layer: KeyboardLayer, languageKey: Boolean = false): List<KeyRow> {
        val rows = when (layer) {
            KeyboardLayer.LETTERS -> letters
            KeyboardLayer.SYMBOLS_1 -> symbols1
            KeyboardLayer.SYMBOLS_2 -> symbols2
            KeyboardLayer.EMOJI -> return emptyList()
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

    /** The emoji grid from the canvas's emoji panel (categories come in phase 5). */
    val emoji: List<String> = listOf(
        "😀", "😂", "🥲", "😊", "😍", "🤔", "😴", "😎",
        "🙏", "👍", "👏", "🙌", "💪", "🎉", "🔥", "✨",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🌸", "🌼",
        "☀️", "🌧️", "🍵", "🍛", "🥭", "🚗", "🏠", "📍",
    )
}
