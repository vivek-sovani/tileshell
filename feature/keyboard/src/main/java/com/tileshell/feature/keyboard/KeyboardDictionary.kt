package com.tileshell.feature.keyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Process-wide word lists, learned words and transliteration picks, one set
 * per language. A list loads off the main thread the first time its language
 * is used and stays for the life of the process (TileShell's, which the
 * keyboard shares): English ~2 MB packed, Marathi / Hindi well under 1 MB.
 */
object KeyboardDictionary {

    /** Bundled list + our short supplement for each language. */
    private val assets = mapOf(
        // AOSP en_GB (built from formal writing), plus Indian English and everyday
        // chat words it lacks or ranks low (congrats, ok, gonna, lol…).
        KeyboardLanguage.ENGLISH to listOf(
            "keyboard/en_words.txt", "keyboard/en_in_extra.txt", "keyboard/en_chat_extra.txt",
        ),
        // From Tatoeba and Common Voice sentences, plus places, festivals and names.
        KeyboardLanguage.MARATHI to listOf("keyboard/mr_words.txt", "keyboard/mr_extra.txt", "keyboard/names.txt"),
        KeyboardLanguage.HINDI to listOf("keyboard/hi_words.txt", "keyboard/hi_extra.txt", "keyboard/names.txt"),
    )

    /**
     * Each language also knows the other's words, a little less common: Marathi
     * text uses many Hindi words and names (नटवरलाल), and Hindi Marathi ones. The
     * language's own spelling still wins where the two differ.
     */
    private val borrowed = mapOf(
        KeyboardLanguage.MARATHI to listOf("keyboard/hi_words.txt", "keyboard/hi_extra.txt"),
        KeyboardLanguage.HINDI to listOf("keyboard/mr_words.txt", "keyboard/mr_extra.txt"),
    )
    private const val BORROWED_WEIGHT = 0.8

    private val mutex = Mutex()
    private val words = HashMap<KeyboardLanguage, WordList>()
    private val learned = HashMap<KeyboardLanguage, LearnedWords>()
    private val picks = HashMap<KeyboardLanguage, TranslitMemory>()
    private val loans = HashMap<KeyboardLanguage, LoanWords>()
    private val nextWords = HashMap<KeyboardLanguage, NextWords>()
    private val pairs = HashMap<KeyboardLanguage, LearnedPairs>()

    /** Which words usually come next in [language] (`<code>_next.txt`). */
    suspend fun nextWords(context: Context, language: KeyboardLanguage): NextWords = withContext(Dispatchers.IO) {
        mutex.withLock {
            nextWords[language] ?: NextWords.parse(
                context.assets.open("keyboard/${language.code}_next.txt").bufferedReader().readText(),
            ).also { nextWords[language] = it }
        }
    }

    /** Writes any learned words and pairs not yet on disk (the keyboard is closing). Saves are otherwise delayed until typing pauses. */
    fun flushLearned() {
        synchronized(learned) { learned.values.toList() }.forEach { it.flush() }
        synchronized(pairs) { pairs.values.toList() }.forEach { it.flush() }
    }

    /** The user's own word pairs in [language]. */
    fun learnedPairs(context: Context, language: KeyboardLanguage): LearnedPairs = synchronized(pairs) {
        pairs.getOrPut(language) { LearnedPairs(file(context, "keyboard_pairs", language)) }
    }

    /**
     * English words as [language] writes them (energy → एनर्जी). Marathi also
     * offers Hindi's spelling after its own (पॉझिटिव्ह, then पॉजिटिव).
     */
    suspend fun loanWords(context: Context, language: KeyboardLanguage): LoanWords? {
        if (!language.indic) return null
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                loans[language] ?: run {
                    fun read(lang: KeyboardLanguage) =
                        context.assets.open("keyboard/${lang.code}_loan.txt").bufferedReader().readLines().asSequence()
                    LoanWords.parse(
                        read(language),
                        if (language == KeyboardLanguage.MARATHI) read(KeyboardLanguage.HINDI) else emptySequence(),
                    )
                }.also { loans[language] = it }
            }
        }
    }

    suspend fun lexicon(context: Context, language: KeyboardLanguage): CombinedLexicon = withContext(Dispatchers.IO) {
        val list = mutex.withLock {
            words[language] ?: run {
                val files = assets.getValue(language)
                val extra = files.drop(1).flatMap { context.assets.open(it).bufferedReader().readLines() } +
                    borrowed[language].orEmpty().flatMap { name ->
                        context.assets.open(name).bufferedReader().readLines().map(::weaken)
                    }
                context.assets.open(files.first()).bufferedReader().useLines { lines ->
                    WordList.parse(lines + extra.asSequence())
                }
            }.also { words[language] = it }
        }
        CombinedLexicon(list, learnedWords(context, language))
    }

    /** A borrowed list's line with its frequency scaled down. */
    internal fun weaken(line: String): String {
        val tab = line.indexOf('\t')
        if (tab < 0) return line
        val end = line.indexOf('\t', tab + 1).let { if (it < 0) line.length else it }
        val f = line.substring(tab + 1, end).toIntOrNull() ?: return line
        return line.substring(0, tab + 1) + (f * BORROWED_WEIGHT).toInt() + line.substring(end)
    }

    fun learnedWords(context: Context, language: KeyboardLanguage = KeyboardLanguage.ENGLISH): LearnedWords =
        synchronized(learned) {
            learned.getOrPut(language) { LearnedWords(file(context, "keyboard_learned", language)) }
        }

    /** Which Devanagari spelling was picked for which typed letters. */
    fun translitPicks(context: Context, language: KeyboardLanguage): TranslitMemory = synchronized(picks) {
        picks.getOrPut(language) { TranslitMemory(file(context, "keyboard_translit", language)) }
    }

    /** Settings → clear learned words: every language's words and picks. */
    fun clearLearned(context: Context) {
        for (lang in KeyboardLanguage.entries) {
            learnedWords(context, lang).clear()
            learnedPairs(context, lang).clear()
            if (lang.indic) translitPicks(context, lang).clear()
        }
    }

    fun learnedCount(context: Context): Int = KeyboardLanguage.entries.sumOf { learnedWords(context, it).size }

    fun learnedPairCount(context: Context): Int = KeyboardLanguage.entries.sumOf { learnedPairs(context, it).size }

    /** English keeps its original file name; others get a language suffix. */
    private fun file(context: Context, base: String, language: KeyboardLanguage) = File(
        context.applicationContext.filesDir,
        if (language == KeyboardLanguage.ENGLISH) "$base.txt" else "${base}_${language.code}.txt",
    )
}

/**
 * Remembered transliteration picks ("namaskar" → the spelling chosen in the
 * strip), so the same letters give the same word next time. Kept on the phone
 * only, `latin<TAB>devanagari`, newest 500.
 */
class TranslitMemory(private val file: File?) {
    private val map = LinkedHashMap<String, String>()

    init {
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { f.readLines() }.getOrDefault(emptyList()).forEach { line ->
                val parts = line.split('\t')
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) map[parts[0]] = parts[1]
            }
        }
    }

    operator fun get(latin: String): String? = map[latin.lowercase()]

    fun remember(latin: String, devanagari: String) {
        val key = latin.lowercase()
        if (key.isBlank() || devanagari.isBlank()) return
        map.remove(key)
        map[key] = devanagari
        while (map.size > MAX) map.remove(map.keys.first())
        save()
    }

    fun clear() {
        map.clear()
        save()
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(map.entries.joinToString("") { "${it.key}\t${it.value}\n" })
            tmp.renameTo(f)
        }
    }

    private companion object {
        const val MAX = 500
    }
}
