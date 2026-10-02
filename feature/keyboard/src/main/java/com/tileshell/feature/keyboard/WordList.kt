package com.tileshell.feature.keyboard

import java.util.BitSet

/** One dictionary word as the suggester sees it. */
data class LexEntry(
    /** How it's written: "the", "I", "London". */
    val word: String,
    /** 0–255, higher = more common (AOSP's scale). */
    val freq: Int,
    /** A real word that's never offered as a suggestion (offensive). */
    val blocked: Boolean = false,
)

/** Anything words can be looked up in: the bundled list, learned words, or both. */
interface Lexicon {
    /** The entry for this spelling, case-insensitively. */
    fun lookup(lower: String): LexEntry?

    /** The most common words starting with [prefixLower], most common first. */
    fun completions(prefixLower: String, limit: Int): List<LexEntry>

    /** A fixed correction for a non-word ("im" → "I'm"), if any. */
    fun shortcut(lower: String): String? = null

    /** Every word starting with [prefixLower], in no particular order (swipe typing). */
    fun startingWith(prefixLower: String): Sequence<LexEntry> = completions(prefixLower, 200).asSequence()
}

/**
 * The bundled word list (AOSP LatinIME's en_GB list, Apache 2.0 — see
 * `assets/keyboard/NOTICE`). ~157k words, so it's packed rather than held as a
 * map: every lower-case spelling sits in one string, sorted, found by binary
 * search; frequencies are one byte each. A word whose usual spelling isn't
 * lower case ("I", "London") keeps it in a small side map.
 */
class WordList private constructor(
    private val keys: String,
    private val starts: IntArray,
    private val freqs: ByteArray,
    private val blocked: BitSet,
    private val display: Map<Int, String>,
    private val shortcuts: Map<String, String>,
) : Lexicon {

    val size: Int get() = freqs.size

    override fun lookup(lower: String): LexEntry? {
        val i = indexOf(lower)
        return if (i < 0) null else entry(i)
    }

    override fun completions(prefixLower: String, limit: Int): List<LexEntry> {
        if (prefixLower.isEmpty() || limit <= 0) return emptyList()
        val best = ArrayList<Int>(limit + 1)
        var i = lowerBound(prefixLower)
        var scanned = 0
        while (i < size && startsWith(i, prefixLower) && scanned < MAX_SCAN) {
            if (!blocked[i]) insertByFreq(best, i, limit)
            i++
            scanned++
        }
        return best.map(::entry)
    }

    override fun shortcut(lower: String): String? = shortcuts[lower]

    /** Every word (romanising the Marathi / Hindi lists for swipe typing). */
    fun entries(): Sequence<LexEntry> = (0 until size).asSequence().map(::entry)

    override fun startingWith(prefixLower: String): Sequence<LexEntry> = sequence {
        var i = lowerBound(prefixLower)
        while (i < size && startsWith(i, prefixLower)) {
            if (!blocked[i]) yield(entry(i))
            i++
        }
    }

    private fun entry(i: Int) = LexEntry(
        word = display[i] ?: keys.substring(starts[i], starts[i + 1]),
        freq = freqs[i].toInt() and 0xFF,
        blocked = blocked[i],
    )

    private fun insertByFreq(best: ArrayList<Int>, i: Int, limit: Int) {
        val f = freqs[i].toInt() and 0xFF
        var at = best.size
        while (at > 0 && (freqs[best[at - 1]].toInt() and 0xFF) < f) at--
        if (at >= limit) return
        best.add(at, i)
        if (best.size > limit) best.removeAt(best.size - 1)
    }

    /** Compares key [i] with [s]: <0, 0, >0 as String.compareTo. */
    private fun compareKey(i: Int, s: String): Int {
        val start = starts[i]
        val len = starts[i + 1] - start
        val n = minOf(len, s.length)
        for (k in 0 until n) {
            val d = keys[start + k] - s[k]
            if (d != 0) return d
        }
        return len - s.length
    }

    private fun startsWith(i: Int, prefix: String): Boolean {
        val start = starts[i]
        if (starts[i + 1] - start < prefix.length) return false
        return keys.regionMatches(start, prefix, 0, prefix.length)
    }

    private fun indexOf(s: String): Int {
        val i = lowerBound(s)
        return if (i < size && compareKey(i, s) == 0) i else -1
    }

    private fun lowerBound(s: String): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareKey(mid, s) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /** Bounds a one-letter prefix scan (thousands of words) per keystroke. */
        private const val MAX_SCAN = 25_000

        /**
         * Parses the asset format: `word<TAB>freq[<TAB>x]` (x = never suggest) and
         * `>from<TAB>to` shortcut lines. Spellings differing only in case merge
         * into one entry: the lower-case spelling wins if it exists ("us" over
         * "US"), else the most common one ("I", "London").
         */
        fun parse(lines: Sequence<String>): WordList {
            class Acc(var freq: Int, var display: String?, var displayFreq: Int, var hasLower: Boolean, var blocked: Boolean)
            val merged = HashMap<String, Acc>(200_000)
            val shortcuts = HashMap<String, String>()
            for (line in lines) {
                if (line.isEmpty()) continue
                val parts = line.split('\t')
                if (line[0] == '>') {
                    if (parts.size >= 2) shortcuts[parts[0].substring(1).lowercase()] = parts[1]
                    continue
                }
                val word = parts[0]
                if (word.isEmpty()) continue
                val freq = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 255) ?: 0
                val bad = parts.getOrNull(2) == "x"
                val key = word.lowercase()
                val acc = merged.getOrPut(key) { Acc(0, null, -1, false, false) }
                acc.freq = maxOf(acc.freq, freq)
                if (word == key) {
                    acc.hasLower = true
                } else if (freq > acc.displayFreq) {
                    acc.display = word
                    acc.displayFreq = freq
                }
                acc.blocked = acc.blocked || bad
            }
            val sorted = merged.keys.sorted()
            val sb = StringBuilder(sorted.sumOf { it.length })
            val starts = IntArray(sorted.size + 1)
            val freqs = ByteArray(sorted.size)
            val blocked = BitSet(sorted.size)
            val display = HashMap<Int, String>()
            sorted.forEachIndexed { i, key ->
                val acc = merged.getValue(key)
                starts[i] = sb.length
                sb.append(key)
                freqs[i] = acc.freq.toByte()
                if (acc.blocked) blocked.set(i)
                if (!acc.hasLower) acc.display?.let { display[i] = it }
            }
            starts[sorted.size] = sb.length
            return WordList(sb.toString(), starts, freqs, blocked, display, shortcuts)
        }
    }
}
