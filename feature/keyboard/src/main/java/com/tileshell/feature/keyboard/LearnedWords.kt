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

    /** Every known learned word. */
    fun known(): List<LexEntry> = counts.keys.mapNotNull(::entry)

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

    private val saver = DebouncedSave { writeNow() }

    private fun save() = saver.request()

    /** Writes any pending change now (the keyboard is closing). */
    fun flush() = saver.flush()

    private fun writeNow() {
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
class CombinedLexicon(val words: Lexicon, val learned: LearnedWords) : Lexicon {

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

    override fun startingWith(prefixLower: String): Sequence<LexEntry> =
        learned.completions(prefixLower, 200).asSequence() + words.startingWith(prefixLower)

    override fun hasPrefix(prefixLower: String): Boolean =
        words.hasPrefix(prefixLower) || learned.completions(prefixLower, 1).isNotEmpty() || learned.entry(prefixLower) != null

    override fun shortcut(lower: String): String? =
        if (learned.isKnown(lower)) null else words.shortcut(lower)
}

/**
 * Which word the user puts after which ("see you" → "soon", "मी" → "घरी"), so
 * their own habits lead the next-word strip. Only words the keyboard itself
 * just put in are counted (never text the cursor merely passes), only where
 * learning is allowed (not passwords or "no learning" fields). Kept only on
 * the phone (`keyboard_pairs.txt`, `previous<TAB>next<TAB>count`), cleared with
 * the learned words.
 */
class LearnedPairs(private val file: File?) {

    private val counts = LinkedHashMap<String, Int>()
    private val spellings = HashMap<String, String>()

    init {
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { f.readLines() }.getOrDefault(emptyList()).forEach { line ->
                val parts = line.split('\t')
                val n = parts.getOrNull(2)?.toIntOrNull()
                if (parts.size == 3 && parts[0].isNotBlank() && parts[1].isNotBlank() && n != null) {
                    val key = key(parts[0], parts[1])
                    counts[key] = n.coerceIn(1, MAX_COUNT)
                    spellings[key] = parts[1]
                }
            }
        }
    }

    val size: Int get() = counts.size

    /** [next] was put in right after [previous]. */
    fun learn(previous: String, next: String) {
        if (!usable(previous) || !usable(next)) return
        val key = key(previous, next)
        val n = ((counts.remove(key) ?: 0) + 1).coerceAtMost(MAX_COUNT)
        counts[key] = n // re-inserted: the newest last, so the oldest go first when full
        if (next != next.lowercase() || key !in spellings) spellings[key] = next
        while (counts.size > MAX_PAIRS) {
            val oldest = counts.keys.first()
            counts.remove(oldest)
            spellings.remove(oldest)
        }
        save()
    }

    /** What the user has put after [previous] at least twice, most often first. */
    fun after(previous: String, limit: Int): List<String> {
        val prefix = previous.lowercase() + "\t"
        return counts.entries.asSequence()
            .filter { it.key.startsWith(prefix) && it.value >= SEEN_AT }
            .sortedByDescending { it.value }
            .take(limit)
            .map { spellings[it.key] ?: it.key.substring(prefix.length) }
            .toList()
    }

    fun clear() {
        counts.clear()
        spellings.clear()
        save()
    }

    private fun usable(w: String) = w.length in 1..MAX_LENGTH && w.none { it.isWhitespace() || it.isDigit() || it == '\t' }

    private fun key(previous: String, next: String) = previous.lowercase() + "\t" + next.lowercase()

    private val saver = DebouncedSave { writeNow() }

    private fun save() = saver.request()

    /** Writes any pending change now (the keyboard is closing). */
    fun flush() = saver.flush()

    private fun writeNow() {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(
                counts.entries.joinToString("") {
                    val previous = it.key.substringBefore('\t')
                    "$previous\t${spellings[it.key] ?: it.key.substringAfter('\t')}\t${it.value}\n"
                },
            )
            tmp.renameTo(f)
        }
    }

    private companion object {
        const val SEEN_AT = 2
        const val MAX_COUNT = 50
        const val MAX_PAIRS = 2_000
        const val MAX_LENGTH = 32
    }
}


/**
 * Runs [action] once, [delayMs] after the last call to [request]: a burst of calls (a word typed every second) becomes
 * one write after the typing pauses, instead of rewriting a whole file per word on the keyboard's main thread.
 * [flush] runs a pending one at once (keyboard closing).
 */
internal class DebouncedSave(private val delayMs: Long = 5_000L, private val action: () -> Unit) {
    // No main looper (a plain JVM unit test): save at once, as before.
    private val handler by lazy { runCatching { android.os.Handler(android.os.Looper.getMainLooper()) }.getOrNull() }
    private var pending = false
    private val run = Runnable {
        pending = false
        action()
    }

    fun request() {
        val h = handler ?: return action()
        h.removeCallbacks(run)
        pending = true
        h.postDelayed(run, delayMs)
    }

    fun flush() {
        if (!pending) return
        handler?.removeCallbacks(run)
        run.run()
    }
}
