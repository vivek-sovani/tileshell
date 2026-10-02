package com.tileshell.feature.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingRulesTest {

    private val sentences = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    private val plain = InputType.TYPE_CLASS_TEXT

    @Test
    fun `shift cycles once, lock, off`() {
        assertEquals(ShiftState.ONCE, ShiftState.OFF.tapped())
        assertEquals(ShiftState.LOCKED, ShiftState.ONCE.tapped())
        assertEquals(ShiftState.OFF, ShiftState.LOCKED.tapped())
        assertFalse(ShiftState.OFF.upperCase)
        assertTrue(ShiftState.LOCKED.upperCase)
    }

    @Test
    fun `sentence capital at start, after end punctuation and after a new line`() {
        assertTrue(TypingRules.autoCapital("", sentences))
        assertTrue(TypingRules.autoCapital(null, sentences))
        assertTrue(TypingRules.autoCapital("Done. ", sentences))
        assertTrue(TypingRules.autoCapital("Really?  ", sentences))
        assertTrue(TypingRules.autoCapital("Yes!\n", sentences))
        assertTrue(TypingRules.autoCapital("line\n", sentences))
    }

    @Test
    fun `no sentence capital mid sentence or straight after the full stop`() {
        assertFalse(TypingRules.autoCapital("Leaving now ", sentences))
        assertFalse(TypingRules.autoCapital("Leaving", sentences))
        assertFalse(TypingRules.autoCapital("e.g.", sentences))
        assertFalse(TypingRules.autoCapital("Hi, ", sentences))
    }

    @Test
    fun `fields without capital flags never auto capitalise`() {
        assertFalse(TypingRules.autoCapital("", plain))
        assertFalse(TypingRules.autoCapital("Done. ", plain))
        assertFalse(TypingRules.autoCapital("", InputType.TYPE_CLASS_NUMBER))
        val email = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertFalse(TypingRules.autoCapital("", email))
    }

    @Test
    fun `word and character capital flags`() {
        val words = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        assertTrue(TypingRules.autoCapital("Rahul ", words))
        assertFalse(TypingRules.autoCapital("Rahul", words))
        val chars = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        assertTrue(TypingRules.autoCapital("ab", chars))
    }

    @Test
    fun `double space after a word becomes a full stop`() {
        assertTrue(TypingRules.doubleSpacePeriod("see you ", previousKeyWasSpace = true))
        assertTrue(TypingRules.doubleSpacePeriod("at 4 ", previousKeyWasSpace = true))
        assertFalse(TypingRules.doubleSpacePeriod("see you ", previousKeyWasSpace = false))
        assertFalse(TypingRules.doubleSpacePeriod("done. ", previousKeyWasSpace = true))
        assertFalse(TypingRules.doubleSpacePeriod(" ", previousKeyWasSpace = true))
        assertFalse(TypingRules.doubleSpacePeriod("word", previousKeyWasSpace = true))
        assertFalse(TypingRules.doubleSpacePeriod(null, previousKeyWasSpace = true))
    }

    @Test
    fun `enter takes the field's action`() {
        assertEquals(EnterAction.SEND, TypingRules.enterAction(EditorInfo.IME_ACTION_SEND, plain))
        assertEquals(EnterAction.SEARCH, TypingRules.enterAction(EditorInfo.IME_ACTION_SEARCH, plain))
        assertEquals(EnterAction.GO, TypingRules.enterAction(EditorInfo.IME_ACTION_GO, plain))
        assertEquals(EnterAction.NEXT, TypingRules.enterAction(EditorInfo.IME_ACTION_NEXT, plain))
        assertEquals(EnterAction.DONE, TypingRules.enterAction(EditorInfo.IME_ACTION_DONE, plain))
        assertEquals(EnterAction.NEW_LINE, TypingRules.enterAction(EditorInfo.IME_ACTION_UNSPECIFIED, plain))
    }

    @Test
    fun `enter types a new line when asked or in a multi-line done field`() {
        val noAction = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertEquals(EnterAction.NEW_LINE, TypingRules.enterAction(noAction, plain))
        val multi = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        assertEquals(EnterAction.NEW_LINE, TypingRules.enterAction(EditorInfo.IME_ACTION_DONE, multi))
    }

    @Test
    fun `number phone and date fields open on the number pad`() {
        assertEquals(KeyboardLayer.NUMPAD, TypingRules.startLayer(InputType.TYPE_CLASS_NUMBER))
        assertEquals(KeyboardLayer.NUMPAD, TypingRules.startLayer(InputType.TYPE_CLASS_PHONE))
        assertEquals(KeyboardLayer.NUMPAD, TypingRules.startLayer(InputType.TYPE_CLASS_DATETIME))
        assertEquals(KeyboardLayer.LETTERS, TypingRules.startLayer(sentences))
    }

    @Test
    fun `password fields are incognito, email and numbers get no suggestions`() {
        val pw = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(FieldMode.INCOGNITO, TypingRules.fieldMode(pw))
        val pin = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertEquals(FieldMode.INCOGNITO, TypingRules.fieldMode(pin))
        val email = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertEquals(FieldMode.NO_SUGGESTIONS, TypingRules.fieldMode(email))
        assertEquals(FieldMode.NO_SUGGESTIONS, TypingRules.fieldMode(InputType.TYPE_CLASS_PHONE))
        val noSuggest = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertEquals(FieldMode.NO_SUGGESTIONS, TypingRules.fieldMode(noSuggest))
        assertEquals(FieldMode.NORMAL, TypingRules.fieldMode(sentences))
    }

    @Test
    fun `nothing is learned in incognito fields`() {
        assertTrue(TypingRules.canLearn(sentences, 0))
        assertFalse(TypingRules.canLearn(sentences, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        val pw = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertFalse(TypingRules.canLearn(pw, 0))
    }

    @Test
    fun `current word is the letters before the cursor`() {
        assertEquals("mee", TypingRules.currentWord("see you at the mee", null))
        assertEquals("don't", TypingRules.currentWord("I don't", null))
        assertEquals("", TypingRules.currentWord("done ", null))
        assertEquals("", TypingRules.currentWord("4", null))
        assertEquals("", TypingRules.currentWord("mee", 't'))
        assertEquals("word", TypingRules.currentWord("'word", null))
    }

    @Test
    fun `sentence start`() {
        assertTrue(TypingRules.sentenceStart(""))
        assertTrue(TypingRules.sentenceStart("Done. "))
        assertTrue(TypingRules.sentenceStart("ok\n"))
        assertFalse(TypingRules.sentenceStart("I met "))
    }

    @Test
    fun `marathi and hindi can be typed in search boxes and address bars, not passwords or email`() {
        val search = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertTrue(TypingRules.languagesAllowed(search))
        val url = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        assertTrue(TypingRules.languagesAllowed(url))
        assertTrue(TypingRules.isWebAddress(url))
        assertFalse(TypingRules.isWebAddress(search))
        assertFalse(TypingRules.languagesAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(TypingRules.languagesAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(TypingRules.languagesAllowed(InputType.TYPE_CLASS_NUMBER))
    }

    @Test
    fun `devanagari words include their vowel signs`() {
        assertEquals("नमस्कार", TypingRules.currentWord("आणि नमस्कार", null))
        assertEquals("ज्येष्ठ", TypingRules.currentWord("ज्येष्ठ", null))
        assertEquals("", TypingRules.currentWord("नम", 'स'))
    }
}
