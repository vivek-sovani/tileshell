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

    /**
     * Marathi / Hindi may be typed: any text field except passwords and email
     * addresses — search boxes too, which often ask for no suggestions.
     */
    fun languagesAllowed(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> false
            else -> true
        }
    }

    /** A web address field (a browser's address bar): opens in English. */
    fun isWebAddress(inputType: Int): Boolean =
        inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_URI

    /** New words are learned only in ordinary fields that don't ask for no learning (incognito tabs). */
    fun canLearn(inputType: Int, imeOptions: Int): Boolean =
        fieldMode(inputType) == FieldMode.NORMAL &&
            imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0

    /**
     * The word being typed: the letters (and inner apostrophes) right before the
     * cursor. Empty when the cursor sits inside a word ([charAfter] is a letter).
     */
    fun currentWord(textBefore: CharSequence?, charAfter: Char?): String {
        if (charAfter != null && (charAfter.isLetterOrDigit() || isWordChar(charAfter))) return ""
        val t = textBefore ?: return ""
        var i = t.length
        while (i > 0 && (isWordChar(t[i - 1]) || (t[i - 1] == '\'' && i - 1 > 0 && t[i - 2].isLetter()))) i--
        return t.substring(i).trimStart('\'')
    }

    /** A letter, or a Devanagari vowel sign / virama / anusvara (marks, not letters). */
    private fun isWordChar(c: Char): Boolean {
        if (c.isLetter()) return true
        val type = Character.getType(c)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }

    /**
     * The text ends in a Devanagari consonant that has no vowel sign yet (क,
     * not का or क्) — where the smart vowel row offers vowel signs.
     */
    fun endsInConsonant(textBefore: CharSequence?): Boolean {
        val c = textBefore?.lastOrNull() ?: return false
        return c in '\u0915'..'\u0939' || c in '\u0958'..'\u095F' || c in '\u0978'..'\u097F' || c == '\u093C'
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

    /**
     * How many spaces to take out before a . , ! ? ; or : so it sits on the
     * word ("hello  ." → "hello."): the spaces (not line breaks) right before the
     * cursor, when a word, number, closing bracket / quote or emoji comes before
     * them. 0 when there are none, or nothing word-like before them.
     */
    fun spacesBeforePunctuation(textBefore: CharSequence?): Int {
        val t = textBefore ?: return 0
        var n = 0
        while (n < t.length && t[t.length - 1 - n] == ' ') n++
        if (n == 0 || n == t.length) return 0
        val c = t[t.length - 1 - n]
        val attaches = c.isLetterOrDigit() || c in '\u0900'..'\u097F' || c in ")]}\"'”’" ||
            Character.isSurrogate(c) || Character.getType(c) == Character.OTHER_SYMBOL.toInt()
        return if (attaches) n else 0
    }

    /** Number, phone and date fields open on the number pad. */
    fun startLayer(inputType: Int): KeyboardLayer = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME ->
            KeyboardLayer.NUMPAD
        else -> KeyboardLayer.LETTERS
    }

    private val SENTENCE_END = setOf('.', '!', '?')
}
