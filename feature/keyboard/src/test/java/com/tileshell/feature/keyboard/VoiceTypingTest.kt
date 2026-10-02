package com.tileshell.feature.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTypingTest {
    @Test
    fun `voice listens in the keyboard's language`() {
        assertEquals("en-IN", VoiceTyping.localeFor(KeyboardLanguage.ENGLISH))
        assertEquals("mr-IN", VoiceTyping.localeFor(KeyboardLanguage.MARATHI))
        assertEquals("hi-IN", VoiceTyping.localeFor(KeyboardLanguage.HINDI))
        assertEquals("English (India)", VoiceTyping.labelFor(KeyboardLanguage.ENGLISH))
        assertEquals("मराठी", VoiceTyping.labelFor(KeyboardLanguage.MARATHI))
    }
}
