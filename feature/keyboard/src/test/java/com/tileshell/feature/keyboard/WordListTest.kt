package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordListTest {

    private val list = WordList.parse(
        sequenceOf(
            "the\t222", "then\t180", "there\t190", "theory\t120", "thermal\t90",
            "I\t196", "London\t150", "us\t180", "US\t120", "rude\t100\tx", ">im\tI'm",
        ),
    )

    @Test
    fun `looks words up case-insensitively, keeping the usual spelling`() {
        assertEquals("the", list.lookup("the")?.word)
        assertEquals("I", list.lookup("i")?.word)
        assertEquals("London", list.lookup("london")?.word)
        // The lower-case spelling wins when both exist.
        assertEquals("us", list.lookup("us")?.word)
        assertEquals(180, list.lookup("us")?.freq)
        assertNull(list.lookup("thx"))
    }

    @Test
    fun `completions are most common first and skip blocked words`() {
        assertEquals(listOf("the", "there", "then"), list.completions("the", 3).map { it.word })
        assertTrue(list.completions("ru", 5).isEmpty())
        assertTrue(list.lookup("rude")!!.blocked)
    }

    @Test
    fun `shortcuts`() {
        assertEquals("I'm", list.shortcut("im"))
        assertNull(list.shortcut("the"))
    }
}
