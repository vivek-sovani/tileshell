package com.tileshell.feature.keyboard

/** How tall the keys are (keyboard settings → key size). */
enum class KeySize(val label: String, val scale: Float) {
    SMALL("small", 0.9f),
    NORMAL("normal", 1f),
    LARGE("large", 1.12f),
    HUGE("huge", 1.25f),
}

/**
 * The space between keys (keyboard settings → key spacing): the gap between
 * keys, between rows and at the sides, in dp. A touch between keys goes to a key
 * all the same (nothing is dead), so spacing changes how the keyboard looks, and
 * how big the keys are, not which key a touch gets.
 */
enum class KeySpacing(val label: String, val keyGap: Float, val rowGap: Float, val side: Float) {
    TIGHT("tight", 2f, 3f, 2f),
    NORMAL("normal", 6f, 8f, 4f),
    WIDE("wide", 9f, 11f, 6f),
}

/** The keyboard's measurements in dp for one size / spacing choice, orientation and row count. Pure, so unit-tested. */
data class KeyMetrics(
    val keyHeight: Float,
    val keyGap: Float,
    val rowGap: Float,
    val side: Float,
) {
    /** The height of the key area for [rows] rows: the rows and gaps, plus 2dp above and 12dp below. */
    fun areaHeight(rows: Int): Float = 2f + keyHeight * rows + rowGap * (rows - 1) + 12f

    companion object {
        /**
         * Portrait keys are 50dp (40 in landscape, where the screen is short) times the size
         * choice, which is held to 1.1 in landscape; the six Devanagari rows start at 48 (36)
         * and stop at 56 (42) so six rows don't fill the screen.
         */
        fun of(size: KeySize, spacing: KeySpacing, landscape: Boolean, sixRows: Boolean): KeyMetrics {
            val scale = if (landscape) minOf(size.scale, 1.1f) else size.scale
            val base = when {
                sixRows -> if (landscape) 36f else 48f
                else -> if (landscape) 40f else 50f
            }
            val cap = if (sixRows) (if (landscape) 42f else 56f) else Float.MAX_VALUE
            return KeyMetrics(minOf(base * scale, cap), spacing.keyGap, spacing.rowGap, spacing.side)
        }
    }
}
