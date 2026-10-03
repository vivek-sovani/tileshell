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

    /** The space bar is moving the cursor. */
    CURSOR,
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

    /** The strip's tools row is open (the menu tool). */
    var toolsOpen by mutableStateOf(false)
        private set

    var emojiTab by mutableStateOf(EmojiTab.SMILEYS)
        private set

    /** Typing a search into the emoji panel's box: keys go to [emojiQuery], not the field. */
    var emojiSearch by mutableStateOf(false)
        private set
    var emojiQuery by mutableStateOf("")
        private set
    var emojiCatalog by mutableStateOf<EmojiCatalog?>(null)
        private set

    /** The clipboard panel's clips, newest first. */
    var clips by mutableStateOf<List<Clip>>(emptyList())
        private set

    /** A copy made a moment ago, offered as a one-tap paste in the strip. */
    var freshClip by mutableStateOf<String?>(null)
        private set

    private val clipStore by lazy { ClipStore(java.io.File(service.filesDir, "keyboard_clips.txt")) }

    /** Voice typing, in the current language; the heard text goes in like a typed word. */
    val voice by lazy { VoiceTyping(service) { text -> commitVoice(text) } }

    /** The space bar is being dragged to move the cursor: labels blank out. */
    var cursorMode by mutableStateOf(false)
        private set

    /** The globe key shows while more than one language can be typed here. */
    var languageKey by mutableStateOf(false)
        private set

    /** The space bar's label: "English", "abc-मराठी" / "abc-हिंदी" (English letters), "मराठी" (Devanagari keys). */
    val spaceLabel: String
        get() = if (translit) "abc-${if (language == KeyboardLanguage.HINDI) "हिंदी" else language.nativeName}" else language.nativeName

    /** मराठी / हिन्दी on the Devanagari keys (settings: not in English letters). */
    var devanagariKeys by mutableStateOf(false)
        private set

    /** The smart vowel row shows full vowels (no consonant just before the cursor). */
    var fullVowels by mutableStateOf(true)
        private set

    private val lexicons = HashMap<KeyboardLanguage, Lexicon>()

    /** English words as मराठी / हिन्दी write them (energy → एनर्जी). */
    private val loanWords = HashMap<KeyboardLanguage, LoanWords>()

    /** Which words usually come next, per language. */
    private val nextTables = HashMap<KeyboardLanguage, NextWords>()

    /** A word was just put in: the next refresh learns it as following the word before. */
    private var learnPairNext = false

    /** मराठी / हिन्दी words by their English-letter spelling, for swipe typing. */
    private val swipeLexicons = HashMap<KeyboardLanguage, RomanizedLexicon>()
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

    /** The last swiped word and its alternatives, while the strip can still swap it. */
    private var lastSwipe: Swipe? = null

    private data class Swipe(val words: List<String>, val chosen: String)

    private val haptics by lazy { KeyHaptics(service) }

    /** A swipe across letters can type a word here (in मराठी / हिन्दी, the Devanagari word). */
    val swipeEnabled: Boolean
        get() = settings.swipe && layer == KeyboardLayer.LETTERS && !devanagariKeys &&
            (if (translit) languagesHere && language in swipeLexicons else fieldMode == FieldMode.NORMAL && lexicon != null)

    /** A space the keyboard added after a picked word; punctuation typed next takes its place. */
    private var autoSpaced = false
    private var stripJob: Job? = null

    private val settings get() = prefs.settings.value
    private val audio by lazy { service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    private val lexicon: Lexicon? get() = lexicons[language]
    /** मराठी / हिन्दी typed in English letters, shown as composing text. */
    private val translit: Boolean get() = language.indic && settings.lettersNow

    private data class Correction(val original: String, val replacement: String, val separator: String)

    private val loading = HashSet<KeyboardLanguage>()

    /** Loads [lang]'s word list the first time it's needed (off the main thread). */
    private fun ensureLexicon(lang: KeyboardLanguage) {
        if (lang in lexicons || !loading.add(lang)) return
        scope.launch {
            runCatching { KeyboardDictionary.lexicon(service, lang) }
                .onSuccess {
                    lexicons[lang] = it
                    runCatching { KeyboardDictionary.nextWords(service, lang) }
                        .onSuccess { table -> nextTables[lang] = table }
                        .onFailure { e -> android.util.Log.w("TileShellKeyboard", "${lang.code} next words didn't load", e) }
                    if (lang == language) refresh()
                    if (lang.indic) {
                        runCatching { KeyboardDictionary.loanWords(service, lang) }.getOrNull()
                            ?.let { loans -> loanWords[lang] = loans }
                        val list = it.words as? WordList
                        if (list != null) {
                            swipeLexicons[lang] = withContext(Dispatchers.Default) {
                                RomanizedLexicon(list.entries() + it.learned.known().asSequence(), lang)
                            }
                        }
                    }
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
        toolsOpen = false
        emojiSearch = false
        if (layer == KeyboardLayer.VOICE) voice.stop()
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
    /** The globe key: the next mode — a language, and for Marathi / Hindi its style. */
    fun switchLanguage(step: Int = 1) {
        if (!languagesHere || settings.modes.size < 2) return
        commitTranslit(separator = "")
        resetTransient()
        val next = settings.modeAfter(step)
        prefs.update { it.copy(language = next.language, translit = if (next.language.indic) next.letters else it.translit) }
        language = next.language
        ensureLexicon(next.language)
        shift = ShiftState.OFF
        refresh()
    }

    fun onKey(key: Key) {
        if (emojiSearch && searchKey(key)) return
        val wasSpace = previousKeyWasSpace
        val undo = lastCorrection
        val spaced = autoSpaced
        resetTransient()
        click(key.kind)
        when (key.kind) {
            KeyKind.CHAR -> when {
                devanagariKeys -> commit(if (fullVowels && key.independent != null) key.independent else key.label)
                translit -> compose(if (shift.upperCase) key.label.uppercase() else key.label)
                else -> commit(if (shift.upperCase) key.label.uppercase() else key.label)
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
            KeyKind.EMOJI -> openEmoji()
            KeyKind.LANGUAGE -> switchLanguage()
        }
        // Shift is only re-read from the text after an edit, so tapping shift sticks.
        if (key.kind != KeyKind.SHIFT) refresh()
    }

    fun onEmoji(emoji: String) {
        resetTransient()
        click(KeyKind.CHAR)
        commit(emoji)
        prefs.addRecentEmoji(emoji)
        refresh()
    }

    // ---- emoji panel ----

    fun openEmoji() {
        commitTranslit(separator = "")
        toolsOpen = false
        emojiSearch = false
        if (emojiCatalog == null) scope.launch { emojiCatalog = EmojiStore.catalog(service) }
        emojiTab = if (prefs.recentEmoji().isNotEmpty()) EmojiTab.RECENT else EmojiTab.SMILEYS
        layer = KeyboardLayer.EMOJI
    }

    fun selectEmojiTab(tab: EmojiTab) {
        emojiTab = tab
    }

    /** The emoji in [tab]: the recent list from settings, the rest from the catalogue. */
    fun emojiFor(tab: EmojiTab): List<String> =
        if (tab == EmojiTab.RECENT) prefs.recentEmoji() else emojiCatalog?.tab(tab).orEmpty()

    fun startEmojiSearch() {
        emojiQuery = ""
        emojiSearch = true
        shift = ShiftState.OFF
        layer = KeyboardLayer.LETTERS
    }

    fun endEmojiSearch() {
        emojiSearch = false
        emojiQuery = ""
        layer = KeyboardLayer.EMOJI
    }

    /** Results for the search box, best first. */
    val emojiResults: List<String>
        get() = emojiCatalog?.search(emojiQuery).orEmpty()

    /** Keys while searching emoji edit the query; false lets the key act normally. */
    private fun searchKey(key: Key): Boolean {
        click(key.kind)
        when (key.kind) {
            KeyKind.CHAR, KeyKind.SYMBOL -> emojiQuery += key.label.lowercase()
            KeyKind.SPACE -> emojiQuery += " "
            KeyKind.BACKSPACE -> emojiQuery = emojiQuery.dropLast(1)
            KeyKind.ENTER, KeyKind.EMOJI -> endEmojiSearch()
            KeyKind.SHIFT -> Unit
            else -> {
                emojiSearch = false
                return false
            }
        }
        return true
    }

    // ---- tools row, clipboard, one-handed ----

    fun toggleTools() {
        toolsOpen = !toolsOpen
        if (!toolsOpen && layer == KeyboardLayer.CLIPBOARD) layer = KeyboardLayer.LETTERS
    }

    fun toggleClipboard() {
        if (layer == KeyboardLayer.CLIPBOARD) {
            layer = TypingRules.startLayer(inputType).let { if (it == KeyboardLayer.NUMPAD) it else KeyboardLayer.LETTERS }
            return
        }
        commitTranslit(separator = "")
        clips = clipStore.list(System.currentTimeMillis())
        layer = KeyboardLayer.CLIPBOARD
    }

    fun pasteClip(text: String) {
        commitTranslit(separator = "")
        resetTransient()
        commit(text)
        freshClip = null
        refresh()
    }

    fun togglePin(clip: Clip) {
        clipStore.togglePin(clip.text)
        clips = clipStore.list(System.currentTimeMillis())
    }

    fun deleteClip(clip: Clip) {
        clipStore.delete(clip.text)
        clips = clipStore.list(System.currentTimeMillis())
        if (freshClip == clip.text) freshClip = null
    }

    fun clearClips() {
        clipStore.clearUnpinned()
        clips = clipStore.list(System.currentTimeMillis())
        freshClip = null
    }

    /**
     * Something was copied. Kept for the clipboard panel unless the copying app
     * marked it sensitive (a password) or history is off; offered in the strip
     * for a minute.
     */
    fun onClipboardChanged(text: String?, sensitive: Boolean) {
        if (text.isNullOrBlank() || sensitive || !settings.clipboardHistory) return
        val now = System.currentTimeMillis()
        clipStore.add(text, now)
        clips = clipStore.list(now)
        freshClip = text.trim()
        scope.launch {
            kotlinx.coroutines.delay(ClipStore.FRESH_MS)
            if (freshClip == text.trim()) freshClip = null
        }
    }

    // ---- voice typing ----

    /** The tools row's mic: opens the voice panel and starts listening (again: back to the keys). */
    fun toggleVoice() {
        // Voice typing is left out of builds with tileshell.keyboardVoice=false.
        if (!BuildConfig.VOICE) return
        if (layer == KeyboardLayer.VOICE) {
            closeVoice()
            return
        }
        commitTranslit(separator = "")
        resetTransient()
        layer = KeyboardLayer.VOICE
        voice.start(language)
    }

    /** The big mic: stop and put in what was heard, or listen (again). */
    fun voiceTap() {
        when (voice.state) {
            VoiceState.Listening -> voice.finish()
            else -> {
                voice.clearProblem()
                voice.start(language)
            }
        }
    }

    fun closeVoice() {
        voice.stop()
        voice.clearProblem()
        layer = KeyboardLayer.LETTERS
        toolsOpen = false
    }

    /** The keyboard was hidden: stop listening. */
    fun onHidden() {
        if (layer == KeyboardLayer.VOICE) closeVoice()
    }

    private fun commitVoice(text: String) {
        val ic = service.currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(1, 0)
        val lead = if (!before.isNullOrEmpty() && !before.last().isWhitespace() && before.last() !in OPENERS) " " else ""
        ic.commitText(lead + text + " ", 1)
        autoSpaced = true
        haptic(HapticKind.CONFIRM)
        refresh()
    }

    /** Both Marathi / Hindi styles are on, so the tools row's अ / abc button can flip them. */
    val bothStyles: Boolean get() = settings.styleLetters && settings.styleKeys

    /** The tools row's अ / abc button: Devanagari keys ↔ English letters. */
    fun toggleInputStyle() {
        commitTranslit(separator = "")
        prefs.update { it.copy(translit = !it.translit) }
    }

    /** The tools row's one-handed button: on (keys to the right) / off. */
    fun toggleOneHand() {
        val next = if (settings.oneHand == OneHand.OFF) OneHand.RIGHT else OneHand.OFF
        prefs.update { it.copy(oneHand = next) }
    }

    /** One-handed panel: move the keys to the other side. */
    fun switchOneHandSide() {
        val next = if (settings.oneHand == OneHand.LEFT) OneHand.RIGHT else OneHand.LEFT
        prefs.update { it.copy(oneHand = next) }
    }

    fun fullSize() {
        prefs.update { it.copy(oneHand = OneHand.OFF) }
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
        val swiped = lastSwipe
        resetTransient()
        if (swiped != null && word.text in swiped.words && replaceSwipe(swiped, word.text)) {
            refresh()
            return
        }
        if (translit) {
            // Composing: the letters or a Devanagari spelling. Not composing (next
            // words): just insert it.
            if (latin.isNotEmpty()) pickTranslit(word) else ic.commitText(word.text + " ", 1)
            learnPairNext = true
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
        learnPairNext = true
        autoSpaced = true
        refresh()
    }

    private fun resetTransient() {
        previousKeyWasSpace = false
        lastCorrection = null
        lastSwipe = null
        autoSpaced = false
    }

    fun haptic(kind: HapticKind) {
        if (settings.vibrate) haptics.play(kind, settings.haptic)
    }

    // ---- long press ----

    fun popupOptions(key: Key): List<String> = KeyPopups.options(key, shift.upperCase, lettersOnly = translit)

    /** A letter, digit or symbol picked from the long-press bar, typed as if its own key. */
    fun onPopupChoice(option: String) {
        // Already cased in the bar (casing it again from shift changes nothing).
        onKey(Key(if (option.first().isLetter()) KeyKind.CHAR else KeyKind.SYMBOL, option))
    }

    // ---- space-bar cursor ----

    fun beginCursor() {
        commitTranslit(separator = "")
        resetTransient()
        cursorMode = true
        refresh()
    }

    /** Moves the cursor [steps] characters (negative = left). */
    fun moveCursor(steps: Int) {
        val ic = service.currentInputConnection ?: return
        val text = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        if (text?.text != null && text.selectionEnd >= 0) {
            val end = text.startOffset + text.text.length
            val at = (text.startOffset + text.selectionEnd + steps).coerceIn(0, end)
            ic.setSelection(at, at)
        } else {
            val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            repeat(kotlin.math.abs(steps)) { service.sendDownUpKeyEvents(code) }
        }
    }

    fun endCursor() {
        cursorMode = false
        refresh()
    }

    // ---- swipe typing ----

    /** Places the best word for a swipe; the alternatives go to the strip. */
    fun onSwipe(path: List<Pt>, decoder: SwipeDecoder) {
        val indic = if (translit) swipeLexicons[language] ?: return else null
        val lex = indic ?: lexicon ?: return
        commitTranslit(separator = " ")
        resetTransient()
        val upper = shift
        scope.launch {
            val results = withContext(Dispatchers.Default) { decoder.decode(path, lex) }
            val words = if (indic != null) {
                // The Devanagari words behind the matched spellings.
                results.flatMap { indic.devanagariFor(it.word) }.distinct().take(Suggester.STRIP_SIZE)
            } else {
                results.map {
                    when (upper) {
                        ShiftState.LOCKED -> it.word.uppercase()
                        ShiftState.ONCE -> it.word.replaceFirstChar(Char::uppercaseChar)
                        ShiftState.OFF -> it.word
                    }
                }
            }
            if (words.isEmpty()) return@launch
            val ic = service.currentInputConnection ?: return@launch
            val before = ic.getTextBeforeCursor(1, 0)
            val lead = if (!before.isNullOrEmpty() && !before.last().isWhitespace() && before.last() !in OPENERS) " " else ""
            ic.commitText(lead + words.first() + " ", 1)
            learnPairNext = true
            lastSwipe = Swipe(words, words.first())
            autoSpaced = true
            haptic(HapticKind.CONFIRM)
            refresh()
        }
    }

    /** Swaps the swiped word for an alternative tapped in the strip. */
    private fun replaceSwipe(s: Swipe, with: String): Boolean {
        val ic = service.currentInputConnection ?: return false
        val tail = s.chosen + " "
        if (ic.getTextBeforeCursor(tail.length, 0)?.toString() != tail) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(tail.length, 0)
        ic.commitText("$with ", 1)
        ic.endBatchEdit()
        lastSwipe = s.copy(chosen = with)
        autoSpaced = true
        // Picking the meant word teaches it, so the same swipe gives it next time.
        if (learnHere) KeyboardDictionary.learnedWords(service, language).learn(with, strong = true)
        return true
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
        val typed = latin.toString()
        val best = translitBest ?: translitCandidates(typed, language, lexicon, null).firstOrNull()
        // An English word kept in English letters isn't a मराठी / हिन्दी word to learn.
        if (best != null && best != typed) learnIndic(best, strong = false)
        finishTranslit((best ?: latin.toString()) + separator)
    }

    /**
     * A मराठी / हिन्दी word put in that the list doesn't have: learned (twice, or
     * once picked from the strip), and then offered and swipeable like the rest.
     */
    private fun learnIndic(word: String, strong: Boolean) {
        if (!learnHere || word.length < 2) return
        val lex = lexicon ?: return
        if (lex.lookup(word) != null) return
        val learned = KeyboardDictionary.learnedWords(service, language)
        learned.learn(word, strong)
        learned.entry(word)?.let { swipeLexicons[language]?.add(it) }
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
            learnIndic(word.text, strong = true)
        }
        finishTranslit(word.text + " ")
    }

    private fun finishTranslit(text: String) {
        latin.clear()
        translitBest = null
        learnPairNext = true
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
        learnPairNext = true
        if (devanagariKeys) {
            ic.commitText(" ", 1)
            learnIndic(TypingRules.currentWord(before, null), strong = false)
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
        if (!settings.autocorrect || fieldMode != FieldMode.NORMAL || before == null || language.indic) return false
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
    /**
     * The language a field opens in: the last one used, except where only
     * English makes sense (passwords, email) and web addresses, which start in
     * English but can be switched (a search typed into an address bar).
     */
    private fun effectiveLanguage(): KeyboardLanguage =
        if (languagesHere && !TypingRules.isWebAddress(inputType)) settings.activeLanguage else KeyboardLanguage.ENGLISH

    /** Marathi / Hindi can be typed in this field (any text field but passwords and email). */
    private val languagesHere: Boolean get() = TypingRules.languagesAllowed(inputType)

    /** Re-reads the text round the cursor: auto capital and the strip. */
    private fun refresh() {
        devanagariKeys = language.indic && !settings.lettersNow
        languageKey = languagesHere && settings.modes.size > 1 && layer != KeyboardLayer.NUMPAD
        val ic = service.currentInputConnection
        val before = ic?.getTextBeforeCursor(CONTEXT_CHARS, 0)
        fullVowels = !TypingRules.endsInConsonant(before)
        if (shift != ShiftState.LOCKED) {
            // Devanagari has no capitals; in मराठी / हिन्दी shift is only for T, D, N…
            val auto = !language.indic && settings.autoCapitals && TypingRules.autoCapital(before, inputType)
            shift = if (auto) ShiftState.ONCE else ShiftState.OFF
        }
        refreshStrip(ic?.getTextAfterCursor(1, 0)?.firstOrNull(), before)
    }

    private fun refreshStrip(after: Char?, before: CharSequence?) {
        stripJob?.cancel()
        if (learnPairNext) {
            learnPairNext = false
            val ctx = NextWords.context(before)
            if (learnHere && ctx.size == 2) KeyboardDictionary.learnedPairs(service, language).learn(ctx[0], ctx[1])
        }
        val lex = lexicon
        stripMode = when {
            cursorMode -> StripMode.CURSOR
            fieldMode == FieldMode.INCOGNITO -> StripMode.INCOGNITO
            // Typing मराठी / हिन्दी needs its spellings even where English gets no suggestions.
            translit || devanagariKeys -> StripMode.WORDS
            fieldMode == FieldMode.NO_SUGGESTIONS -> StripMode.TOOLS
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
        lastSwipe?.let { sw ->
            if (before?.endsWith(sw.chosen + " ") == true) {
                strip = sw.words.map { StripWord(it, StripWord.Kind.WORD, best = it == sw.chosen) }
                return
            }
            lastSwipe = null
        }
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
        if (word.isEmpty() && devanagariKeys) {
            val next = fitIdle(predictions(before).ifEmpty { NEXT_WORDS_INDIC[language].orEmpty() })
            strip = next.map { StripWord(it, StripWord.Kind.WORD) }
            return
        }
        if (word.isEmpty()) {
            val upper = shift.upperCase
            // "I" stays a capital; the rest as the list writes them.
            val next = fitIdle(predictions(before).map { lex.lookup(it)?.word ?: it }.ifEmpty { Suggester.NEXT_WORDS })
            strip = next.map {
                StripWord(if (upper) it.replaceFirstChar(Char::uppercaseChar) else it, StripWord.Kind.WORD)
            }
            return
        }
        // No autocorrect on Devanagari keys: its corrections are for English spellings.
        val autocorrect = settings.autocorrect && !devanagariKeys
        val level = settings.level
        val next = predictions(withoutCurrentWord(before), PREDICT_FOR_RANKING)
        stripJob = scope.launch {
            strip = withContext(Dispatchers.Default) { Suggester.strip(word, lex, level, autocorrect, next) }
        }
    }

    /** "namaskar" (dim) · **नमस्कार** · alternatives — the canvas's transliteration strip. */
    private fun refreshTranslitStrip(lex: Lexicon?) {
        val before = service.currentInputConnection?.getTextBeforeCursor(CONTEXT_CHARS, 0)
        lastSwipe?.let { sw ->
            if (latin.isEmpty() && before?.endsWith(sw.chosen + " ") == true) {
                strip = sw.words.map { StripWord(it, StripWord.Kind.WORD, best = it == sw.chosen) }
                return
            }
            lastSwipe = null
        }
        if (latin.isEmpty()) {
            translitBest = null
            val next = fitIdle(predictions(before).ifEmpty { NEXT_WORDS_INDIC[language].orEmpty() })
            strip = next.map { StripWord(it, StripWord.Kind.WORD) }
            return
        }
        val typed = latin.toString()
        val lang = language
        val remembered = KeyboardDictionary.translitPicks(service, lang)[typed]
        val next = predictions(withoutCurrentWord(before), PREDICT_FOR_RANKING)
        stripJob = scope.launch {
            val words = withContext(Dispatchers.Default) {
                translitCandidates(typed, lang, lex, remembered, next)
            }
            // Still the same letters (typing may have moved on).
            if (latin.toString() != typed) return@launch
            translitBest = words.firstOrNull()
            // An English word with no मराठी / हिन्दी spelling leads as typed.
            val englishFirst = words.firstOrNull() == typed
            strip = listOf(StripWord(typed, StripWord.Kind.TYPED, best = englishFirst)) +
                words.filter { it != typed }.mapIndexed { i, w -> StripWord(w, StripWord.Kind.WORD, best = !englishFirst && i == 0) }
        }
    }

    private fun translitCandidates(
        typed: String,
        lang: KeyboardLanguage,
        lex: Lexicon?,
        remembered: String?,
        next: List<String> = emptyList(),
    ) = Transliterator.candidates(
        typed, lang, lex, remembered,
        loans = loanWords[lang], english = lexicons[KeyboardLanguage.ENGLISH], next = next,
    )

    /**
     * The usual next words after the text before the cursor in the current
     * language: the greeting for the time of day, the user's own pairs, then the
     * bundled tables.
     */
    private fun predictions(before: CharSequence?, limit: Int = Suggester.STRIP_SIZE): List<String> {
        val ctx = NextWords.context(before)
        if (ctx.isEmpty()) return emptyList()
        return NextWords.predict(
            ctx,
            nextTables[language],
            KeyboardDictionary.learnedPairs(service, language),
            language,
            java.time.LocalTime.now().hour,
            limit,
        )
    }

    /**
     * As many next words as fit beside the strip's two icons (afternoon,
     * evening, night — "morning" would be cut off), always at least one.
     */
    private fun fitIdle(words: List<String>): List<String> {
        var used = 0
        return words.filterIndexed { i, w ->
            used += w.length + IDLE_WORD_PADDING
            i == 0 || used <= IDLE_STRIP_CHARS
        }
    }

    /** The text before the word being typed (its letters dropped), for that word's context. */
    private fun withoutCurrentWord(before: CharSequence?): CharSequence? =
        before?.toString()?.dropLastWhile { it.isLetter() || it == '\'' || it in '\u0900'..'\u097F' }

    private companion object {
        const val CONTEXT_CHARS = 64

        /** How many likely next words rank the word being typed. */
        const val PREDICT_FOR_RANKING = 12

        /** The idle strip's width in characters, and each word's padding in the same units. */
        const val IDLE_STRIP_CHARS = 34
        const val IDLE_WORD_PADDING = 3
        val PUNCTUATION = setOf(".", ",", "!", "?", ";", ":")

        /** No space is put before a swiped word right after these. */
        const val OPENERS = "([{\"'“‘"

        /** Common sentence starts, shown before anything is typed. */
        val NEXT_WORDS_INDIC = mapOf(
            KeyboardLanguage.MARATHI to listOf("मी", "आहे", "नाही", "का"),
            KeyboardLanguage.HINDI to listOf("मैं", "है", "नहीं", "क्या"),
        )
    }
}
