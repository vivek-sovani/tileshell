package com.tileshell.feature.keyboard

/** A typing language. English is always on; मराठी and हिन्दी are chosen in settings. */
enum class KeyboardLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    MARATHI("mr", "मराठी"),
    HINDI("hi", "हिन्दी"),
    ;

    val indic: Boolean get() = this != ENGLISH
}

/**
 * English letters → Devanagari, the casual way people type मराठी / हिन्दी
 * ("namaskar", "dhanyavad", "maza"). Typed letters are ambiguous (t is त or
 * ट, a final a is usually ा, n before a consonant can be ं), so the rules
 * produce several spellings, each with a cost; a word list then ranks the
 * real words first. Pure, so it's unit-tested.
 */
object Transliterator {

    private const val VIRAMA = "्"
    private const val ANUSVARA = "ं"
    private const val BEAM = 48
    private const val REAL_WORD = 300f
    private const val COST_WEIGHT = 20f

    private data class Opt(val text: String, val cost: Float)

    private sealed interface Unit
    private data class Cons(val options: List<Opt>) : Unit
    private data class Vowel(
        val independent: List<Opt>,
        val matra: List<Opt>,
        val matraFinal: List<Opt> = matra,
    ) : Unit
    private data class Mark(val text: String) : Unit
    private data class Other(val text: String) : Unit

    private fun o(vararg pairs: Pair<String, Float>) = pairs.map { Opt(it.first, it.second) }
    private fun one(text: String) = listOf(Opt(text, 0f))

    private val vowels: Map<String, Vowel> = mapOf(
        "a" to Vowel(o("अ" to 0f, "आ" to 0.9f), o("" to 0f, "ा" to 1f), o("ा" to 0f, "" to 0.6f)),
        "aa" to Vowel(one("आ"), one("ा")),
        "A" to Vowel(one("आ"), one("ा")),
        "i" to Vowel(o("इ" to 0f, "ई" to 0.9f), o("ि" to 0f, "ी" to 0.9f), o("ी" to 0f, "ि" to 0.5f)),
        "ii" to Vowel(one("ई"), one("ी")),
        "ee" to Vowel(one("ई"), one("ी")),
        "I" to Vowel(one("ई"), one("ी")),
        "u" to Vowel(o("उ" to 0f, "ऊ" to 0.9f), o("ु" to 0f, "ू" to 0.9f), o("ू" to 0.2f, "ु" to 0.4f)),
        "uu" to Vowel(one("ऊ"), one("ू")),
        "oo" to Vowel(one("ऊ"), one("ू")),
        "U" to Vowel(one("ऊ"), one("ू")),
        "e" to Vowel(one("ए"), one("े")),
        "ai" to Vowel(one("ऐ"), one("ै")),
        "ei" to Vowel(one("ऐ"), one("ै")),
        "o" to Vowel(o("ओ" to 0f, "ऑ" to 1.2f), o("ो" to 0f, "ॉ" to 1.2f)),
        "au" to Vowel(one("औ"), one("ौ")),
        "ou" to Vowel(one("औ"), one("ौ")),
        "Ru" to Vowel(one("ऋ"), one("ृ")),
        "R" to Vowel(one("ऋ"), one("ृ")),
    )

    private fun consonants(lang: KeyboardLanguage): Map<String, List<Opt>> {
        val marathi = lang == KeyboardLanguage.MARATHI
        return mapOf(
            "ksh" to one("क्ष"), "x" to one("क्ष"),
            "dny" to one("ज्ञ"), "gy" to o("ग्य" to 0f, "ज्ञ" to 0.5f),
            "chh" to one("छ"), "shh" to one("ष"),
            "kh" to one("ख"), "gh" to one("घ"),
            "ch" to o("च" to 0f, "छ" to 1f),
            "jh" to one("झ"),
            "th" to o("थ" to 0f, "ठ" to 1.2f), "Th" to one("ठ"),
            "dh" to o("ध" to 0f, "ढ" to 1.2f), "Dh" to one("ढ"),
            "ph" to one("फ"), "bh" to one("भ"),
            "sh" to o("श" to 0f, "ष" to 1f), "Sh" to one("ष"),
            "k" to one("क"), "q" to one("क"), "c" to one("क"),
            "g" to one("ग"), "j" to one("ज"),
            "t" to o("त" to 0f, "ट" to 1.2f), "T" to one("ट"),
            "d" to o("द" to 0f, "ड" to 1.2f), "D" to one("ड"),
            "n" to o("न" to 0f, "ण" to 1.5f), "N" to one("ण"),
            "p" to one("प"),
            "f" to if (marathi) one("फ") else o("फ" to 0f, "फ़" to 0.5f),
            "b" to one("ब"), "m" to one("म"), "y" to one("य"), "r" to one("र"),
            "l" to if (marathi) o("ल" to 0f, "ळ" to 1.5f) else one("ल"),
            "L" to if (marathi) one("ळ") else one("ल"),
            "v" to one("व"), "w" to one("व"),
            "s" to one("स"), "h" to one("ह"),
            // Marathi writes झ for z ("maza" → माझा); Hindi uses ज़.
            "z" to if (marathi) o("झ" to 0f, "ज" to 1f) else o("ज़" to 0f, "ज" to 0.4f, "झ" to 1f),
        )
    }

