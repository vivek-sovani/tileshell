package com.tileshell.feature.keyboard

/** The emoji panel's tabs, in the canvas's order along its bottom row. */
enum class EmojiTab(val code: String, val label: String, val icon: PanelIcon) {
    RECENT("recent", "recent", PanelIcon.RECENT),
    SMILEYS("smileys", "smileys and people", PanelIcon.SMILEYS),
    NATURE("nature", "animals and nature", PanelIcon.NATURE),
    FOOD("food", "food and drink", PanelIcon.FOOD),
    TRAVEL("travel", "travel and activities", PanelIcon.TRAVEL),
    SYMBOLS("symbols", "objects, symbols and flags", PanelIcon.SYMBOLS),
}

/**
 * Every emoji, by tab, with its Unicode name for search ("grinning face").
 * From Unicode's emoji-test.txt (`assets/keyboard/emoji.txt`, built by
 * tools/keyboard/build_emoji.py): fully-qualified, no skin-tone variants.
 * Pure — the lines come in, and a glyph check drops emoji the phone's font
 * can't draw — so it's unit-tested.
 */
class EmojiCatalog(lines: Sequence<String>, canDraw: (String) -> Boolean = { true }) {

    data class Emoji(val glyph: String, val name: String)

    private val byTab: Map<EmojiTab, List<Emoji>>
    private val all: List<Emoji>

    init {
        val tabs = EmojiTab.entries.associateBy { it.code }
        val grouped = LinkedHashMap<EmojiTab, MutableList<Emoji>>()
        val everything = ArrayList<Emoji>()
        for (line in lines) {
            val parts = line.split('\t')
            if (parts.size < 3) continue
            val tab = tabs[parts[0]] ?: continue
            if (!canDraw(parts[1])) continue
            val e = Emoji(parts[1], parts[2])
            grouped.getOrPut(tab) { ArrayList() } += e
            everything += e
        }
        byTab = grouped
        all = everything
    }

    fun tab(tab: EmojiTab): List<String> = byTab[tab].orEmpty().map { it.glyph }

    /**
     * Emoji whose name has a word starting with every word of [query]
     * ("hea" → heart…, "red hea" → red heart), whole-word matches first.
     */
    fun search(query: String, limit: Int = 40): List<String> {
        val words = query.lowercase().split(' ', '-').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return all.asSequence()
            .mapNotNull { e ->
                val nameWords = e.name.lowercase().split(' ', '-', ':', ',')
                if (!words.all { w -> nameWords.any { it.startsWith(w) } }) return@mapNotNull null
                val exact = words.count { w -> nameWords.any { it == w } }
                e to (exact * 100 - e.name.length)
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first.glyph }
            .toList()
    }

    companion object {
        const val RECENT_MAX = 32

        /** [recent] with [emoji] moved to the front, capped. */
        fun pushRecent(recent: List<String>, emoji: String): List<String> =
            (listOf(emoji) + recent.filter { it != emoji }).take(RECENT_MAX)
    }
}

/**
 * The emoji a typed word stands for ("pizza" → 🍕, "lol" → 😂), offered in the
 * suggestion strip. From Unicode CLDR's keywords by
 * `tools/keyboard/build_emoji_words.py` (`assets/keyboard/emoji_words.txt`,
 * `word<TAB>emoji`): a common word only where the emoji is named exactly that,
 * plus everyday chat words.
 */
class EmojiWords(lines: Sequence<String>, canDraw: (String) -> Boolean = { true }) {

    private val map = HashMap<String, String>()

    init {
        for (line in lines) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val emoji = line.substring(tab + 1).substringBefore(' ').trim()
            if (emoji.isNotEmpty() && canDraw(emoji)) map[line.substring(0, tab)] = emoji
        }
    }

    val size: Int get() = map.size

    /** The emoji for [word] (any case; a plural too: "pizzas" → 🍕), or null. */
    operator fun get(word: String): String? {
        val lower = word.lowercase()
        if (lower.length < 2) return null
        return map[lower]
            ?: lower.takeIf { it.length > 3 && it.endsWith("es") }?.let { map[it.dropLast(2)] }
            ?: lower.takeIf { it.length > 3 && it.endsWith('s') }?.let { map[it.dropLast(1)] }
    }
}
