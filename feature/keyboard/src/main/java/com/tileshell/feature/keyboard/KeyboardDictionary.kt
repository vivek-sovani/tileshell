package com.tileshell.feature.keyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Process-wide word list and learned words. The list (~2 MB packed) loads off
 * the main thread the first time the keyboard opens and stays for the life of
 * the process (TileShell's, which the keyboard shares).
 */
object KeyboardDictionary {
    private const val ASSET = "keyboard/en_words.txt"

    /** Indian English the AOSP list lacks: places, festivals, food, names, lakh/crore. */
    private const val INDIA_ASSET = "keyboard/en_in_extra.txt"
    private const val LEARNED_FILE = "keyboard_learned.txt"

    private val mutex = Mutex()
    @Volatile private var words: WordList? = null
    @Volatile private var learned: LearnedWords? = null

    suspend fun lexicon(context: Context): CombinedLexicon = withContext(Dispatchers.IO) {
        val list = words ?: mutex.withLock {
            words ?: run {
                val extra = context.assets.open(INDIA_ASSET).bufferedReader().readLines()
                context.assets.open(ASSET).bufferedReader().useLines { lines ->
                    WordList.parse(lines + extra.asSequence())
                }
            }.also { words = it }
        }
        CombinedLexicon(list, learnedWords(context))
    }

    fun learnedWords(context: Context): LearnedWords = learned ?: synchronized(this) {
        learned ?: LearnedWords(File(context.applicationContext.filesDir, LEARNED_FILE)).also { learned = it }
    }
}
