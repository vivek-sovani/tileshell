package com.tileshell.feature.keyboard

/**
 * English words as मराठी / हिन्दी write them, for transliteration: typed
 * "energy" offers एनर्जी, "positive" पॉजिटिव / पॉझिटिव्ह. Built from the CMU
 * Pronouncing Dictionary by `tools/keyboard/build_loanwords.py` (the spelling
 * the language's own word list uses wins); see `assets/keyboard/NOTICE`.
 *
 * Asset lines are `english<TAB>spelling[<TAB>second spelling]`, most common
 * English word first; that order ranks prefix completions.
 */
class LoanWords private constructor(
    private val keys: Array<String>,
    private val spellings: Array<List<String>>,
    /** English commonness: lower is more common. */
    private val rank: IntArray,
) {
    val size: Int get() = keys.size

    /** The spellings of the English word [latin], usual one first; empty if it isn't one. */
    operator fun get(latin: String): List<String> {
        val i = keys.binarySearch(latin.lowercase())
        return if (i >= 0) spellings[i] else emptyList()
    }

    /** The most common English words starting with [prefix] (not the word itself), as spelled. */
    fun completions(prefix: String, limit: Int): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty() || limit <= 0) return emptyList()
        var i = lowerBound(p)
        val found = ArrayList<Int>()
        while (i < keys.size && keys[i].startsWith(p) && found.size < MAX_SCAN) {
            if (keys[i] != p) found += i
            i++
        }
        return found.sortedBy { rank[it] }.take(limit).map { spellings[it].first() }
    }

    private fun lowerBound(s: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < s) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        private const val MAX_SCAN = 2_000

        /**
         * Parses [lines]; a [borrowed] table's spellings are added after the
         * language's own (Marathi also offers Hindi's पॉजिटिव after पॉझिटिव्ह).
         */
        fun parse(lines: Sequence<String>, borrowed: Sequence<String> = emptySequence()): LoanWords {
            val map = HashMap<String, Pair<Int, MutableList<String>>>()
            var order = 0
            fun add(line: String, rankOffset: Int) {
                val parts = line.split('\t')
                if (parts.size < 2 || parts[0].isEmpty()) return
                val entry = map.getOrPut(parts[0].lowercase()) { (rankOffset + order) to ArrayList() }
                for (s in parts.drop(1)) if (s.isNotEmpty() && s !in entry.second) entry.second += s
                order++
            }
            lines.forEach { add(it, 0) }
            borrowed.forEach { add(it, BORROWED_RANK) }
            val sorted = map.keys.sorted()
            return LoanWords(
                sorted.toTypedArray(),
                Array(sorted.size) { map.getValue(sorted[it]).second.toList() },
                IntArray(sorted.size) { map.getValue(sorted[it]).first },
            )
        }

        /** A word only the borrowed table has ranks after every own one. */
        private const val BORROWED_RANK = 1_000_000
    }
}
