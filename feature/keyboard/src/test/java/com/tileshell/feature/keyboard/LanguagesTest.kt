package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanguagesTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `space bar swipes cycle through the languages that are on`() {
        val s = KeyboardSettings(marathi = true, hindi = true, language = KeyboardLanguage.ENGLISH)
        assertEquals(KeyboardLanguage.MARATHI, s.languageAfter(1))
        assertEquals(KeyboardLanguage.HINDI, s.languageAfter(-1))
        assertEquals(KeyboardLanguage.ENGLISH, s.copy(language = KeyboardLanguage.HINDI).languageAfter(1))
    }

    @Test
    fun `a language switched off falls back to english`() {
        val s = KeyboardSettings(marathi = false, hindi = false, language = KeyboardLanguage.MARATHI)
        assertEquals(KeyboardLanguage.ENGLISH, s.activeLanguage)
        assertEquals(listOf(KeyboardLanguage.ENGLISH), s.languages)
        assertEquals(KeyboardLanguage.ENGLISH, s.languageAfter(1))
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
