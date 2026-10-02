package com.tileshell.feature.keyboard

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.tileshell.core.data.settings.SettingsRepository
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.HubFilter
import com.tileshell.core.design.LumiaSwitch
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens

/**
 * Keyboard settings (canvas "Keyboard settings"), opened from the strip's
 * settings tool and from Android's keyboard list. Accent is TileShell's own,
 * so it's shown, not chosen here.
 */
class KeyboardSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = KeyboardPrefs.get(this)
        setContent { KeyboardSettingsScreen(prefs) }
    }
}

@Composable
private fun KeyboardSettingsScreen(prefs: KeyboardPrefs) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher by remember { SettingsRepository.create(context).settings }.collectAsState(initial = null)
    val settings by prefs.settings.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        KeyboardTheme.DARK -> true
        KeyboardTheme.LIGHT -> false
        KeyboardTheme.TILESHELL -> launcher?.let { if (it.followSystemTheme) systemDark else it.dark } ?: systemDark
    }
    val tokens = colorTokens(dark)
    val activity = context as? ComponentActivity
    SideEffect {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).run {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val accent = TileAccents.forId(launcher?.accentId)
    var learnedCount by remember { mutableIntStateOf(KeyboardDictionary.learnedCount(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column {
            BasicText(
                "SETTINGS",
                style = TextStyle(color = tokens.fgDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
            )
            BasicText(
                "keyboard",
                modifier = Modifier.semantics { heading() },
                style = TextStyle(color = tokens.fg, fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.02).em),
            )
        }

        Group("Theme", tokens) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (t in KeyboardTheme.entries) {
                    HubFilter(t.label, selected = settings.theme == t, tokens = tokens, accent = accent) {
                        prefs.update { it.copy(theme = t) }
                    }
                }
            }
        }

        Group("Accent colour", tokens) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(accent))
                Spacer(Modifier.width(12.dp))
                BasicText(
                    "tileshell's accent — change it in personalize → colours",
                    style = TextStyle(color = tokens.fgDim, fontSize = 15.sp),
                )
            }
        }

        Toggle("Show suggestion strip", settings.suggestions, accent, tokens) { on -> prefs.update { it.copy(suggestions = on) } }
        Toggle("Swipe across letters to type", settings.swipe, accent, tokens) { on -> prefs.update { it.copy(swipe = on) } }
        Toggle("Autocorrect misspelt words", settings.autocorrect, accent, tokens) { on -> prefs.update { it.copy(autocorrect = on) } }

        Group("Autocorrect level", tokens) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (l in AutocorrectLevel.entries) {
                    HubFilter(
                        l.label,
                        selected = settings.autocorrect && settings.level == l,
                        tokens = tokens,
                        accent = accent,
                    ) { prefs.update { it.copy(level = l, autocorrect = true) } }
                }
            }
            Note(
                "Corrections happen when you press space or punctuation. Press backspace straight after to undo.",
                tokens,
            )
        }

        Toggle("Capitalise first letter of a sentence", settings.autoCapitals, accent, tokens) { on -> prefs.update { it.copy(autoCapitals = on) } }
        Toggle("Double-tap space for a full stop", settings.doubleSpacePeriod, accent, tokens) { on -> prefs.update { it.copy(doubleSpacePeriod = on) } }
        Toggle("Key press sound", settings.keySound, accent, tokens) { on -> prefs.update { it.copy(keySound = on) } }
        Toggle("Haptic feedback on key press", settings.vibrate, accent, tokens) { on -> prefs.update { it.copy(vibrate = on) } }

        Group("Haptic strength", tokens) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (h in HapticStrength.entries) {
                    HubFilter(
                        h.label,
                        selected = settings.vibrate && settings.haptic == h,
                        tokens = tokens,
                        accent = accent,
                    ) {
                        prefs.update { it.copy(haptic = h, vibrate = true) }
                        KeyHaptics(context).play(HapticKind.KEY_TAP, h)
                    }
                }
            }
            Note(
                "A short tick on each key, a firmer pulse on long-press, and a soft double tap when a swiped word is placed.",
                tokens,
            )
        }

        Group("Learned words", tokens) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    if (learnedCount == 1) "1 word" else "$learnedCount words",
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = tokens.fg, fontSize = 26.sp, fontWeight = FontWeight.Light),
                )
                if (learnedCount > 0) {
                    BasicText(
                        "clear",
                        modifier = Modifier
                            .clickable {
                                KeyboardDictionary.clearLearned(context)
                                learnedCount = 0
                                Toast.makeText(context, "learned words cleared", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        style = TextStyle(color = accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            Note(
                "Names and words the keyboard didn't know, kept only on this phone. Password fields never add to it.",
                tokens,
            )
        }

        Group("Typing languages", tokens) {
            BasicText("English (India)", style = TextStyle(color = tokens.fg, fontSize = 22.sp, fontWeight = FontWeight.Light))
            LanguageRow("मराठी", settings.marathi, accent, tokens) { on -> prefs.update { it.copy(marathi = on) } }
            LanguageRow("हिन्दी", settings.hindi, accent, tokens) { on -> prefs.update { it.copy(hindi = on) } }
            Note(
                "Type मराठी and हिन्दी in English letters — namaskar becomes नमस्कार when you press space. " +
                    "Tap the globe key beside emoji to switch language.",
                tokens,
            )
        }
    }
}

@Composable
private fun Group(label: String, tokens: ColorTokens, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(label, style = TextStyle(color = tokens.fgDim, fontSize = 15.sp))
        content()
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, accent: Color, tokens: ColorTokens, onChange: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!on) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(label, style = TextStyle(color = tokens.fgDim, fontSize = 15.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                if (on) "On" else "Off",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = tokens.fg, fontSize = 26.sp, fontWeight = FontWeight.Light),
            )
            LumiaSwitch(on, accent, tokens, onChange)
        }
    }
}

@Composable
private fun LanguageRow(name: String, on: Boolean, accent: Color, tokens: ColorTokens, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            name,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = tokens.fg, fontSize = 22.sp, fontWeight = FontWeight.Light),
        )
        LumiaSwitch(on, accent, tokens, onChange)
    }
}

@Composable
private fun Note(text: String, tokens: ColorTokens) {
    BasicText(text, style = TextStyle(color = tokens.fgDim, fontSize = 13.sp, lineHeight = 19.sp))
}
