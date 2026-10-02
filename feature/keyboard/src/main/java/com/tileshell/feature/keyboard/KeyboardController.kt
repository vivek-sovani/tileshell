package com.tileshell.feature.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the strip above the keys shows. */
enum class StripMode {
    /** Suggestions (or next words). */
    WORDS,

    /** No suggestions in this field: the tools (settings). */
    TOOLS,

    /** A password field: "incognito typing", nothing learned. */
    INCOGNITO,
}

/**
 * The keyboard's state (layer, shift, enter action, language, suggestion
 * strip) and every edit it makes through the service's current
 * InputConnection. Compose reads the state; key presses and strip taps come
 * back here.
 *
 * मराठी / हिन्दी are typed in English letters: they collect as composing text
 * (underlined in the field), the strip offers Devanagari spellings, and space
 * puts in the best one.
 */
class KeyboardController(
    private val service: InputMethodService,
    private val prefs: KeyboardPrefs,
    private val scope: CoroutineScope,
) {

    var layer by mutableStateOf(KeyboardLayer.LETTERS)
        private set
    var shift by mutableStateOf(ShiftState.OFF)
        private set
    var enterAction by mutableStateOf(EnterAction.NEW_LINE)
        private set
    var stripMode by mutableStateOf(StripMode.TOOLS)
        private set
    var strip by mutableStateOf<List<StripWord>>(emptyList())
        private set

    /** The language being typed (English in fields where only English makes sense). */
    var language by mutableStateOf(KeyboardLanguage.ENGLISH)
        private set

    /** The globe key shows while more than one language can be typed here. */
    var languageKey by mutableStateOf(false)
        private set

    /** The space bar's label: "English", "मराठी · abc". */
    val spaceLabel: String
        get() = if (language.indic) "${language.nativeName} · abc" else language.nativeName

    private val lexicons = HashMap<KeyboardLanguage, Lexicon>()
    private var inputType = 0
    private var fieldMode = FieldMode.NO_SUGGESTIONS
    private var learnHere = false
    private var previousKeyWasSpace = false

    /** English letters typed for a मराठी / हिन्दी word, shown as composing text. */
    private val latin = StringBuilder()

    /** The best Devanagari spelling of [latin], what space puts in. */
    private var translitBest: String? = null

    /** The last autocorrect, while backspace (or tapping the original) can still undo it. */
    private var lastCorrection: Correction? = null

    /** A space the keyboard added after a picked word; punctuation typed next takes its place. */
    private var autoSpaced = false
    private var stripJob: Job? = null

    private val settings get() = prefs.settings.value
    private val audio by lazy { service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    private val lexicon: Lexicon? get() = lexicons[language]
    private val translit: Boolean get() = language.indic

    private data class Correction(val original: String, val replacement: String, val separator: String)

    private val loading = HashSet<KeyboardLanguage>()

    /** Loads [lang]'s word list the first time it's needed (off the main thread). */
    private fun ensureLexicon(lang: KeyboardLanguage) {
        if (lang in lexicons || !loading.add(lang)) return
        scope.launch {
            runCatching { KeyboardDictionary.lexicon(service, lang) }
                .onSuccess {
                    lexicons[lang] = it
                    if (lang == language) refresh()
                }
                .onFailure { android.util.Log.w("TileShellKeyboard", "${lang.code} word list didn't load", it) }
            loading.remove(lang)
        }
    }

    fun onStartInput(info: EditorInfo?) {
        inputType = info?.inputType ?: 0
        val imeOptions = info?.imeOptions ?: 0
        enterAction = TypingRules.enterAction(imeOptions, inputType)
        layer = TypingRules.startLayer(inputType)
        fieldMode = TypingRules.fieldMode(inputType)
        learnHere = TypingRules.canLearn(inputType, imeOptions)
        shift = ShiftState.OFF
        latin.clear()
        translitBest = null
        resetTransient()
        language = effectiveLanguage()
        ensureLexicon(KeyboardLanguage.ENGLISH)
        ensureLexicon(language)
        refresh()
    }

    /**
     * The cursor moved. [composing] is false once the field no longer holds our
     * composing text (a tap elsewhere, or the app took it): the letters are then
     * left as they are.
     */
    fun onSelectionChanged(composing: Boolean) {
        if (!composing && latin.isNotEmpty()) {
            latin.clear()
            translitBest = null
        }
        refresh()
    }

    /** Settings changed (from the settings page). */
    fun onSettingsChanged() {
        val lang = effectiveLanguage()
        if (lang != language) {
            commitTranslit(separator = "")
            language = lang
            ensureLexicon(lang)
        }
        refresh()
    }

    /** The globe key: the next (+1) language that's on. */
    fun switchLanguage(step: Int = 1) {
        if (fieldMode != FieldMode.NORMAL || settings.languages.size < 2) return
        commitTranslit(separator = "")
        resetTransient()
        val next = settings.languageAfter(step)
        prefs.update { it.copy(language = next) }
        language = next
        ensureLexicon(next)
        shift = ShiftState.OFF
        refresh()
    }

    fun onKey(key: Key) {
        val wasSpace = previousKeyWasSpace
        val undo = lastCorrection
        val spaced = autoSpaced
        resetTransient()
        click(key.kind)
        when (key.kind) {
            KeyKind.CHAR -> {
                val ch = if (shift.upperCase) key.label.uppercase() else key.label
                if (translit) compose(ch) else commit(ch)
            }
            KeyKind.SYMBOL -> when {
                translit -> {
                    commitTranslit(separator = "")
                    commit(key.label)
                }
                key.label in PUNCTUATION -> punctuation(key.label, spaced)
                else -> commit(key.label)
            }
            KeyKind.SPACE -> {
                if (translit && latin.isNotEmpty()) {
                    commitTranslit(separator = " ")
                } else {
                    space(wasSpace)
                }
                previousKeyWasSpace = true
            }
            KeyKind.BACKSPACE -> when {
                translit && latin.isNotEmpty() -> uncompose()
                undo == null || !undoCorrection(undo, keepSeparator = false) -> deleteOne()
            }
            KeyKind.ENTER -> {
                commitTranslit(separator = "")
                enter()
            }
            KeyKind.SHIFT -> shift = shift.tapped()
            KeyKind.LAYER -> {
                commitTranslit(separator = "")
                layer = if (layer == KeyboardLayer.LETTERS) KeyboardLayer.SYMBOLS_1 else KeyboardLayer.LETTERS
            }
            KeyKind.PAGE -> layer =
                if (layer == KeyboardLayer.SYMBOLS_1) KeyboardLayer.SYMBOLS_2 else KeyboardLayer.SYMBOLS_1
            KeyKind.EMOJI -> {
                commitTranslit(separator = "")
                layer = KeyboardLayer.EMOJI
            }
            KeyKind.LANGUAGE -> switchLanguage()
        }
        // Shift is only re-read from the text after an edit, so tapping shift sticks.
        if (key.kind != KeyKind.SHIFT) refresh()
    }

    fun onEmoji(emoji: String) {
        resetTransient()
        click(KeyKind.CHAR)
        commit(emoji)
        refresh()
    }

    fun backToLetters() {
        layer = KeyboardLayer.LETTERS
    }

    /** Backspace held: repeats without undoing an autocorrect. */
    fun backspace() {
        resetTransient()
        click(KeyKind.BACKSPACE)
        if (translit && latin.isNotEmpty()) uncompose() else deleteOne()
        refresh()
    }

    /** A word tapped in the strip. */
    fun onStripWord(word: StripWord) {
        val ic = service.currentInputConnection ?: return
        val undo = lastCorrection
        resetTransient()
        if (translit) {
            // Composing: the letters or a Devanagari spelling. Not composing (next
            // words): just insert it.
            if (latin.isNotEmpty()) pickTranslit(word) else ic.commitText(word.text + " ", 1)
            autoSpaced = true
            refresh()
            return
        }
        if (word.kind == StripWord.Kind.UNDO) {
            if (undo != null) undoCorrection(undo, keepSeparator = true)
            refresh()
            return
        }
        val typed = typedWord()
        if (typed.isEmpty()) {
            ic.commitText(word.text + " ", 1)
        } else {
            replaceWord(typed, word.text, " ")
            if (word.kind == StripWord.Kind.TYPED) learn(typed)
        }
        autoSpaced = true
        refresh()
    }

    private fun resetTransient() {
        previousKeyWasSpace = false
        lastCorrection = null
        autoSpaced = false
    }

    // ---- मराठी / हिन्दी in English letters ----

    private fun compose(letter: String) {
        latin.append(letter)
        service.currentInputConnection?.setComposingText(latin, 1)
    }

    private fun uncompose() {
        latin.setLength(latin.length - 1)
        val ic = service.currentInputConnection ?: return
        if (latin.isEmpty()) {
            ic.setComposingText("", 1)
            ic.finishComposingText()
            translitBest = null
        } else {
            ic.setComposingText(latin, 1)
        }
    }

    /** Puts in the best Devanagari spelling (or the letters, if there's none yet). */
    private fun commitTranslit(separator: String) {
        if (latin.isEmpty()) return
        val best = translitBest ?: Transliterator.candidates(latin.toString(), language, lexicon).firstOrNull()
        finishTranslit((best ?: latin.toString()) + separator)
    }

    /** A strip word while composing: the letters as typed, or a Devanagari spelling. */
    private fun pickTranslit(word: StripWord) {
        val typed = latin.toString()
        if (word.kind == StripWord.Kind.TYPED) {
            finishTranslit("$typed ")
            return
        }
        if (learnHere) {
            KeyboardDictionary.translitPicks(service, language).remember(typed, word.text)
            if (lexicon?.lookup(word.text) == null) KeyboardDictionary.learnedWords(service, language).learn(word.text, strong = true)
        }
        finishTranslit(word.text + " ")
    }

    private fun finishTranslit(text: String) {
        latin.clear()
        translitBest = null
        service.currentInputConnection?.commitText(text, 1)
    }

    // ---- English ----

    private fun commit(text: String) {
        service.currentInputConnection?.commitText(text, 1)
    }

    private fun deleteOne() {
        // A key event (not deleteSurroundingText) so a selection, an emoji's
        // surrogate pair or a combined character goes in one step, as the
        // field itself decides.
        service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun space(previousWasSpace: Boolean) {
        val ic = service.currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)
        if (settings.doubleSpacePeriod && TypingRules.doubleSpacePeriod(before, previousWasSpace)) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
            return
        }
        if (!autocorrectWord(before, " ")) {
            ic.commitText(" ", 1)
            learn(TypingRules.currentWord(before, null))
        }
    }

    /** . , ! ? ; : — corrects the word before it, and replaces a space the keyboard added. */
    private fun punctuation(mark: String, spaced: Boolean) {
        val ic = service.currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)
        if (spaced && before?.endsWith(" ") == true) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText("$mark ", 1)
            ic.endBatchEdit()
            return
        }
        if (!autocorrectWord(before, mark)) ic.commitText(mark, 1)
    }

    /**
     * Replaces the word before the cursor with its correction followed by
     * [separator], remembering it for undo. False when there's nothing to fix.
     */
    private fun autocorrectWord(before: CharSequence?, separator: String): Boolean {
        val lex = lexicon ?: return false
        if (!settings.autocorrect || fieldMode != FieldMode.NORMAL || before == null) return false
        val word = TypingRules.currentWord(before, null)
        if (word.isEmpty()) return false
        // A capital typed mid-sentence is a name: leave it.
        val beforeWord = before.subSequence(0, before.length - word.length)
        if (word[0].isUpperCase() && !TypingRules.sentenceStart(beforeWord)) return false
        val fix = Suggester.correction(word, lex, settings.level) ?: return false
        replaceWord(word, fix, separator)
        lastCorrection = Correction(word, fix, separator)
        return true
    }

    /** Puts the original word back; it's learned so it isn't corrected again. */
    private fun undoCorrection(c: Correction, keepSeparator: Boolean): Boolean {
        val ic = service.currentInputConnection ?: return false
        val tail = c.replacement + c.separator
        val before = ic.getTextBeforeCursor(tail.length, 0) ?: return false
        if (before.toString() != tail) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(tail.length, 0)
        ic.commitText(if (keepSeparator) c.original + c.separator else c.original, 1)
        ic.endBatchEdit()
        if (learnHere) KeyboardDictionary.learnedWords(service).learn(c.original, strong = true)
        return true
    }

    private fun replaceWord(word: String, replacement: String, separator: String) {
        val ic = service.currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(replacement + separator, 1)
        ic.endBatchEdit()
    }

    /** A word kept as typed that the list doesn't know: learned (twice = known). */
    private fun learn(word: String) {
        if (!learnHere || word.length < 2) return
        val lex = lexicon ?: return
        if (lex.lookup(word.lowercase()) == null) KeyboardDictionary.learnedWords(service).learn(word)
    }

    private fun enter() {
        val ic = service.currentInputConnection ?: return
        if (enterAction == EnterAction.NEW_LINE) {
            service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            ic.performEditorAction(enterAction.imeAction)
        }
    }

    private fun click(kind: KeyKind) {
        if (!settings.keySound) return
        val effect = when (kind) {
            KeyKind.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyKind.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
            KeyKind.ENTER -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audio?.playSoundEffect(effect, -1f)
    }

    private fun typedWord(): String {
        val ic = service.currentInputConnection ?: return ""
        val after = ic.getTextAfterCursor(1, 0)?.firstOrNull()
        return TypingRules.currentWord(ic.getTextBeforeCursor(CONTEXT_CHARS, 0), after)
    }

    /** Passwords, email and web addresses are always typed in English letters. */
    private fun effectiveLanguage(): KeyboardLanguage =
        if (fieldMode == FieldMode.NORMAL) settings.activeLanguage else KeyboardLanguage.ENGLISH

    /** Re-reads the text round the cursor: auto capital and the strip. */
    private fun refresh() {
        languageKey = fieldMode == FieldMode.NORMAL && settings.languages.size > 1
        val ic = service.currentInputConnection
        val before = ic?.getTextBeforeCursor(CONTEXT_CHARS, 0)
        if (shift != ShiftState.LOCKED) {
            // Devanagari has no capitals; in मराठी / हिन्दी shift is only for T, D, N…
            val auto = !translit && settings.autoCapitals && TypingRules.autoCapital(before, inputType)
            shift = if (auto) ShiftState.ONCE else ShiftState.OFF
        }
        refreshStrip(ic?.getTextAfterCursor(1, 0)?.firstOrNull(), before)
    }

    private fun refreshStrip(after: Char?, before: CharSequence?) {
        stripJob?.cancel()
        val lex = lexicon
        stripMode = when {
            fieldMode == FieldMode.INCOGNITO -> StripMode.INCOGNITO
            fieldMode == FieldMode.NO_SUGGESTIONS -> StripMode.TOOLS
            translit -> StripMode.WORDS
            !settings.suggestions || lex == null -> StripMode.TOOLS
            else -> StripMode.WORDS
        }
        if (stripMode != StripMode.WORDS) {
            strip = emptyList()
            return
        }
        if (translit) {
            refreshTranslitStrip(lex)
            return
        }
        if (lex == null) return
        // The undo offer lasts only while the corrected word is still right before
        // the cursor (the field may have changed it, or the cursor moved away).
        lastCorrection?.let { c ->
            if (before?.endsWith(c.replacement + c.separator) == true) {
                strip = Suggester.undoStrip(c.original, c.replacement, lex)
                return
            }
            lastCorrection = null
        }
        val word = TypingRules.currentWord(before, after)
        if (word.isEmpty()) {
            val upper = shift.upperCase
            strip = Suggester.NEXT_WORDS.map {
                StripWord(if (upper) it.replaceFirstChar(Char::uppercaseChar) else it, StripWord.Kind.WORD)
            }
            return
        }
        val autocorrect = settings.autocorrect
        val level = settings.level
        stripJob = scope.launch {
            strip = withContext(Dispatchers.Default) { Suggester.strip(word, lex, level, autocorrect) }
        }
    }

    /** "namaskar" (dim) · **नमस्कार** · alternatives — the canvas's transliteration strip. */
    private fun refreshTranslitStrip(lex: Lexicon?) {
        if (latin.isEmpty()) {
            translitBest = null
            strip = NEXT_WORDS_INDIC[language].orEmpty().map { StripWord(it, StripWord.Kind.WORD) }
            return
        }
        val typed = latin.toString()
        val lang = language
        val remembered = KeyboardDictionary.translitPicks(service, lang)[typed]
        stripJob = scope.launch {
            val words = withContext(Dispatchers.Default) {
                Transliterator.candidates(typed, lang, lex, remembered)
            }
            // Still the same letters (typing may have moved on).
            if (latin.toString() != typed) return@launch
            translitBest = words.firstOrNull()
            strip = listOf(StripWord(typed, StripWord.Kind.TYPED)) +
                words.mapIndexed { i, w -> StripWord(w, StripWord.Kind.WORD, best = i == 0) }
        }
    }

    private companion object {
        const val CONTEXT_CHARS = 64
        val PUNCTUATION = setOf(".", ",", "!", "?", ";", ":")

        /** Common sentence starts, shown before anything is typed. */
        val NEXT_WORDS_INDIC = mapOf(
            KeyboardLanguage.MARATHI to listOf("मी", "आहे", "नाही", "का"),
            KeyboardLanguage.HINDI to listOf("मैं", "है", "नहीं", "क्या"),
        )
    }
}
