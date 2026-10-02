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
    fun `number phone and date fields open on the digits page`() {
        assertEquals(KeyboardLayer.SYMBOLS_1, TypingRules.startLayer(InputType.TYPE_CLASS_NUMBER))
        assertEquals(KeyboardLayer.SYMBOLS_1, TypingRules.startLayer(InputType.TYPE_CLASS_PHONE))
        assertEquals(KeyboardLayer.SYMBOLS_1, TypingRules.startLayer(InputType.TYPE_CLASS_DATETIME))
        assertEquals(KeyboardLayer.LETTERS, TypingRules.startLayer(sentences))
    }
}
