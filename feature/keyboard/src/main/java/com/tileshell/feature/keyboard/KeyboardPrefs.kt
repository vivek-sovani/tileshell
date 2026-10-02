package com.tileshell.feature.keyboard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keyboard theme: TileShell's own theme by default, or forced. */
enum class KeyboardTheme(val label: String) {
    TILESHELL("match tileshell"),
    DARK("dark"),
    LIGHT("light"),
}

/** A language and, for Marathi / Hindi, how it's typed: English letters or Devanagari keys. */
data class TypingMode(val language: KeyboardLanguage, val letters: Boolean)

/** One-handed mode: the keys pushed to one side, the canvas's side panel on the other. */
enum class OneHand { OFF, LEFT, RIGHT }

/** The keyboard's settings page (canvas "Keyboard settings"). */
data class KeyboardSettings(
    val theme: KeyboardTheme = KeyboardTheme.TILESHELL,
    val suggestions: Boolean = true,
    val autocorrect: Boolean = true,
    val level: AutocorrectLevel = AutocorrectLevel.BALANCED,
    val autoCapitals: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val keySound: Boolean = false,
    val vibrate: Boolean = true,
    val haptic: HapticStrength = HapticStrength.MEDIUM,
    /** Swipe across letters to type a word. */
    val swipe: Boolean = true,
    val oneHand: OneHand = OneHand.OFF,
    /** Keep copied text for the clipboard panel (an hour unless pinned). */
    val clipboardHistory: Boolean = true,
    /** Typed in English letters, written in Devanagari. English is always on. */
    val marathi: Boolean = true,
    val hindi: Boolean = false,
    /** Marathi / Hindi typed in English letters (true) or on Devanagari keys (false), last used. */
    val translit: Boolean = true,
    /** The two ways to type Marathi / Hindi offered on the globe key (at least one stays on). */
    val styleLetters: Boolean = true,
    val styleKeys: Boolean = true,
    /** The language last typed in; the globe key moves through the ones on. */
    val language: KeyboardLanguage = KeyboardLanguage.ENGLISH,
) {
    /**
     * What the globe key steps through: English, then each Marathi / Hindi in
     * each style that's on — मराठी · abc (English letters), मराठी (Devanagari keys).
     */
    val modes: List<TypingMode>
        get() = buildList {
            add(TypingMode(KeyboardLanguage.ENGLISH, letters = true))
            for (lang in languages.drop(1)) {
                if (styleLetters || !styleKeys) add(TypingMode(lang, letters = true))
                if (styleKeys) add(TypingMode(lang, letters = false))
            }
        }

    /** The mode in use: the last language and style, if still on. */
    val activeMode: TypingMode
        get() = modes.firstOrNull { it.language == activeLanguage && (it.language == KeyboardLanguage.ENGLISH || it.letters == translit) }
            ?: modes.firstOrNull { it.language == activeLanguage }
            ?: modes.first()

    /** Marathi / Hindi in English letters right now (else Devanagari keys). */
    val lettersNow: Boolean get() = activeMode.letters

    fun modeAfter(step: Int): TypingMode {
        val list = modes
        return list[Math.floorMod(list.indexOf(activeMode) + step, list.size)]
    }

    /** The languages the space bar cycles through, English first. */
    val languages: List<KeyboardLanguage>
        get() = buildList {
            add(KeyboardLanguage.ENGLISH)
            if (marathi) add(KeyboardLanguage.MARATHI)
            if (hindi) add(KeyboardLanguage.HINDI)
        }

    /** The language now in use: the last one, if still on. */
    val activeLanguage: KeyboardLanguage
        get() = if (language in languages) language else KeyboardLanguage.ENGLISH

}

/**
 * Keyboard-only settings in `keyboard_prefs` (accent and the default theme
 * come from TileShell's settings). SharedPreferences rather than DataStore:
 * tiny, read synchronously by the input method on every key.
 */
class KeyboardPrefs private constructor(private val prefs: SharedPreferences) {

