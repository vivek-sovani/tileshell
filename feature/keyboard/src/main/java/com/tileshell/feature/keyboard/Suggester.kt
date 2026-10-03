package com.tileshell.feature.keyboard

/** How readily a misspelt word is replaced on space (settings → autocorrect level). */
enum class AutocorrectLevel(val label: String, val minScore: Int, val secondEditMinLength: Int) {
    MILD("mild", minScore = 130, secondEditMinLength = Int.MAX_VALUE),
    BALANCED("balanced", minScore = 60, secondEditMinLength = 7),
    AGGRESSIVE("aggressive", minScore = 25, secondEditMinLength = 4),
}

/** One word in the suggestion strip. */
data class StripWord(
    val text: String,
    val kind: Kind,
    /** The best guess: what space would put in. Underlined in the accent. */
    val best: Boolean = false,
) {
    enum class Kind {
        /** The word as typed — dim when space would replace it. */
        TYPED,

        /** The quoted original right after an autocorrect: tap to undo. */
        UNDO,
        WORD,
    }
}

/**
 * Suggestions and corrections for the word being typed. Pure: the dictionary
 * comes in as a [Lexicon], so it's tested with small word lists.
 */
object Suggester {

    const val STRIP_SIZE = 4

    /** What the strip shows before anything is typed (the canvas's next-word state). */
    val NEXT_WORDS = listOf("I", "the", "will", "see")

    /**
     * The replacement space (or punctuation) would put in for [typed], or null
     * to leave it. A word in the dictionary stays, except for its usual capital
     * ("i" → "I", "london" → "London"); a fixed shortcut ("im" → "I'm") applies;
     * otherwise the best close word whose score clears the [level].
     */
    fun correction(
        typed: String,
        lexicon: Lexicon,
        level: AutocorrectLevel,
        secondEdits: Boolean = true,
    ): String? {
        if (typed.isEmpty() || typed.any { it.isDigit() }) return null
        val lower = typed.lowercase()
        lexicon.lookup(lower)?.let { known ->
            val recased = applyCase(typed, known.word)
            return if (typed == lower && recased != typed) recased else null
        }
        lexicon.shortcut(lower)?.let { return applyCase(typed, it) }
        if (typed.length < 2) return null
        val best = candidates(lower, lexicon, level, secondEdits).firstOrNull() ?: return null
        return if (best.score >= level.minScore) applyCase(typed, best.entry.word) else null
    }

    /**
     * The strip for the word being typed: the typed word first, then the best
     * guess and other completions / near words, at most [STRIP_SIZE]. Words the
     * context makes likely ([next], best first: "good mor" → morning) lead the
     * completions.
     */
    fun strip(
        typed: String,
        lexicon: Lexicon,
        level: AutocorrectLevel,
        autocorrect: Boolean,
        next: List<String> = emptyList(),
    ): List<StripWord> {
        val lower = typed.lowercase()
        val fix = if (autocorrect) correction(typed, lexicon, level, secondEdits = false) else null
        val known = lexicon.lookup(lower) != null
        val out = ArrayList<StripWord>(STRIP_SIZE)
        out += StripWord(typed, StripWord.Kind.TYPED, best = fix == null)
        val seen = hashSetOf(lower)
        fun add(word: String, best: Boolean = false) {
            if (out.size >= STRIP_SIZE) return
            if (seen.add(word.lowercase())) out += StripWord(word, StripWord.Kind.WORD, best)
        }
        if (fix != null) add(fix, best = true)
        // Rare words ("Tehuantepec") only clutter the strip as completions.
        val completions = lexicon.completions(lower, STRIP_SIZE + 1)
            .filter { it.freq >= MIN_COMPLETION_FREQ }
            .map { applyCase(typed, it.word) }
        val near = if (known) emptyList() else candidates(lower, lexicon, level, secondEdits = false)
            .take(STRIP_SIZE).map { applyCase(typed, it.entry.word) }
        val expected = next.filter { it.length > lower.length && it.lowercase().startsWith(lower) }
            .map { applyCase(typed, lexicon.lookup(it.lowercase())?.word ?: it) }
        // Completions lead while a word is being typed; near words fill in.
        (expected.take(2) + completions.take(2) + near.take(2) + completions.drop(2) + near.drop(2)).forEach { add(it) }
        return out
    }

