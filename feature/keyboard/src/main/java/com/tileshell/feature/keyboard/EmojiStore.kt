package com.tileshell.feature.keyboard

import android.content.Context
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The emoji catalogue, loaded once, without emoji this phone's font can't draw. */
internal object EmojiStore {
    private val mutex = Mutex()
    @Volatile private var catalog: EmojiCatalog? = null

    suspend fun catalog(context: Context): EmojiCatalog = withContext(Dispatchers.IO) {
        catalog ?: mutex.withLock {
            catalog ?: run {
                val paint = Paint()
                context.assets.open("keyboard/emoji.txt").bufferedReader().useLines { lines ->
                    EmojiCatalog(lines) { paint.hasGlyph(it) }
                }
            }.also { catalog = it }
        }
    }

    @Volatile private var words: EmojiWords? = null

    /** Which emoji a typed word stands for ("pizza" → 🍕), those this phone can draw. */
    suspend fun words(context: Context): EmojiWords = withContext(Dispatchers.IO) {
        words ?: mutex.withLock {
            words ?: run {
                val paint = Paint()
                context.assets.open("keyboard/emoji_words.txt").bufferedReader().useLines { lines ->
                    EmojiWords(lines) { paint.hasGlyph(it) }
                }
            }.also { words = it }
        }
    }
}
