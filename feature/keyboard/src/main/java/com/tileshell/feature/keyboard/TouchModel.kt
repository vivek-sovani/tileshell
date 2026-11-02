package com.tileshell.feature.keyboard

import kotlin.math.ln

/**
 * Which letter a touch was meant for, given the letters typed so far in the
 * word: the way the Windows Phone keyboard grew the invisible target of a likely
 * next letter ("th" → "e") while the drawn keys never moved.
 *
 * Each candidate letter near the touch is scored as the touch's closeness to the
 * key's centre (a Gaussian, [sigma] in key widths) plus [priorWeight] times the
 * log of how likely that letter is next ([PrefixLetterModel]). Two guard rails
 * keep it from ever being annoying:
 *  - a touch inside a key's central part (the box shrunk by [coreInset] of a key
 *    width on every side) is always that key, so the prior only moves the
 *    boundaries between neighbours;
 *  - a letter's prior never drops below [floorLn], so any letter stays typable.
 * Function keys (shift, backspace, space…) are never chosen by the prior and
 * never lose a touch to it: when the nearest key is not a letter, it wins.
 *
 * Pure, so it is unit-tested and the typing-accuracy harness drives it directly.
 */
class TouchModel(
    val sigma: Float = 0.25f,
    val priorWeight: Float = 1f,
    val coreInset: Float = 0.22f,
    val reach: Float = 0.6f,
    val floorLn: Float = -4f,
) {

    /**
     * The key for a touch at ([x], [y]) as (row, index). [logPriors] is the natural
     * log of each letter a–z's probability as the next letter, or null for plain
     * nearest-key choice (no prefix known, a field that gets no smart touch).
     */
    fun choose(
        boxes: List<List<KeyBox>>,
        rows: List<KeyRow>,
        x: Float,
        y: Float,
        logPriors: DoubleArray?,
    ): Pair<Int, Int>? {
        val nearest = KeyGeometry.hit(boxes, x, y) ?: return null
        if (logPriors == null) return nearest
        val (nr, ni) = nearest
        if (!isLetter(rows[nr].keys[ni])) return nearest
        val box = boxes[nr][ni]
        val inset = coreInset * box.width
        if (x >= box.left + inset && x <= box.right - inset && y >= box.top + inset && y <= box.bottom - inset) return nearest

        val kw = box.width
        val reachPx2 = (reach * kw) * (reach * kw)
        var best = nearest
        var bestScore = Double.NEGATIVE_INFINITY
        for (r in boxes.indices) {
            for (i in boxes[r].indices) {
                val key = rows[r].keys[i]
                if (!isLetter(key)) continue
                val b = boxes[r][i]
                if (b.distance2(x, y) > reachPx2) continue
                val dx = (x - b.centerX) / kw
                val dy = (y - b.centerY) / kw
                val closeness = -(dx * dx + dy * dy) / (2.0 * sigma * sigma)
                val prior = maxOf(logPriors[key.label[0] - 'a'], floorLn.toDouble())
                val score = closeness + priorWeight * prior
                if (score > bestScore) {
                    bestScore = score
                    best = r to i
                }
            }
        }
        return best
    }

    private fun isLetter(key: Key) = key.kind == KeyKind.CHAR && key.label.length == 1 && key.label[0] in 'a'..'z'
}

/**
 * How likely each letter is to come next after a typed prefix, from the
 * language's word list weighted by how common each word is. Cached per prefix
 * (a touch must not wait on a scan of thousands of words); used from one thread.
 * A prefix no word starts with, or with anything but a–z in it, has no prior.
 */
class PrefixLetterModel(private val lexicon: Lexicon, private val floor: Double = 0.04) {

    private val cache = object : LinkedHashMap<String, DoubleArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DoubleArray>?) = size > MAX_CACHED
    }

    /** Natural logs of the 26 next-letter probabilities for [prefixLower], or null. */
    fun logPriors(prefixLower: String): DoubleArray? {
        if (prefixLower.any { it !in 'a'..'z' }) return null
        val hit = cache[prefixLower] ?: compute(prefixLower).also { cache[prefixLower] = it }
        return hit.takeIf { it.isNotEmpty() }
    }

    private fun compute(prefix: String): DoubleArray {
        val w = lexicon.nextLetterWeights(prefix) ?: return NONE
        val total = w.sum()
        if (total <= 0.0) return NONE
        return DoubleArray(26) { ln((1 - floor) * w[it] / total + floor / 26) }
    }

    private companion object {
        const val MAX_CACHED = 600
        val NONE = DoubleArray(0)
    }
}
