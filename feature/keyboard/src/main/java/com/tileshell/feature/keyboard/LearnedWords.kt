package com.tileshell.feature.keyboard

import java.io.File

/**
 * Words the keyboard has learned on this phone: names and words missing from
 * the bundled list. A word typed and kept twice counts as known; undoing an
 * autocorrect teaches the original at once. Stored only in the app's own files
 * (`keyboard_learned.txt`, `word<TAB>count`), never sent anywhere; password
 * fields and "no learning" fields never add to it.
 */
class LearnedWords(private val file: File?) {

    private val counts = HashMap<String, Int>()
    private val spellings = HashMap<String, String>()

    init {
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { f.readLines() }.getOrDefault(emptyList()).forEach(::readLine)
        }
    }

    val size: Int get() = counts.size

    fun isKnown(lower: String): Boolean = (counts[lower] ?: 0) >= KNOWN_AT

    /** One more use of [word]; [strong] (an undone autocorrect) makes it known now. */
    fun learn(word: String, strong: Boolean = false) {
        val clean = word.trim()
        if (clean.length < 2 || clean.length > MAX_LENGTH || clean.any { it.isWhitespace() || it.isDigit() }) return
        val lower = clean.lowercase()
        val next = ((counts[lower] ?: 0) + if (strong) KNOWN_AT else 1).coerceAtMost(MAX_COUNT)
        counts[lower] = next
        // Keep the capitalised spelling if that's how it was typed ("Rahul").
        if (clean != lower || lower !in spellings) spellings[lower] = clean
        if (counts.size > MAX_WORDS) trim()
        save()
    }

    fun clear() {
        counts.clear()
        spellings.clear()
        save()
    }

    fun entry(lower: String): LexEntry? {
        val n = counts[lower] ?: return null
        if (n < KNOWN_AT) return null
        return LexEntry(spellings[lower] ?: lower, freqFor(n))
    }

    fun completions(prefixLower: String, limit: Int): List<LexEntry> =
        counts.entries.asSequence()
            .filter { it.value >= KNOWN_AT && it.key.startsWith(prefixLower) && it.key != prefixLower }
            .sortedByDescending { it.value }
            .take(limit)
            .map { LexEntry(spellings[it.key] ?: it.key, freqFor(it.value)) }
            .toList()

    private fun readLine(line: String) {
        val parts = line.split('\t')
        val word = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return
        val n = parts.getOrNull(1)?.toIntOrNull() ?: return
        val lower = word.lowercase()
        counts[lower] = n.coerceIn(0, MAX_COUNT)
        spellings[lower] = word
    }

    /** Drops the least used words once the list is full. */
    private fun trim() {
        counts.entries.sortedBy { it.value }.take(counts.size - MAX_WORDS).forEach {
            counts.remove(it.key)
            spellings.remove(it.key)
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(counts.entries.joinToString("") { "${spellings[it.key] ?: it.key}\t${it.value}\n" })
            tmp.renameTo(f)
        }
    }

    companion object {
        const val KNOWN_AT = 2
        private const val MAX_COUNT = 50
        private const val MAX_WORDS = 3_000
        private const val MAX_LENGTH = 32

        /** A learned word ranks with fairly common words, more so the more it's used. */
        fun freqFor(count: Int): Int = (100 + count * 8).coerceAtMost(200)
    }
}

/** The bundled word list plus the learned words. */
class CombinedLexicon(private val words: Lexicon, private val learned: LearnedWords) : Lexicon {

    override fun lookup(lower: String): LexEntry? {
        val bundled = words.lookup(lower)
        val mine = learned.entry(lower)
        return when {
            bundled == null -> mine
            mine == null -> bundled
            // A learned spelling (a typed capital) wins; frequency is the higher.
            else -> LexEntry(mine.word, maxOf(bundled.freq, mine.freq), bundled.blocked)
        }
    }

    override fun completions(prefixLower: String, limit: Int): List<LexEntry> {
        val merged = (learned.completions(prefixLower, limit) + words.completions(prefixLower, limit))
            .distinctBy { it.word.lowercase() }
        return merged.sortedByDescending { it.freq }.take(limit)
    }

    override fun shortcut(lower: String): String? =
        if (learned.isKnown(lower)) null else words.shortcut(lower)
}