    /** The strip right after an autocorrect: “original” (undo), the correction, alternatives. */
    fun undoStrip(original: String, corrected: String, lexicon: Lexicon): List<StripWord> {
        val out = arrayListOf(
            StripWord("“$original”", StripWord.Kind.UNDO),
            StripWord(corrected, StripWord.Kind.WORD, best = true),
        )
        val seen = hashSetOf(original.lowercase(), corrected.lowercase())
        for (c in candidates(original.lowercase(), lexicon, AutocorrectLevel.BALANCED, secondEdits = false)) {
            if (out.size >= STRIP_SIZE) break
            val w = applyCase(original, c.entry.word)
            if (seen.add(w.lowercase())) out += StripWord(w, StripWord.Kind.WORD)
        }
        return out
    }

    /**
     * Matches a suggestion to how the word was typed: ALL CAPS stays caps, a
     * typed capital stays; otherwise the dictionary's own form ("I", "London").
     */
    fun applyCase(typed: String, word: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } && typed.any { it.isLetter() } ->
            word.uppercase()
        typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    internal data class Candidate(val entry: LexEntry, val score: Int)

    /**
     * Words one edit away, best first. Two edits for longer words at higher
     * levels — only when [secondEdits] and nothing one edit away is good
     * enough, since that search is ~60× bigger.
     */
    internal fun candidates(
        lower: String,
        lexicon: Lexicon,
        level: AutocorrectLevel,
        secondEdits: Boolean = true,
    ): List<Candidate> {
        val scored = HashMap<String, Candidate>()
        fun consider(spelling: String, penalty: Int) {
            val e = lexicon.lookup(spelling) ?: return
            if (e.blocked) return
            val score = e.freq - penalty
            val key = e.word.lowercase()
            val prev = scored[key]
            if (prev == null || prev.score < score) scored[key] = Candidate(e, score)
        }
        val first = edits(lower)
        first.forEach { (s, p) -> consider(s, p) }
        val goodFirst = scored.values.any { it.score >= level.minScore }
        if (secondEdits && !goodFirst && lower.length >= level.secondEditMinLength) {
            for ((s, p) in first) {
                if (p >= SECOND_EDIT_CUTOFF) continue
                for ((s2, p2) in edits(s)) consider(s2, p + p2 + SECOND_EDIT_EXTRA)
            }
        }
        return scored.values.sortedByDescending { it.score }
    }

    /**
     * Every spelling one edit from [w], with a penalty: neighbouring-key slips
     * and swapped letters cost little, a missing apostrophe nothing, an
     * unrelated letter a lot.
     */
    internal fun edits(w: String): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>(w.length * 60)
        for (i in w.indices) {
            // Deletion: an extra letter typed (a doubled one is the usual slip).
            val doubled = (i > 0 && w[i - 1] == w[i]) || (i + 1 < w.length && w[i + 1] == w[i])
            out += (w.removeRange(i, i + 1) to if (doubled) 10 else 25)
            // Transposition.
            if (i + 1 < w.length && w[i] != w[i + 1]) {
                val c = w.toCharArray()
                c[i] = w[i + 1]
                c[i + 1] = w[i]
                out += (String(c) to 15)
            }
            // Substitution.
            for (ch in ALPHABET) {
                if (ch == w[i]) continue
                val penalty = if (KeyProximity.near(w[i], ch)) 15 else 45
                out += (w.substring(0, i) + ch + w.substring(i + 1) to penalty)
            }
        }
        // Insertion: a letter left out (a doubled one is cheap), or an apostrophe.
        for (i in 0..w.length) {
            for (ch in ALPHABET) {
                val doubled = (i > 0 && w[i - 1] == ch) || (i < w.length && w[i] == ch)
                out += (w.substring(0, i) + ch + w.substring(i) to if (doubled) 10 else 25)
            }
            if (i in 1 until w.length) out += (w.substring(0, i) + '\'' + w.substring(i) to 0)
        }
        return out
    }

    private const val MIN_COMPLETION_FREQ = 70
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
    private const val SECOND_EDIT_CUTOFF = 25
    private const val SECOND_EDIT_EXTRA = 15
}

/** Neighbouring keys on the qwerty layout, for cheap "fat finger" corrections. */
object KeyProximity {
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val rowOffset = listOf(0f, 0.5f, 1.5f)

    private val position: Map<Char, Pair<Float, Int>> = buildMap {
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> put(ch, (c + rowOffset[r]) to r) } }
    }

    fun near(a: Char, b: Char): Boolean {
        val pa = position[a] ?: return false
        val pb = position[b] ?: return false
        val dr = kotlin.math.abs(pa.second - pb.second)
        return dr <= 1 && kotlin.math.abs(pa.first - pb.first) <= 1f
    }
}
