package com.tileshell.feature.keyboard

/**
 * Which words usually come next: "thank" → you, "see you" → again / tomorrow,
 * a sentence's start → I / the. Counted from sentence collections by
 * `tools/keyboard/build_ngrams.py` (see `assets/keyboard/NOTICE`); asset lines
 * are `context<TAB>next next…`, sorted by context, where a context is one word,
 * two with a space, or [START].
 *
 * Packed like [WordList]: the file stays one string with line offsets, found by
 * binary search, so ~80k English contexts cost a few MB, not a map's worth.
 */
class NextWords private constructor(
    private val text: String,
    private val starts: IntArray,
) {
    val size: Int get() = starts.size

    /** The usual next words after [context] (lower case, words joined by a space), best first. */
    operator fun get(context: String): List<String> {
        var lo = 0
        var hi = starts.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val c = compareKey(mid, context)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid
                else -> {
                    val tab = text.indexOf('\t', starts[mid])
                    val end = text.indexOf('\n', tab).let { if (it < 0) text.length else it }
                    return text.substring(tab + 1, end).split(' ').filter { it.isNotEmpty() }
                }
            }
        }
        return emptyList()
    }

    private fun compareKey(i: Int, s: String): Int {
        val start = starts[i]
        var k = 0
        while (true) {
            val ch = text[start + k]
            val keyEnded = ch == '\t'
            if (keyEnded || k == s.length) {
                return when {
                    keyEnded && k == s.length -> 0
                    keyEnded -> -1
                    else -> 1
                }
            }
            val d = ch - s[k]
            if (d != 0) return d
            k++
        }
    }

    companion object {
        /** The context at a sentence's start. */
        const val START = "^"

        fun parse(content: String): NextWords {
            val starts = ArrayList<Int>()
            var i = 0
            while (i < content.length) {
                val end = content.indexOf('\n', i).let { if (it < 0) content.length else it }
                if (end > i && content.indexOf('\t', i).let { it in i until end }) starts += i
                i = end + 1
            }
            return NextWords(content, starts.toIntArray())
        }

        /**
         * The words before the cursor that predict the next one, last word last
         * (lower case), or [[START]] at a sentence's start. Empty mid-word or
         * after something that isn't a word.
         */
        fun context(before: CharSequence?): List<String> {
            val text = before?.toString().orEmpty()
            if (text.isNotEmpty() && !text.last().isWhitespace()) return emptyList()
            val trimmed = text.trimEnd()
            if (trimmed.isEmpty() || trimmed.last() in SENTENCE_END) return listOf(START)
            if (!isWordChar(trimmed.last())) return emptyList()
            // The words since the sentence's last break or comma, up to the last
            // thing that isn't a word ("call 9876 now" → "now").
            val phrase = trimmed.substring(trimmed.indexOfLast { it in SENTENCE_END || it == ',' } + 1)
            val words = phrase.split(' ', '\n', '\t').filter { it.isNotEmpty() }
                .takeLastWhile { w -> w.all(::isWordChar) }
            return words.takeLast(2).map { it.lowercase() }
        }

        private fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c in 'ऀ'..'ॿ'

        private const val SENTENCE_END = ".!?।॥"

        /** Words a time-of-day greeting can follow. */
        private val GREETING_OPENERS = setOf(START, "hi", "hello", "hey", "very", "नमस्कार", "नमस्ते")

        /**
         * The greeting for the time of day after "good" (morning … night) or शुभ
         * (सकाळ / प्रभात …), the current one first, then the rest of the day in order.
         */
        fun greetings(lastWord: String, language: KeyboardLanguage, hour: Int): List<String> {
            val day = when {
                lastWord == "good" -> listOf("morning", "afternoon", "evening", "night")
                lastWord != "शुभ" -> return emptyList()
                language == KeyboardLanguage.MARATHI -> listOf("सकाळ", "दुपार", "संध्याकाळ", "रात्री")
                language == KeyboardLanguage.HINDI -> listOf("प्रभात", "दोपहर", "संध्या", "रात्रि")
                else -> return emptyList()
            }
            val now = when (hour) {
                in 4..11 -> 0
                in 12..16 -> 1
                in 17..20 -> 2
                else -> 3
            }
            return day.drop(now) + day.take(now)
        }

        /**
         * The next words for [context]: the time-of-day greeting, then the
         * user's own pairs ([learned], most used first), then the two-word and
         * one-word tables. At most [limit], no repeats.
         */
        fun predict(
            context: List<String>,
            table: NextWords?,
            learned: LearnedPairs?,
            language: KeyboardLanguage,
            hour: Int,
            limit: Int,
        ): List<String> {
            if (context.isEmpty()) return emptyList()
            val last = context.last()
            val out = LinkedHashSet<String>()
            fun add(words: List<String>) {
                for (w in words) if (out.size < limit) out += w
            }
            // A greeting opens a phrase ("Good …", "hi good …"), not "a good idea".
            if (context.size == 1 || context.first() in GREETING_OPENERS) add(greetings(last, language, hour))
            learned?.let { add(it.after(last, limit)) }
            if (table != null) {
                if (context.size >= 2) add(table[context.joinToString(" ")])
                add(table[last])
            }
            return out.toList()
        }
    }
}
