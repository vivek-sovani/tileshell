package com.tileshell.feature.keyboard

import android.content.Intent
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.settings.SettingsRepository
import com.tileshell.core.design.TileAccents
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Build-spec colour tokens (dark / light). The accent is TileShell's own. */
internal data class KeyboardColors(
    val background: Color,
    val letterKey: Color,
    val functionKey: Color,
    val text: Color,
    val secondary: Color,
) {
    companion object {
        val Dark = KeyboardColors(
            background = Color(0xFF161616),
            letterKey = Color(0xFF3A3A3A),
            functionKey = Color(0xFF262626),
            text = Color.White,
            secondary = Color(0xFFA3A3A3),
        )
        val Light = KeyboardColors(
            background = Color(0xFFDADADA),
            letterKey = Color.White,
            functionKey = Color(0xFFBFBFBF),
            text = Color.Black,
            secondary = Color(0xFF595959),
        )
    }
}

private val KEY_GAP = 6.dp
private val ROW_GAP = 8.dp
private val SIDE_PADDING = 4.dp
private val STRIP_HEIGHT = 46.dp
private const val PRESS_FLASH_MS = 140L
private const val REPEAT_START_MS = 400L
private const val REPEAT_EVERY_MS = 50L

/** Whether key presses tick the haptic (keyboard settings → haptic feedback). */
private val LocalKeyHaptics = staticCompositionLocalOf { true }

@Composable
internal fun KeyboardScreen(controller: KeyboardController, prefs: KeyboardPrefs) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository.create(context) }
    val settings by repository.settings.collectAsState(initial = null)
    val keyboard by prefs.settings.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (keyboard.theme) {
        KeyboardTheme.DARK -> true
        KeyboardTheme.LIGHT -> false
        KeyboardTheme.TILESHELL -> settings?.let { if (it.followSystemTheme) systemDark else it.dark } ?: systemDark
    }
    val colors = if (dark) KeyboardColors.Dark else KeyboardColors.Light
    val accent = TileAccents.forId(settings?.accentId)
    // Landscape: shorter keys, so the keyboard doesn't take most of the screen.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val keyHeight = if (landscape) 40.dp else 50.dp

    CompositionLocalProvider(LocalKeyHaptics provides keyboard.vibrate) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Strip(controller, colors, accent)
            val keysHeight = 2.dp + keyHeight * 4 + ROW_GAP * 3 + 12.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keysHeight)
                    .padding(start = SIDE_PADDING, end = SIDE_PADDING, top = 2.dp, bottom = 12.dp),
            ) {
                if (controller.layer == KeyboardLayer.EMOJI) {
                    EmojiPanel(controller, colors, accent, keyHeight)
                } else {
                    KeyRows(controller, colors, accent, keyHeight)
                }
            }
        }
    }
}

/**
 * The strip above the keys (canvas "Suggestion strip states"): suggestions with
 * the best guess underlined in the accent, the quoted original after an
 * autocorrect, "incognito typing" in password fields, or the tools.
 */
@Composable
private fun Strip(controller: KeyboardController, colors: KeyboardColors, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (controller.stripMode) {
            StripMode.INCOGNITO -> {
                Spacer(Modifier.width(12.dp))
                KeyIcon(KeyGlyph.LOCK, colors.secondary, iconSize = 18.dp)
                Spacer(Modifier.width(8.dp))
                BasicText("incognito typing", style = TextStyle(color = colors.secondary, fontSize = 14.sp))
            }
            StripMode.TOOLS -> {
                Spacer(Modifier.weight(1f))
                SettingsTool(colors)
            }
            StripMode.WORDS -> {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (word in controller.strip) StripWordCell(word, colors, accent) { controller.onStripWord(word) }
                }
                // Nothing typed yet: settings stays reachable beside the next words.
                if (controller.strip.none { it.kind != StripWord.Kind.WORD || it.best }) SettingsTool(colors)
            }
        }
    }
}

