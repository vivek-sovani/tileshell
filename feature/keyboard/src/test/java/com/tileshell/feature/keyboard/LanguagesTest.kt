package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanguagesTest {

    @get:Rule val tmp = TemporaryFolder()

    private val en = TypingMode(KeyboardLanguage.ENGLISH, letters = true)
    private val mrLetters = TypingMode(KeyboardLanguage.MARATHI, letters = true)
    private val mrKeys = TypingMode(KeyboardLanguage.MARATHI, letters = false)
    private val hiLetters = TypingMode(KeyboardLanguage.HINDI, letters = true)
    private val hiKeys = TypingMode(KeyboardLanguage.HINDI, letters = false)

    @Test
    fun `the globe steps through every language in every style`() {
        val s = KeyboardSettings(marathi = true, hindi = true)
        assertEquals(listOf(en, mrLetters, mrKeys, hiLetters, hiKeys), s.modes)
        assertEquals(mrLetters, s.modeAfter(1))
        assertEquals(mrKeys, s.copy(language = KeyboardLanguage.MARATHI, translit = true).modeAfter(1))
        assertEquals(en, s.copy(language = KeyboardLanguage.HINDI, translit = false).modeAfter(1))
        assertEquals(hiKeys, s.modeAfter(-1))
    }

    @Test
    fun `a style switched off is skipped`() {
        val s = KeyboardSettings(marathi = true, hindi = false, styleKeys = false)
        assertEquals(listOf(en, mrLetters), s.modes)
        // Last typed on Devanagari keys, now off: English letters instead.
        val was = s.copy(language = KeyboardLanguage.MARATHI, translit = false)
        assertEquals(mrLetters, was.activeMode)
        assertTrue(was.lettersNow)
        assertEquals(listOf(en, mrKeys), KeyboardSettings(styleLetters = false).modes)
    }

    @Test
    fun `a language switched off falls back to english`() {
        val s = KeyboardSettings(marathi = false, hindi = false, language = KeyboardLanguage.MARATHI)
        assertEquals(KeyboardLanguage.ENGLISH, s.activeLanguage)
        assertEquals(listOf(en), s.modes)
        assertEquals(en, s.modeAfter(1))
    }

    @Test
    fun `transliteration picks are remembered across restarts`() {
        val file = tmp.newFile("picks.txt")
        TranslitMemory(file).remember("namaskar", "नमस्कर")
        assertEquals("नमस्कर", TranslitMemory(file)["namaskar"])
        assertNull(TranslitMemory(file)["dhanyavad"])
    }

    @Test
    fun `clearing forgets picks`() {
        val m = TranslitMemory(null)
        m.remember("maza", "माजा")
        m.clear()
        assertNull(m["maza"])
    }
}
