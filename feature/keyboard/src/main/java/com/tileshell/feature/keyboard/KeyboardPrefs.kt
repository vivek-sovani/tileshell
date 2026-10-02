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
)

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
            .apply()
        state.value = next
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

        @Volatile private var instance: KeyboardPrefs? = null

        fun get(context: Context): KeyboardPrefs = instance ?: synchronized(this) {
            instance ?: KeyboardPrefs(
                context.applicationContext.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}
