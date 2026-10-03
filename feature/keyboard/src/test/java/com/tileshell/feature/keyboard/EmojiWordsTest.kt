package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class EmojiWordsTest {

    private val words = EmojiWords(File("src/main/assets/keyboard/emoji_words.txt").readLines().asSequence())

    @Test
    fun `everyday words have their emoji`() {
        assertEquals("🍕", words["pizza"])
        assertEquals("🍕", words["Pizza"])
        assertEquals("😂", words["lol"])
        assertEquals("❤️", words["love"])
        assertEquals("🎂", words["birthday"])
        assertEquals("🙏", words["thanks"])
        assertEquals("🥭", words["mango"])
        assertEquals("🇮🇳", words["india"])
        assertEquals("🏏", words["cricket"])
    }

    @Test
    fun `plurals find the singular's emoji`() {
        assertEquals("🍕", words["pizzas"])
        assertEquals("🚲", words["bikes"])
    }

    @Test
    fun `small and loosely matched words get none`() {
        for (w in listOf("this", "for", "from", "the", "a", "small", "middle", "central", "i")) {
            assertNull(w, words[w])
        }
    }

    @Test
    fun `an emoji the phone can't draw is left out`() {
        val noPizza = EmojiWords(sequenceOf("pizza\t🍕", "love\t❤️")) { it != "🍕" }
        assertNull(noPizza["pizza"])
        assertEquals("❤️", noPizza["love"])
    }

    @Test
    fun `the emoji takes the strip's last place`() {
        val strip = listOf(
            StripWord("piza", StripWord.Kind.TYPED),
            StripWord("pizza", StripWord.Kind.WORD, best = true),
            StripWord("pita", StripWord.Kind.WORD),
            StripWord("pizzas", StripWord.Kind.WORD),
        )
        val with = StripWord.withEmoji(strip, "🍕")
        assertEquals(listOf("piza", "pizza", "pita", "🍕"), with.map { it.text })
        assertEquals(StripWord.Kind.EMOJI, with.last().kind)
        assertEquals(strip, StripWord.withEmoji(strip, null))
        assertEquals(listOf("pizza", "🍕"), StripWord.withEmoji(listOf(StripWord("pizza", StripWord.Kind.TYPED)), "🍕").map { it.text })
    }
}