    private val marks = mapOf("M" to ANUSVARA, "H" to "ः")

    /** Splits typed letters into sounds, longest match first (exact case, then lower). */
    private fun tokenize(latin: String, cons: Map<String, List<Opt>>): List<Unit> {
        val out = ArrayList<Unit>()
        var i = 0
        while (i < latin.length) {
            var matched: Pair<Unit, Int>? = null
            for (len in 3 downTo 1) {
                if (i + len > latin.length) continue
                val piece = latin.substring(i, i + len)
                for (key in listOf(piece, piece.lowercase())) {
                    val unit: Unit? = marks[key]?.let(::Mark)
                        ?: vowels[key]
                        ?: cons[key]?.let(::Cons)
                    if (unit != null) {
                        matched = unit to len
                        break
                    }
                }
                if (matched != null) break
            }
            if (matched == null) {
                out += Other(latin[i].toString())
                i++
            } else {
                out += matched.first
                i += matched.second
            }
        }
        return out
    }

    private data class State(
        val text: String,
        val cost: Float,
        /** Ends in a consonant with no vowel yet (gets a virama if another follows). */
        val pending: Boolean,
        /** That pending consonant is न or म — it may be written as ं. */
        val nasal: Boolean,
    )

    /** Possible Devanagari spellings of [latin], cheapest (most literal) first. */
    fun spellings(latin: String, lang: KeyboardLanguage): List<Pair<String, Float>> {
        if (latin.isEmpty()) return emptyList()
        val units = tokenize(latin, consonants(lang))
        var beam = listOf(State("", 0f, pending = false, nasal = false))
        units.forEachIndexed { index, unit ->
            val last = index == units.lastIndex
            val next = ArrayList<State>()
            for (s in beam) {
                when (unit) {
                    is Cons -> for (opt in unit.options) {
                        val nasal = opt.text == "न" || opt.text == "म"
                        if (s.pending) {
                            // Joined (क्ष), or the unwritten a between them (समझा).
                            next += State(s.text + VIRAMA + opt.text, s.cost + opt.cost, true, nasal)
                            next += State(s.text + opt.text, s.cost + opt.cost + 0.8f, true, nasal)
                            if (s.nasal) {
                                next += State(s.text.dropLast(1) + ANUSVARA + opt.text, s.cost + opt.cost + 0.3f, true, nasal)
                            }
                        } else {
                            next += State(s.text + opt.text, s.cost + opt.cost, true, nasal)
                            // A final n after a vowel is usually nasal: nahin → नहीं, main → मैं.
                            if (last && nasal && s.text.isNotEmpty()) {
                                next += State(s.text + ANUSVARA, s.cost + 0.4f, false, false)
                            }
                        }
                    }
                    is Vowel -> {
                        val options = when {
                            !s.pending -> unit.independent
                            last -> unit.matraFinal
                            else -> unit.matra
                        }
                        for (opt in options) next += State(s.text + opt.text, s.cost + opt.cost, false, false)
                    }
                    is Mark -> next += State(s.text + unit.text, s.cost, false, false)
                    is Other -> next += State(s.text + unit.text, s.cost, false, false)
                }
            }
            beam = next.groupBy { it.text }.map { (_, same) -> same.minBy { it.cost } }
                .sortedBy { it.cost }.take(BEAM)
        }
        return beam.map { it.text to it.cost }
    }

    /**
     * The strip's Devanagari words for [latin], best first: real words (from
     * [lexicon], most common first, nearer the literal spelling first) ahead of
     * rule-only spellings; a spelling picked for these letters before ([remembered])
     * leads. Then a couple of longer words for a word still being typed.
     */
    fun candidates(
        latin: String,
        lang: KeyboardLanguage,
        lexicon: Lexicon?,
        remembered: String? = null,
        limit: Int = 3,
    ): List<String> {
        val spelled = spellings(latin, lang)
        if (spelled.isEmpty()) return emptyList()
        val scored = spelled.map { (text, cost) ->
            val e = lexicon?.lookup(text)
            text to if (e != null) REAL_WORD + e.freq - cost * COST_WEIGHT else -cost * COST_WEIGHT
        }.sortedByDescending { it.second }
        val out = LinkedHashSet<String>()
        remembered?.let { out += it }
        // Real words first; if none, the most literal spelling.
        for ((text, score) in scored) if (score > REAL_WORD / 2 && out.size < limit) out += text
        if (out.isEmpty()) out += scored.first().first
        // Longer words for one still being typed ("namask" → नमस्कार).
        if (lexicon != null) {
            for ((prefix, _) in spelled.take(2)) {
                for (c in lexicon.completions(prefix, 2)) if (out.size < limit) out += c.word
            }
        }
        for ((text, _) in scored) if (out.size < limit) out += text
        return out.toList()
    }
}
