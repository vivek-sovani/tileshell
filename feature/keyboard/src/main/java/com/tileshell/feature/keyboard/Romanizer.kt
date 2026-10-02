package com.tileshell.feature.keyboard

/**
 * Devanagari → the casual English letters people type for it (नमस्कार →
 * "namaskar", माझा → "maza"), the reverse of [Transliterator]. Swipe typing in
 * मराठी / हिन्दी matches the finger's path against these spellings, then puts
 * in the Devanagari word. Long and short vowels romanise alike (ा → a, ी → i):
 * a swipe can't tell "aa" from "a" anyway, as a doubled letter doesn't move the
 * finger. Pure, so it's unit-tested.
 */
object Romanizer {

    private const val VIRAMA = '्'

    private val consonants: Map<Char, String> = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ऱ' to "r", 'ल' to "l", 'ळ' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
    )

    private val matras: Map<Char, String> = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ृ' to "ru",
        // English words' vowels: bag (बॅग), shop (शॉप).
        'ॅ' to "a", 'ॉ' to "o",
    )

    private val vowels: Map<Char, String> = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऋ' to "ru",
        'ऍ' to "a", 'ऑ' to "o", 'ॲ' to "a",
    )

    /** Marks an unwritten a in [spell]'s output, for [variants]. */
    private const val SCHWA = '\u0000'

    /** The casual romanisation of [word]; null if it has letters outside Devanagari. */
    fun romanize(word: String, lang: KeyboardLanguage): String? = spell(word, lang)?.replace(SCHWA, 'a')

    /**
     * The spellings people type for [word]: every unwritten a spelled out
     * ("apalyala"), and with the ones speech drops left out ("aplyala" —
     * an a between a consonant and a consonant + vowel, past the first
     * syllable, the usual Hindi / Marathi schwa deletion).
     */
    fun variants(word: String, lang: KeyboardLanguage): List<String> {
        val marked = spell(word, lang) ?: return emptyList()
        val full = marked.replace(SCHWA, 'a')
        val sb = StringBuilder()
        var seenVowel = false
        for (i in marked.indices) {
            val c = marked[i]
            if (c == SCHWA) {
                if (seenVowel && consonantThenVowel(marked, i + 1)) continue
                sb.append('a')
                seenVowel = true
                continue
            }
            if (c in LATIN_VOWELS) seenVowel = true
            sb.append(c)
        }
        return listOf(full, sb.toString()).distinct()
    }

    private val LATIN_VOWELS = setOf('a', 'e', 'i', 'o', 'u')

    /**
     * At [at]: one consonant sound (with an h, as kh, sh), maybe joined to a
     * y / r / v / l (the ly of आपल्याला, "aplyala"), followed by a vowel.
     */
    private fun consonantThenVowel(s: String, at: Int): Boolean {
        var i = at
        if (i >= s.length || s[i] in LATIN_VOWELS || s[i] == SCHWA) return false
        i++
        if (i < s.length && s[i] == 'h') i++
        if (i < s.length && s[i] in GLIDES && i + 1 < s.length && (s[i + 1] in LATIN_VOWELS)) i++
        return i < s.length && (s[i] in LATIN_VOWELS || s[i] == SCHWA)
    }

    private val GLIDES = setOf('y', 'r', 'v', 'l')

    private fun spell(word: String, lang: KeyboardLanguage): String? {
        val out = StringBuilder()
        var i = 0
        while (i < word.length) {
            val c = word[i]
            val next = word.getOrNull(i + 1)
            when {
                // क्ष and ज्ञ are typed as units.
                c == 'क' && next == VIRAMA && word.getOrNull(i + 2) == 'ष' -> {
                    out.append("ksh")
                    i += 3
                    inherentA(word, i, out)
                    continue
                }
                c == 'ज' && next == VIRAMA && word.getOrNull(i + 2) == 'ञ' -> {
                    out.append(if (lang == KeyboardLanguage.MARATHI) "dny" else "gy")
                    i += 3
                    inherentA(word, i, out)
                    continue
                }
                c in consonants -> {
                    // Marathi types झ as z (maza); a nukta letter keeps its base sound.
                    out.append(if (c == 'झ' && lang == KeyboardLanguage.MARATHI) "z" else consonants.getValue(c))
                    i++
                    if (word.getOrNull(i) == '़') {
                        if (c == 'ज') out.setLength(out.length - 1).also { out.append('z') }
                        if (c == 'फ') out.setLength(out.length - 2).also { out.append('f') }
                        i++
                    }
                    inherentA(word, i, out)
                    continue
                }
                c in matras -> out.append(matras.getValue(c))
                c in vowels -> out.append(vowels.getValue(c))
                c == 'ं' || c == 'ँ' -> out.append(if (next == 'प' || next == 'ब' || next == 'भ' || next == 'म') "m" else "n")
                c == 'ः' -> out.append("h")
                c == VIRAMA -> Unit
                else -> return null
            }
            i++
        }
        return out.toString().takeIf { it.isNotEmpty() }
    }

    /**
     * The unwritten a after a consonant: written when a consonant (or a nasal)
     * follows directly, dropped at the end of a word ("namaskar", not
     * "namaskara") and before a matra or virama.
     */
    private fun inherentA(word: String, i: Int, out: StringBuilder) {
        val n = word.getOrNull(i) ?: return
        if (n == VIRAMA || n in matras || n == '़') return
        out.append(SCHWA)
    }
}

/**
 * The swipe word list for मराठी / हिन्दी: each word's romanised spelling, so a
 * path across the English letters can be matched, mapping back to the
 * Devanagari words (several can share one spelling — मी, मि — most common first).
 */
class RomanizedLexicon(words: Sequence<LexEntry>, lang: KeyboardLanguage) : Lexicon {
    private val devanagari = HashMap<String, MutableList<LexEntry>>()
    private val latin: WordList

    init {
        val best = HashMap<String, Int>()
        for (e in words) {
            if (e.blocked) continue
            for (r in Romanizer.variants(e.word, lang)) {
                devanagari.getOrPut(r) { ArrayList() } += e
                best[r] = maxOf(best[r] ?: 0, e.freq)
            }
        }
        devanagari.values.forEach { list -> list.sortByDescending { it.freq } }
        latin = WordList.parse(best.entries.asSequence().map { "${it.key}\t${it.value}" })
    }

    /** Words learned since the list was built (typed or picked), so they can be swiped too. */
    private val added = HashMap<String, LexEntry>()
    private val language = lang

    /** The Devanagari words spelled [romanized], most common first. */
    fun devanagariFor(romanized: String): List<String> = devanagari[romanized.lowercase()].orEmpty().map { it.word }

    /** Adds a newly learned word. */
    fun add(word: LexEntry) {
        for (r in Romanizer.variants(word.word, language)) {
            val list = devanagari.getOrPut(r) { ArrayList() }
            if (list.none { it.word == word.word }) {
                list += word
                list.sortByDescending { it.freq }
            }
            if (latin.lookup(r) == null) added[r] = LexEntry(r, maxOf(word.freq, added[r]?.freq ?: 0))
        }
    }

    override fun lookup(lower: String) = latin.lookup(lower) ?: added[lower]
    override fun completions(prefixLower: String, limit: Int) = latin.completions(prefixLower, limit)
    override fun startingWith(prefixLower: String) =
        latin.startingWith(prefixLower) + added.values.asSequence().filter { it.word.startsWith(prefixLower) }
}
