package com.tileshell.feature.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Shift, per the build spec: tap once for one capital, again for caps lock
 * (the arrow fills), again for off. An automatic capital at the start of a
 * sentence is [ONCE] too, so tapping shift there locks, as in the prototype.
 */
enum class ShiftState {
    OFF, ONCE, LOCKED;

    fun tapped(): ShiftState = when (this) {
        OFF -> ONCE
        ONCE -> LOCKED
        LOCKED -> OFF
    }

    val upperCase: Boolean get() = this != OFF
}

/** What the enter key does in the focused field. */
enum class EnterAction(val label: String?, val imeAction: Int) {
    NEW_LINE(null, EditorInfo.IME_ACTION_NONE),
    GO("go", EditorInfo.IME_ACTION_GO),
    SEARCH("search", EditorInfo.IME_ACTION_SEARCH),
    SEND("send", EditorInfo.IME_ACTION_SEND),
    NEXT("next", EditorInfo.IME_ACTION_NEXT),
    PREVIOUS("back", EditorInfo.IME_ACTION_PREVIOUS),
    DONE("done", EditorInfo.IME_ACTION_DONE),
}

/** What the suggestion strip may do in the focused field. */
enum class FieldMode {
    /** Suggestions, autocorrect and learning. */
    NORMAL,

    /** Numbers, email addresses, web addresses, or a field that asks for none. */
    NO_SUGGESTIONS,

    /** Passwords: strip hidden, nothing learned. */
    INCOGNITO,
}

object TypingRules {

    fun fieldMode(inputType: Int): FieldMode {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_NUMBER) {
            return if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) FieldMode.INCOGNITO else FieldMode.NO_SUGGESTIONS
        }
        if (cls != InputType.TYPE_CLASS_TEXT) return FieldMode.NO_SUGGESTIONS
        return when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> FieldMode.INCOGNITO
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI -> FieldMode.NO_SUGGESTIONS
            else ->
                if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) FieldMode.NO_SUGGESTIONS
                else FieldMode.NORMAL
        }
    }

    /** New words are learned only in ordinary fields that don't ask for no learning (incognito tabs). */
    fun canLearn(inputType: Int, imeOptions: Int): Boolean =
        fieldMode(inputType) == FieldMode.NORMAL &&
            imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0

    /**
     * The word being typed: the letters (and inner apostrophes) right before the
     * cursor. Empty when the cursor sits inside a word ([charAfter] is a letter).
     */
    fun currentWord(textBefore: CharSequence?, charAfter: Char?): String {
        if (charAfter != null && charAfter.isLetterOrDigit()) return ""
        val t = textBefore ?: return ""
        var i = t.length
        while (i > 0 && (t[i - 1].isLetter() || (t[i - 1] == '\'' && i - 1 > 0 && t[i - 2].isLetter()))) i--
        return t.substring(i).trimStart('\'')
    }

    /** True when [beforeWord] ends a sentence (or is empty): a capital there isn't a name. */
    fun sentenceStart(beforeWord: CharSequence): Boolean {
        val t = beforeWord.trimEnd { it == ' ' || it == '\t' }
        return t.isEmpty() || t.last() in SENTENCE_END || t.last() == '\n'
    }

    /**
     * The enter key's job: a multi-line field, or one that asks for no enter
     * action, gets a new line; otherwise the field's own action (send, search…).
     */
    fun enterAction(imeOptions: Int, inputType: Int): EnterAction {
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.NEW_LINE
        val multiLine = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            EditorInfo.IME_ACTION_DONE -> if (multiLine) EnterAction.NEW_LINE else EnterAction.DONE
            else -> EnterAction.NEW_LINE
        }
    }

    /**
     * Whether the next letter should be a capital. Follows the field's own
     * capitalisation flags (as Android keyboards do — a search box or an email
     * address never auto-capitalises): sentences start after . ! ? and after a
     * new line; word caps after any space.
     */
    fun autoCapital(textBefore: CharSequence?, inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) return true
        val words = inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0
        val sentences = inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
        if (!words && !sentences) return false
        val text = textBefore ?: ""
        if (text.isEmpty() || text.last() == '\n') return true
        if (words && text.last().isWhitespace()) return true
        if (!text.last().isWhitespace()) return false
        val end = text.trimEnd { it == ' ' || it == '\t' }
        return end.isEmpty() || end.last() in SENTENCE_END
    }

    /**
     * Space tapped right after another space: when the space follows a letter
     * or digit, it becomes ". " (the first space is replaced). Returns true when
     * that should happen.
     */
    fun doubleSpacePeriod(textBefore: CharSequence?, previousKeyWasSpace: Boolean): Boolean {
        if (!previousKeyWasSpace) return false
        val t = textBefore ?: return false
        if (t.length < 2 || t[t.length - 1] != ' ') return false
        return t[t.length - 2].isLetterOrDigit()
    }

    /** Number, phone and date fields open on the digits page. */
    fun startLayer(inputType: Int): KeyboardLayer = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME ->
            KeyboardLayer.SYMBOLS_1
        else -> KeyboardLayer.LETTERS
    }

    private val SENTENCE_END = setOf('.', '!', '?')
}