@Composable
private fun StripWordCell(word: StripWord, colors: KeyboardColors, accent: Color, onTap: () -> Unit) {
    // The typed word is dim when space would replace it; the undo word always is.
    val dim = word.kind == StripWord.Kind.UNDO || (word.kind == StripWord.Kind.TYPED && !word.best)
    Box(
        modifier = Modifier
            .height(44.dp)
            .clickable(onClick = onTap)
            .then(
                if (word.best) {
                    Modifier.drawBehind {
                        val h = 3.dp.toPx()
                        drawRect(accent, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = if (word.kind == StripWord.Kind.UNDO) "undo, ${word.text}" else word.text },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = word.text,
            maxLines = 1,
            style = TextStyle(
                color = if (dim) colors.secondary else colors.text,
                fontSize = 18.sp,
                fontWeight = if (word.best) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun SettingsTool(colors: KeyboardColors) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 44.dp)
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(context, KeyboardSettingsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            .semantics { contentDescription = "keyboard settings" },
        contentAlignment = Alignment.Center,
    ) {
        KeyIcon(KeyGlyph.SETTINGS, colors.secondary, filled = false, background = colors.background)
    }
}

@Composable
private fun KeyRows(controller: KeyboardController, colors: KeyboardColors, accent: Color, keyHeight: Dp) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 1 unit = (width − 9 gaps) ÷ 10 (the side padding is already outside).
        val unit = (maxWidth - KEY_GAP * 9) / 10
        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            for (row in KeyboardLayouts.rowsFor(controller.layer, controller.languageKey)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (row.inset) (unit + KEY_GAP) / 2 else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
                ) {
                    for (key in row.keys) {
                        KeyCell(key, controller, colors, accent, keyHeight)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyCell(
    key: Key,
    controller: KeyboardController,
    colors: KeyboardColors,
    accent: Color,
    keyHeight: Dp,
) {
    val shift = controller.shift
    val label = when (key.kind) {
        KeyKind.CHAR -> if (shift.upperCase) key.label.uppercase() else key.label
        KeyKind.ENTER -> controller.enterAction.label ?: ""
        KeyKind.SPACE -> controller.spaceLabel
        else -> key.label
    }
    val description = when (key.kind) {
        KeyKind.SHIFT -> when (shift) {
            ShiftState.OFF -> "shift"
            ShiftState.ONCE -> "shift, on"
            ShiftState.LOCKED -> "caps lock"
        }
        KeyKind.ENTER -> controller.enterAction.label ?: "enter"
        KeyKind.SPACE -> "space, ${controller.spaceLabel}"
        KeyKind.LANGUAGE -> "switch language, now ${controller.language.nativeName}"
        else -> label
    }
    val lit = key.kind == KeyKind.SHIFT && shift.upperCase
    PressableKey(
        modifier = Modifier.weight(key.units),
        height = keyHeight,
        base = if (key.isFunction) colors.functionKey else colors.letterKey,
        accent = accent,
        lit = lit,
        description = description,
        repeats = key.kind == KeyKind.BACKSPACE,
        onPress = { controller.onKey(key) },
        onRepeat = { controller.backspace() },
    ) { pressed ->
        val fg = if (pressed || lit) Color.White else if (key.kind == KeyKind.SPACE) colors.secondary else colors.text
        when (key.kind) {
            KeyKind.SHIFT -> KeyIcon(KeyGlyph.SHIFT, fg, filled = shift == ShiftState.LOCKED)
            KeyKind.BACKSPACE -> KeyIcon(KeyGlyph.BACKSPACE, fg)
            KeyKind.EMOJI -> KeyIcon(KeyGlyph.EMOJI, fg)
            KeyKind.LANGUAGE -> KeyIcon(KeyGlyph.GLOBE, fg)
            KeyKind.ENTER ->
                if (controller.enterAction == EnterAction.NEW_LINE) {
                    KeyIcon(KeyGlyph.ENTER, fg)
                } else {
                    KeyLabel(label, fg, 15, FontWeight.SemiBold)
                }
            KeyKind.CHAR, KeyKind.SYMBOL -> KeyLabel(label, fg, 22, FontWeight.Normal)
            KeyKind.SPACE -> KeyLabel(label, fg, 13, FontWeight.Normal)
            KeyKind.LAYER, KeyKind.PAGE -> KeyLabel(label, fg, 15, FontWeight.SemiBold)
        }
    }
}

/**
 * One square key. Acts on finger down (no wait for the lift, so quick
 * two-thumb typing lands in order), fills with the accent while held and for
 * at least [PRESS_FLASH_MS], ticks the keyboard haptic, and — for backspace —
 * repeats while held.
 */
@Composable
private fun PressableKey(
    modifier: Modifier,
    height: Dp,
    base: Color,
    accent: Color,
    lit: Boolean,
    description: String,
    repeats: Boolean,
    onPress: () -> Unit,
    onRepeat: () -> Unit,
    content: @Composable (pressed: Boolean) -> Unit,
) {
    val view = LocalView.current
    val haptics = LocalKeyHaptics.current
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    var flashing by remember { mutableStateOf(false) }
    val press by rememberUpdatedState(onPress)
    val repeat by rememberUpdatedState(onRepeat)
    val pressed = held || flashing
    Box(
        modifier = modifier
            .height(height)
            .background(if (pressed || lit) accent else base)
            .pointerInput(repeats, haptics) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    held = true
                    flashing = true
                    scope.launch {
                        delay(PRESS_FLASH_MS)
                        flashing = false
                    }
                    if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    press()
                    var repeater: Job? = null
                    if (repeats) {
                        repeater = scope.launch {
                            delay(REPEAT_START_MS)
                            while (true) {
                                repeat()
                                delay(REPEAT_EVERY_MS)
                            }
                        }
                    }
                    waitForUpOrCancellation()
                    repeater?.cancel()
                    held = false
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { press(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(pressed)
    }
}

@Composable
private fun KeyLabel(text: String, color: Color, sizeSp: Int, weight: FontWeight) {
    BasicText(
        text = text,
        maxLines = 1,
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontWeight = weight,
            textAlign = TextAlign.Center,
        ),
    )
}

/** The canvas's emoji grid, with abcd and backspace along the bottom. */
@Composable
private fun EmojiPanel(controller: KeyboardController, colors: KeyboardColors, accent: Color, keyHeight: Dp) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            for (row in KeyboardLayouts.emoji.chunked(8)) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (emoji in row) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable { controller.onEmoji(emoji) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(emoji, style = TextStyle(fontSize = 24.sp))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            PressableKey(
                modifier = Modifier.weight(1.5f),
                height = keyHeight,
                base = colors.functionKey,
                accent = accent,
                lit = false,
                description = "letters",
                repeats = false,
                onPress = { controller.backToLetters() },
                onRepeat = {},
            ) { pressed ->
                KeyLabel("abcd", if (pressed) Color.White else colors.text, 15, FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(7f))
            PressableKey(
                modifier = Modifier.weight(1.5f),
                height = keyHeight,
                base = colors.functionKey,
                accent = accent,
                lit = false,
                description = "backspace",
                repeats = true,
                onPress = { controller.backspace() },
                onRepeat = { controller.backspace() },
            ) { pressed ->
                KeyIcon(KeyGlyph.BACKSPACE, if (pressed) Color.White else colors.text)
            }
        }
    }
}

internal enum class KeyGlyph { SHIFT, BACKSPACE, ENTER, EMOJI, SETTINGS, LOCK, GLOBE }

/**
 * Monoline key glyphs, drawn from the canvas's 24-unit SVG paths (stroke 1.6).
 * [background] fills the settings sliders' knobs so the line breaks behind them.
 */
@Composable
private fun KeyIcon(
    glyph: KeyGlyph,
    color: Color,
    filled: Boolean = false,
    background: Color = Color.Transparent,
    iconSize: Dp = if (glyph == KeyGlyph.SETTINGS) 22.dp else 24.dp,
) {
    Canvas(Modifier.size(iconSize)) {
        val u = size.width / 24f
        val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Float): Path = Path().apply {
            moveTo(pts[0] * u, pts[1] * u)
            var i = 2
            while (i < pts.size) {
                lineTo(pts[i] * u, pts[i + 1] * u)
                i += 2
            }
        }
        when (glyph) {
            KeyGlyph.SHIFT -> {
                val arrow = p(12f, 3.5f, 3.5f, 12f, 8.5f, 12f, 8.5f, 20f, 15.5f, 20f, 15.5f, 12f, 20.5f, 12f)
                    .apply { close() }
                if (filled) drawPath(arrow, color, style = Fill)
                drawPath(arrow, color, style = stroke)
            }
            KeyGlyph.BACKSPACE -> {
                drawPath(p(9f, 5f, 21f, 5f, 21f, 19f, 9f, 19f, 2.5f, 12f).apply { close() }, color, style = stroke)
                drawPath(p(12.5f, 9f, 17.5f, 15f), color, style = stroke)
                drawPath(p(17.5f, 9f, 12.5f, 15f), color, style = stroke)
            }
            KeyGlyph.ENTER -> {
                drawPath(p(19.5f, 5f, 19.5f, 13.5f, 5f, 13.5f), color, style = stroke)
                drawPath(p(9f, 9.5f, 5f, 13.5f, 9f, 17.5f), color, style = stroke)
            }
            KeyGlyph.EMOJI -> {
                drawCircle(color, radius = 8.5f * u, center = Offset(12f * u, 12f * u), style = stroke)
                val mouth = Path().apply {
                    moveTo(8.3f * u, 14f * u)
                    quadraticTo(12f * u, 17.8f * u, 15.7f * u, 14f * u)
                }
                drawPath(mouth, color, style = stroke)
                drawCircle(color, radius = 0.9f * u, center = Offset(9.2f * u, 10f * u))
                drawCircle(color, radius = 0.9f * u, center = Offset(14.8f * u, 10f * u))
            }
            KeyGlyph.GLOBE -> {
                val c = Offset(12f * u, 12f * u)
                drawCircle(color, radius = 8.5f * u, center = c, style = stroke)
                drawOval(
                    color,
                    topLeft = Offset(8.2f * u, 3.5f * u),
                    size = Size(7.6f * u, 17f * u),
                    style = stroke,
                )
                drawLine(color, Offset(3.5f * u, 12f * u), Offset(20.5f * u, 12f * u), strokeWidth = stroke.width)
                drawLine(color, Offset(5f * u, 7.5f * u), Offset(19f * u, 7.5f * u), strokeWidth = stroke.width)
                drawLine(color, Offset(5f * u, 16.5f * u), Offset(19f * u, 16.5f * u), strokeWidth = stroke.width)
            }
            KeyGlyph.LOCK -> {
                val lockStroke = Stroke(width = 1.8f * u, join = StrokeJoin.Round)
                drawPath(p(5f, 10.5f, 19f, 10.5f, 19f, 20.5f, 5f, 20.5f).apply { close() }, color, style = lockStroke)
                val shackle = Path().apply {
                    moveTo(8f * u, 10.5f * u)
                    lineTo(8f * u, 7.5f * u)
                    arcTo(androidx.compose.ui.geometry.Rect(8f * u, 3.5f * u, 16f * u, 11.5f * u), 180f, 180f, false)
                    lineTo(16f * u, 10.5f * u)
                }
                drawPath(shackle, color, style = lockStroke)
            }
            KeyGlyph.SETTINGS -> {
                drawSlider(7f, 9f, u, color, background, stroke)
                drawSlider(12f, 15.5f, u, color, background, stroke)
                drawSlider(17f, 7f, u, color, background, stroke)
            }
        }
    }
}

private fun DrawScope.drawSlider(y: Float, knobX: Float, u: Float, color: Color, background: Color, stroke: Stroke) {
    drawLine(color, Offset(3.5f * u, y * u), Offset(20.5f * u, y * u), strokeWidth = stroke.width)
    drawCircle(background, radius = 2.2f * u, center = Offset(knobX * u, y * u))
    drawCircle(color, radius = 2.2f * u, center = Offset(knobX * u, y * u), style = stroke)
}