    private val state = MutableStateFlow(read())
    val settings: StateFlow<KeyboardSettings> = state.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = read() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun update(change: (KeyboardSettings) -> KeyboardSettings) {
        val next = change(state.value)
        prefs.edit()
            .putString(THEME, next.theme.name)
            .putBoolean(SUGGESTIONS, next.suggestions)
            .putBoolean(AUTOCORRECT, next.autocorrect)
            .putString(LEVEL, next.level.name)
            .putBoolean(CAPS, next.autoCapitals)
            .putBoolean(PERIOD, next.doubleSpacePeriod)
            .putBoolean(SOUND, next.keySound)
            .putBoolean(VIBRATE, next.vibrate)
            .putString(HAPTIC, next.haptic.name)
            .putBoolean(SWIPE, next.swipe)
            .putString(ONE_HAND, next.oneHand.name)
            .putBoolean(CLIPBOARD, next.clipboardHistory)
            .putBoolean(MARATHI, next.marathi)
            .putBoolean(HINDI, next.hindi)
            .putBoolean(TRANSLIT, next.translit)
            .putBoolean(STYLE_LETTERS, next.styleLetters)
            .putBoolean(STYLE_KEYS, next.styleKeys)
            .putString(LANGUAGE, next.language.name)
            .apply()
        state.value = next
    }

    /** Emoji used lately, newest first (the emoji panel's "recent" tab). */
    fun recentEmoji(): List<String> =
        prefs.getString(EMOJI_RECENT, null)?.split('\u0001')?.filter { it.isNotEmpty() }.orEmpty()

    fun addRecentEmoji(emoji: String) {
        val next = EmojiCatalog.pushRecent(recentEmoji(), emoji)
        prefs.edit().putString(EMOJI_RECENT, next.joinToString("\u0001")).apply()
    }

    private fun read(): KeyboardSettings {
        val d = KeyboardSettings()
        return KeyboardSettings(
            theme = KeyboardTheme.entries.find { it.name == prefs.getString(THEME, null) } ?: d.theme,
            suggestions = prefs.getBoolean(SUGGESTIONS, d.suggestions),
            autocorrect = prefs.getBoolean(AUTOCORRECT, d.autocorrect),
            level = AutocorrectLevel.entries.find { it.name == prefs.getString(LEVEL, null) } ?: d.level,
            autoCapitals = prefs.getBoolean(CAPS, d.autoCapitals),
            doubleSpacePeriod = prefs.getBoolean(PERIOD, d.doubleSpacePeriod),
            keySound = prefs.getBoolean(SOUND, d.keySound),
            vibrate = prefs.getBoolean(VIBRATE, d.vibrate),
            haptic = HapticStrength.entries.find { it.name == prefs.getString(HAPTIC, null) } ?: d.haptic,
            swipe = prefs.getBoolean(SWIPE, d.swipe),
            oneHand = OneHand.entries.find { it.name == prefs.getString(ONE_HAND, null) } ?: d.oneHand,
            clipboardHistory = prefs.getBoolean(CLIPBOARD, d.clipboardHistory),
            marathi = prefs.getBoolean(MARATHI, d.marathi),
            hindi = prefs.getBoolean(HINDI, d.hindi),
            translit = prefs.getBoolean(TRANSLIT, d.translit),
            styleLetters = prefs.getBoolean(STYLE_LETTERS, d.styleLetters),
            styleKeys = prefs.getBoolean(STYLE_KEYS, d.styleKeys),
            language = KeyboardLanguage.entries.find { it.name == prefs.getString(LANGUAGE, null) } ?: d.language,
        )
    }

    companion object {
        private const val THEME = "theme"
        private const val SUGGESTIONS = "suggestions"
        private const val AUTOCORRECT = "autocorrect"
        private const val LEVEL = "autocorrect_level"
        private const val CAPS = "auto_capitals"
        private const val PERIOD = "double_space_period"
        private const val SOUND = "key_sound"
        private const val VIBRATE = "vibrate"
        private const val HAPTIC = "haptic_strength"
        private const val SWIPE = "swipe"
        private const val ONE_HAND = "one_hand"
        private const val CLIPBOARD = "clipboard_history"
        private const val EMOJI_RECENT = "emoji_recent"
        private const val MARATHI = "lang_marathi"
        private const val HINDI = "lang_hindi"
        private const val TRANSLIT = "translit"
        private const val STYLE_LETTERS = "style_letters"
        private const val STYLE_KEYS = "style_keys"
        private const val LANGUAGE = "language"

        @Volatile private var instance: KeyboardPrefs? = null

        fun get(context: Context): KeyboardPrefs = instance ?: synchronized(this) {
            instance ?: KeyboardPrefs(
                context.applicationContext.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
