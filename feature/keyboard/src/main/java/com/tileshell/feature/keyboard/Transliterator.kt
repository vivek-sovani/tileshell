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
    private const val BEAM = 96

    /** Spellings kept that aren't the start of any known word (new words, names). */
    private const val BEAM_LITERAL = 12
    private const val CHANDRABINDU = "\u0901"
    private const val VISARGA = "\u0903"
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
        // u is also the English-style short a of names (Burman → बर्मन).
        "u" to Vowel(o("उ" to 0f, "ऊ" to 0.9f), o("ु" to 0f, "ू" to 0.9f, "" to 1.3f), o("ू" to 0.2f, "ु" to 0.4f)),
        "uu" to Vowel(one("ऊ"), one("ू")),
        "oo" to Vowel(one("ऊ"), one("ू")),
        "U" to Vowel(one("ऊ"), one("ू")),
        "e" to Vowel(one("ए"), one("े")),
        // "ai" is ऐ, or two vowels (आई, कई, मुंबई); "au" likewise (भाऊ, जाऊ).
        "ai" to Vowel(
            o("ऐ" to 0f, "आई" to 0.4f, "अई" to 0.9f, "आइ" to 0.9f),
            o("ै" to 0f, "ाई" to 0.4f, "ई" to 0.5f, "ाइ" to 0.9f, "इ" to 0.9f),
        ),
        "ei" to Vowel(o("ऐ" to 0f, "एई" to 0.5f), o("ै" to 0f, "ेई" to 0.4f, "ेइ" to 0.8f)),
        "o" to Vowel(o("ओ" to 0f, "ऑ" to 1.2f), o("ो" to 0f, "ॉ" to 1.2f)),
        "au" to Vowel(
            o("औ" to 0f, "आऊ" to 0.4f, "अऊ" to 0.9f, "आउ" to 0.9f),
            o("ौ" to 0f, "ाऊ" to 0.4f, "ऊ" to 0.5f, "ाउ" to 0.9f, "उ" to 0.9f),
        ),
        "ou" to Vowel(o("औ" to 0f, "ओऊ" to 0.5f), o("ौ" to 0f, "ोऊ" to 0.4f, "ोउ" to 0.8f)),
        // r + u after a consonant: joined (क्रु), the ृ sign (कृपया, पृथ्वी), or a full syllable (करू).
        "ru" to Vowel(
            o("रु" to 0f, "ऋ" to 0.6f, "रू" to 0.8f),
            o("्रु" to 0f, "ृ" to 0.4f, "रु" to 0.6f, "्रू" to 0.9f, "रू" to 0.9f),
            o("्रु" to 0.2f, "ृ" to 0.5f, "रू" to 0.4f, "रु" to 0.6f, "्रू" to 0.7f),
        ),
        // ri after a consonant: joined (प्रिय), a full syllable (हरि), or ृ (कृष्ण, typed "krishna").
        "ri" to Vowel(
            o("रि" to 0f, "री" to 0.9f, "ऋ" to 0.6f),
            o("्रि" to 0f, "ृ" to 0.6f, "रि" to 0.8f, "्री" to 0.9f, "री" to 1.7f),
            o("्री" to 0f, "्रि" to 0.5f, "री" to 0.8f, "ृ" to 0.8f, "रि" to 1.3f),
        ),
        "Ru" to Vowel(one("ऋ"), one("ृ")),
        "R" to Vowel(one("ऋ"), one("ृ")),
    )

    /**
     * kh, ph, dh… are aspirates (ख, फ, ध), or a consonant then ह with the a
     * between them unwritten (एकही typed "ekhi", अपहरण "apharan", दोपहर "dophar").
     */
    private val aspirateParts = mapOf(
        "kh" to "क", "gh" to "ग", "ch" to "च", "jh" to "ज", "th" to "त", "dh" to "द",
        "ph" to "प", "bh" to "ब", "sh" to "स",
    )

    private fun consonants(lang: KeyboardLanguage): Map<String, List<Opt>> =
        baseConsonants(lang).mapValues { (key, options) ->
            val base = aspirateParts[key] ?: return@mapValues options
            options + Opt(base + "ह", 1.0f) + Opt(base + VIRAMA + "ह", 1.4f)
        }

    private fun baseConsonants(lang: KeyboardLanguage): Map<String, List<Opt>> {
        val marathi = lang == KeyboardLanguage.MARATHI
        return mapOf(
            "ksh" to o("क्ष" to 0f, "क्श" to 0.6f), "x" to one("क्ष"),
            // sh before t / th is nearly always ष with ट / ठ (ज्येष्ठ, राष्ट्र, स्पष्ट).
            "shth" to o("ष्ठ" to 0f, "श्थ" to 1.5f), "sht" to o("ष्ट" to 0f, "श्त" to 1.5f),
            "dny" to one("ज्ञ"), "gy" to o("ग्य" to 0f, "ज्ञ" to 0.5f),
            "chh" to one("छ"), "shh" to one("ष"),
            "kh" to if (marathi) one("ख") else o("ख" to 0f, "ख़" to 1.0f), "gh" to one("घ"),
            "ch" to o("च" to 0f, "छ" to 1f),
            "jh" to one("झ"),
            "th" to o("थ" to 0f, "ठ" to 1.2f), "Th" to one("ठ"),
            "dh" to if (marathi) o("ध" to 0f, "ढ" to 1.2f) else o("ध" to 0f, "ढ" to 1.2f, "ढ़" to 1.3f),
            "Dh" to one("ढ"),
            "ph" to one("फ"), "bh" to one("भ"),
            "sh" to o("श" to 0f, "ष" to 1f), "Sh" to one("ष"),
            "k" to if (marathi) one("क") else o("क" to 0f, "क़" to 1.2f),
            "q" to if (marathi) one("क") else o("क़" to 0f, "क" to 0.3f), "c" to one("क"),
            "g" to if (marathi) one("ग") else o("ग" to 0f, "ग़" to 1.2f), "j" to one("ज"),
            "t" to o("त" to 0f, "ट" to 1.2f), "T" to one("ट"),
            // Hindi's ड़ (बड़ा, लड़की) is typed d.
            "d" to if (marathi) o("द" to 0f, "ड" to 1.2f) else o("द" to 0f, "ड" to 1.2f, "ड़" to 1.0f),
            "D" to one("ड"),
            "n" to o("न" to 0f, "ण" to 1.5f), "N" to one("ण"),
            "p" to one("प"),
            "f" to if (marathi) one("फ") else o("फ" to 0f, "फ़" to 0.5f),
            "b" to one("ब"), "m" to one("म"), "y" to one("य"),
            "r" to if (marathi) one("र") else o("र" to 0f, "ड़" to 1.4f),
            "l" to if (marathi) o("ल" to 0f, "ळ" to 1.5f) else one("ल"),
            "L" to if (marathi) one("ळ") else one("ल"),
            "v" to one("व"), "w" to one("व"),
            "s" to one("स"), "h" to one("ह"),
            // Marathi writes झ for z ("maza" → माझा); Hindi uses ज़.
            "z" to if (marathi) o("झ" to 0f, "ज" to 1f) else o("ज़" to 0f, "ज" to 0.4f, "झ" to 1f),
        )
    }

    private val marks = mapOf("M" to ANUSVARA, "H" to "ः")

    /** A nasal before these stays a joined letter, not ं (कन्या, जन्म, अन्न, वाङ्मय). */
    private const val NASAL_STAYS_BEFORE = "यरवलळमनण"
    private const val NASAL_JOIN_COST = 0.3f

    /** "sh" starting a word may be क्ष, as many say it: shetra → क्षेत्र, shan → क्षण. */
    private val KSHA = Opt("क्ष", 1.0f)

    private fun consOptions(unit: Cons, index: Int, lang: KeyboardLanguage): List<Opt> =
        if (index == 0 && unit.options.any { it.text == "श" }) unit.options + KSHA else unit.options

    /** "ao": Marathi ends words in ाव (राव, गाव, नाव), Hindi in ाओ (जाओ, आओ). */
    private fun aoVowel(lang: KeyboardLanguage): Vowel {
        val marathi = lang == KeyboardLanguage.MARATHI
        return Vowel(
            o("आओ" to 0f, "आव" to 0.3f),
            o("ाओ" to 0f, "ाव" to 0.3f, "ओ" to 0.8f),
            if (marathi) o("ाव" to 0f, "ाओ" to 0.3f) else o("ाओ" to 0f, "ाव" to 0.3f),
        )
    }

    /** Splits typed letters into sounds, longest match first (exact case, then lower). */
    private fun tokenize(latin: String, cons: Map<String, List<Opt>>, lang: KeyboardLanguage): List<Unit> {
        val ao = aoVowel(lang)
        val out = ArrayList<Unit>()
        var i = 0
        while (i < latin.length) {
            var matched: Pair<Unit, Int>? = null
            for (len in 4 downTo 1) {
                if (i + len > latin.length) continue
                val piece = latin.substring(i, i + len)
                for (key in listOf(piece, piece.lowercase())) {
                    val unit: Unit? = marks[key]?.let(::Mark)
                        ?: (if (key == "ao") ao else null)
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

    /**
     * Possible Devanagari spellings of [latin], cheapest (most literal) first.
     * With a [lexicon], spellings that begin some real word survive the beam
     * however many ambiguous letters they took (त्यांच्याकडे has six), so the
     * list can rank them; a few literal ones are kept too, for new words.
     */
    fun spellings(latin: String, lang: KeyboardLanguage, lexicon: Lexicon? = null): List<Pair<String, Float>> {
        if (latin.isEmpty()) return emptyList()
        val units = tokenize(latin, consonants(lang), lang)
        // Hindi writes ँ often (हूँ, माँ, कहाँ); Marathi rarely (सँडविच).
        val chandraCost = if (lang == KeyboardLanguage.HINDI) 0.2f else 0.8f
        var beam = listOf(State("", 0f, pending = false, nasal = false))
        units.forEachIndexed { index, unit ->
            val last = index == units.lastIndex
            val next = ArrayList<State>()
            for (s in beam) {
                when (unit) {
                    is Cons -> for (opt in consOptions(unit, index, lang)) {
                        val nasal = opt.text == "न" || opt.text == "म"
                        if (s.pending) {
                            // n / m before a consonant is usually ं (लिंग, पंख, संध्या, अंबर), but
                            // stays joined before y, r, v, l, m, n (कन्या, जन्म, अन्न).
                            val joinedNasal = s.nasal && opt.text.first() in NASAL_STAYS_BEFORE
                            val joinCost = if (s.nasal && !joinedNasal) NASAL_JOIN_COST else 0f
                            // Joined (क्ष), or the unwritten a between them (समझा).
                            next += State(s.text + VIRAMA + opt.text, s.cost + opt.cost + joinCost, true, nasal)
                            next += State(s.text + opt.text, s.cost + opt.cost + 0.8f, true, nasal)
                            if (s.nasal) {
                                val anusvaraCost = if (joinedNasal) 0.3f else 0f
                                next += State(s.text.dropLast(1) + ANUSVARA + opt.text, s.cost + opt.cost + anusvaraCost, true, nasal)
                                next += State(s.text.dropLast(1) + CHANDRABINDU + opt.text, s.cost + opt.cost + chandraCost, true, nasal)
                            }
                            // Marathi's eyelash ra before y / h: दुसऱ्या, ऱ्हस्व.
                            if (lang == KeyboardLanguage.MARATHI && s.text.endsWith("र") && (opt.text == "य" || opt.text == "ह")) {
                                next += State(s.text.dropLast(1) + "ऱ" + VIRAMA + opt.text, s.cost + opt.cost + 0.5f, true, nasal)
                            }
                        } else {
                            next += State(s.text + opt.text, s.cost + opt.cost, true, nasal)
                            // A final n after a vowel is usually nasal: nahin → नहीं, main → मैं, maan → माँ.
                            if (last && nasal && s.text.isNotEmpty()) {
                                next += State(s.text + ANUSVARA, s.cost + 0.4f, false, false)
                                next += State(s.text + CHANDRABINDU, s.cost + 0.2f + chandraCost, false, false)
                            }
                            // h after a vowel can be the visarga (स्वतःला, दुःख).
                            if (opt.text == "ह" && s.text.isNotEmpty()) {
                                next += State(s.text + VISARGA, s.cost + 0.9f, false, false)
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
                        // Marathi writes English words' short a as ॅ / ॲ (bag → बॅग, camera → कॅमेरा).
                        if (lang == KeyboardLanguage.MARATHI && unit === vowels["a"]) {
                            next += State(s.text + if (s.pending) "ॅ" else "ॲ", s.cost + 1.1f, false, false)
                        }
                    }
                    is Mark -> next += State(s.text + unit.text, s.cost, false, false)
                    is Other -> next += State(s.text + unit.text, s.cost, false, false)
                }
            }
            val unique = next.groupBy { it.text }.map { (_, same) -> same.minBy { it.cost } }.sortedBy { it.cost }
            beam = if (lexicon == null) {
                unique.take(BEAM)
            } else {
                // A pending न / म may still turn into ं (सगळ्यान + n → सगळ्यांन), so
                // it counts as a word's start if that would be.
                val (words, other) = unique.partition {
                    lexicon.hasPrefix(it.text) || (it.nasal && lexicon.hasPrefix(it.text.dropLast(1) + ANUSVARA))
                }
                words.take(BEAM) + other.take(BEAM_LITERAL)
            }
        }
        return beam.map { it.text to it.cost }
    }

    /**
     * The strip's words for [latin], best first: real words (from [lexicon],
     * most common first, nearer the literal spelling first) ahead of rule-only
     * spellings; a spelling picked for these letters before ([remembered]) leads.
     * An English word is offered as मराठी / हिन्दी write it ([loans]: energy →
     * एनर्जी) unless a common native word reads the same; an English word with no
     * such spelling ([english] has it, [loans] doesn't) is offered as typed, in
     * English letters, first. Then a couple of longer words for a word still
     * being typed.
     */
    fun candidates(
        latin: String,
        lang: KeyboardLanguage,
        lexicon: Lexicon?,
        remembered: String? = null,
        limit: Int = 3,
        loans: LoanWords? = null,
        english: Lexicon? = null,
        next: List<String> = emptyList(),
    ): List<String> {
        val spelled = spellings(latin, lang, lexicon)
        if (spelled.isEmpty()) return emptyList()
        val loanSpellings = loans?.get(latin).orEmpty()
        val byText = HashMap<String, Float>()
        for ((text, cost) in spelled) {
            val e = lexicon?.lookup(text)
            byText[text] = if (e != null) REAL_WORD + e.freq - cost * COST_WEIGHT else -cost * COST_WEIGHT
        }
        // Each further spelling of the English word a little behind the first.
        loanSpellings.forEachIndexed { i, text ->
            val freq = maxOf(lexicon?.lookup(text)?.freq ?: 0, LOAN_FREQ) - i
            byText[text] = maxOf(byText[text] ?: Float.NEGATIVE_INFINITY, REAL_WORD + freq)
        }
        // A word the context makes likely (मला → माहीत) moves up.
        for (w in next) byText[w]?.let { if (it > REAL_WORD / 2) byText[w] = it + NEXT_BONUS }
        val scored = byText.entries.map { it.key to it.value }.sortedByDescending { it.second }
        val out = LinkedHashSet<String>()
        remembered?.let { out += it }
        val englishWord = loanSpellings.isEmpty() &&
            english?.lookup(latin.lowercase())?.let { !it.blocked && it.freq >= ENGLISH_MIN_FREQ } == true
        // No मराठी / हिन्दी spelling of an English word, and no native word reads
        // the same ("namaskar" is in the English list too): the English word itself.
        if (englishWord && scored.none { it.second > REAL_WORD / 2 }) out += latin
        // Real words first; if none, a compound of real words (प्यारे + लाल), then
        // the most literal spelling.
        for ((text, score) in scored) if (score > REAL_WORD / 2 && out.size < limit) out += text
        if (lexicon != null && scored.none { it.second > REAL_WORD / 2 }) {
            compound(latin, lang, lexicon)?.let { out += it }
        }
        if (out.isEmpty()) out += scored.first().first
        // Longer words for one still being typed ("namask" → नमस्कार, "ener" → एनर्जी),
        // the context's likely ones first.
        for ((prefix, _) in spelled.take(2)) {
            for (w in next) if (w.length > prefix.length && w.startsWith(prefix) && out.size < limit) out += w
        }
        if (lexicon != null) {
            for ((prefix, _) in spelled.take(2)) {
                for (c in lexicon.completions(prefix, 2)) if (out.size < limit) out += c.word
            }
        }
        if (loans != null && latin.length >= MIN_LOAN_PREFIX) {
            for (c in loans.completions(latin, 1)) if (out.size < limit) out += c
        }
        for ((text, _) in scored) if (out.size < limit) out += text
        return out.toList()
    }

    /**
     * A word not in the list, spelled as two or three that are: names and
     * compounds (pyarelal → प्यारे + लाल, rahuldev → राहुल + देव, natwarlal →
     * नट + वर + लाल). Each part at least two letters; the split whose rarest part
     * is most common wins. Null when no split is made of real words.
     */
    internal fun compound(latin: String, lang: KeyboardLanguage, lexicon: Lexicon, parts: Int = 3): String? {
        val memo = HashMap<String, LexEntry?>()
        fun word(part: String): LexEntry? = memo.getOrPut(part) {
            spellings(part, lang, lexicon).asSequence()
                .mapNotNull { (text, cost) -> lexicon.lookup(text)?.let { it to cost } }
                .filter { (e, _) -> e.word.length >= 2 }
                .maxByOrNull { (e, cost) -> e.freq - cost * COST_WEIGHT }?.first
        }
        fun part(piece: String): LexEntry? =
            word(piece)?.takeIf { it.freq >= MIN_PART_FREQ } // reasonably common, so names don't split into junk
        var best: Pair<String, Int>? = null
        fun consider(pieces: List<String>) {
            // A part after the first starts with a consonant (rahul + dev, pyare + lal);
            // one starting with a vowel is a split mid-word (ling + oba → लिंगओब).
            if (pieces.drop(1).any { it.first().lowercaseChar() in "aeiou" }) return
            val entries = pieces.map { part(it) ?: return }
            // Another part must earn its place: three-way splits are a last resort.
            val score = entries.minOf { it.freq } - EXTRA_PART_COST * (entries.size - 1)
            if (best == null || score > best!!.second) best = entries.joinToString("") { it.word } to score
        }
        val n = latin.length
        for (cut in MIN_PART..n - MIN_PART) consider(listOf(latin.substring(0, cut), latin.substring(cut)))
        // Three parts each need three letters, so दिन + चार + या doesn't stand in for दिनचर्या.
        if (parts >= 3) {
            for (a in MIN_PART_OF_THREE..n - 2 * MIN_PART_OF_THREE) {
                for (b in a + MIN_PART_OF_THREE..n - MIN_PART_OF_THREE) {
                    consider(listOf(latin.substring(0, a), latin.substring(a, b), latin.substring(b)))
                }
            }
        }
        return best?.first
    }

    /** An English word's spelling ranks like a native word this common (a commoner native one wins). */
    private const val LOAN_FREQ = 60

    /** How common an English word must be to be offered in English letters. */
    private const val ENGLISH_MIN_FREQ = 60
    private const val MIN_LOAN_PREFIX = 3

    /** How much a word the context makes likely moves up. */
    private const val NEXT_BONUS = 60f

    private const val MIN_PART = 2
    private const val MIN_PART_OF_THREE = 3
    private const val MIN_PART_FREQ = 40
    private const val EXTRA_PART_COST = 25
}
