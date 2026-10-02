package com.tileshell.feature.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The keyboard's state (layer, shift, enter action) and every edit it makes
 * through the service's current InputConnection. Compose reads the state; key
 * presses come back here.
 */
class KeyboardController(private val service: InputMethodService) {

    var layer by mutableStateOf(KeyboardLayer.LETTERS)
        private set
    var shift by mutableStateOf(ShiftState.OFF)
        private set
    var enterAction by mutableStateOf(EnterAction.NEW_LINE)
        private set

    private var inputType = 0
    private var previousKeyWasSpace = false

    fun onStartInput(info: EditorInfo?) {
        inputType = info?.inputType ?: 0
        enterAction = TypingRules.enterAction(info?.imeOptions ?: 0, inputType)
        layer = TypingRules.startLayer(inputType)
        shift = ShiftState.OFF
        previousKeyWasSpace = false
        refreshShift()
    }

    /** The cursor moved (typed text or a tap in the field): re-check the auto capital. */
    fun onSelectionChanged() {
        refreshShift()
    }

    fun onKey(key: Key) {
        val wasSpace = previousKeyWasSpace
        previousKeyWasSpace = false
        when (key.kind) {
            KeyKind.CHAR -> commit(if (shift.upperCase) key.label.uppercase() else key.label)
            KeyKind.SYMBOL -> commit(key.label)
            KeyKind.SPACE -> {
                space(wasSpace)
                previousKeyWasSpace = true
            }
            KeyKind.BACKSPACE -> backspace()
            KeyKind.ENTER -> enter()
            KeyKind.SHIFT -> shift = shift.tapped()
            KeyKind.LAYER -> layer =
                if (layer == KeyboardLayer.LETTERS) KeyboardLayer.SYMBOLS_1 else KeyboardLayer.LETTERS
            KeyKind.PAGE -> layer =
                if (layer == KeyboardLayer.SYMBOLS_1) KeyboardLayer.SYMBOLS_2 else KeyboardLayer.SYMBOLS_1
            KeyKind.EMOJI -> layer = KeyboardLayer.EMOJI
        }
    }

    fun onEmoji(emoji: String) {
        previousKeyWasSpace = false
        commit(emoji)
    }

    fun backToLetters() {
        layer = KeyboardLayer.LETTERS
    }

    /** Also the repeat step while backspace is held. */
    fun backspace() {
        previousKeyWasSpace = false
        // A key event (not deleteSurroundingText) so a selection, an emoji's
        // surrogate pair or a combined character goes in one step, as the
        // field itself decides.
        service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        refreshShift()
    }

    private fun commit(text: String) {
        val ic = service.currentInputConnection ?: return
        ic.commitText(text, 1)
        refreshShift()
    }

    private fun space(previousWasSpace: Boolean) {
        val ic = service.currentInputConnection ?: return
        if (TypingRules.doubleSpacePeriod(ic.getTextBeforeCursor(2, 0), previousWasSpace)) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
        } else {
            ic.commitText(" ", 1)
        }
        refreshShift()
    }

    private fun enter() {
        val ic = service.currentInputConnection ?: return
        if (enterAction == EnterAction.NEW_LINE) {
            service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            ic.performEditorAction(enterAction.imeAction)
        }
        refreshShift()
    }

    private fun refreshShift() {
        if (shift == ShiftState.LOCKED) return
        val before = service.currentInputConnection?.getTextBeforeCursor(CONTEXT_CHARS, 0)
        shift = if (TypingRules.autoCapital(before, inputType)) ShiftState.ONCE else ShiftState.OFF
    }

    private companion object {
        const val CONTEXT_CHARS = 64
    }
}
