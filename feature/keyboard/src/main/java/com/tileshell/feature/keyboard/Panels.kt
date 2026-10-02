package com.tileshell.feature.keyboard

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The accent bar under the active tool or tab (3dp, as the best guess's). */
private fun Modifier.underline(on: Boolean, accent: Color): Modifier =
    if (!on) this else drawBehind {
        val h = 3.dp.toPx()
        drawRect(accent, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
    }

// ---- strip pieces ----

/**
 * The tools row (canvas "No suggestions — tools"): menu, clipboard, one-handed,
 * settings; the active one white with the accent bar, the rest grey.
 */
@Composable
internal fun ToolsRow(controller: KeyboardController, colors: KeyboardColors, accent: Color, oneHand: Boolean) {
    val clipboardOpen = controller.layer == KeyboardLayer.CLIPBOARD
    val voiceOpen = controller.layer == KeyboardLayer.VOICE
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        Tool(PanelIcon.MENU, "close tools", controller.toolsOpen && !clipboardOpen && !voiceOpen, colors, accent) { controller.toggleTools() }
        Tool(PanelIcon.CLIPBOARD, "clipboard", clipboardOpen, colors, accent) { controller.toggleClipboard() }
        if (BuildConfig.VOICE) {
            Tool(PanelIcon.MIC, "voice typing", voiceOpen, colors, accent) { controller.toggleVoice() }
        }
        if (controller.language.indic) {
            // Devanagari keys ↔ English letters for मराठी / हिन्दी.
            TextTool(if (controller.devanagariKeys) "abc" else "अ", if (controller.devanagariKeys) "type in English letters" else "Devanagari keys", colors) {
                controller.toggleInputStyle()
            }
        }
        Tool(PanelIcon.ONE_HANDED, "one-handed mode", oneHand, colors, accent) { controller.toggleOneHand() }
        SettingsButton(colors)
    }
}

@Composable
private fun Tool(icon: PanelIcon, description: String, active: Boolean, colors: KeyboardColors, accent: Color, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 44.dp)
            .underline(active, accent)
            .clickable(onClick = onTap)
            .semantics {
                contentDescription = description
                role = Role.Button
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        PanelIconView(icon, if (active) colors.text else colors.secondary, background = colors.background)
    }
}

@Composable
private fun TextTool(label: String, description: String, colors: KeyboardColors, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 44.dp)
            .clickable(onClick = onTap)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = colors.secondary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** The menu tool at the start of the strip, opening the tools row. */
@Composable
internal fun MenuTool(controller: KeyboardController, colors: KeyboardColors, accent: Color) {
    Tool(PanelIcon.MENU, "tools", false, colors, accent) { controller.toggleTools() }
}

@Composable
internal fun SettingsButton(colors: KeyboardColors) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 44.dp)
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(context, KeyboardSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            .semantics { contentDescription = "keyboard settings" },
        contentAlignment = Alignment.Center,
    ) {
        PanelIconView(PanelIcon.SETTINGS, colors.secondary, background = colors.background)
    }
}

/** A copy made a moment ago: one tap pastes it (canvas "Fresh copy"). */
@Composable
internal fun FreshClipChip(text: String, colors: KeyboardColors, onPaste: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .height(34.dp)
            .border(2.dp, colors.letterKey)
            .clickable(onClick = onPaste)
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = "paste $text" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelIconView(PanelIcon.CLIPBOARD, colors.text, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        BasicText(
            text.replace('\n', ' '),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = colors.text, fontSize = 15.sp),
        )
    }
}

/** The emoji panel's strip: a search box (tap to search by name). */
@Composable
internal fun EmojiSearchBox(controller: KeyboardController, colors: KeyboardColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(34.dp)
            .border(2.dp, colors.letterKey)
            .clickable { controller.startEmojiSearch() }
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = "search emoji" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelIconView(PanelIcon.SEARCH, colors.secondary, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        BasicText("search emoji", style = TextStyle(color = colors.secondary, fontSize = 15.sp))
    }
}

/** While searching: close, the query being typed, then the matching emoji to tap. */
@Composable
internal fun EmojiSearchStrip(controller: KeyboardController, colors: KeyboardColors, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp, 44.dp)
                .clickable { controller.endEmojiSearch() }
                .semantics { contentDescription = "close emoji search" },
            contentAlignment = Alignment.Center,
        ) { PanelIconView(PanelIcon.CLOSE, colors.secondary, size = 18.dp) }
        Row(
            Modifier
                .widthIn(min = 96.dp, max = 140.dp)
                .height(34.dp)
                .border(2.dp, accent)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val q = controller.emojiQuery
            BasicText(
                if (q.isEmpty()) "search emoji" else q,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = if (q.isEmpty()) colors.secondary else colors.text, fontSize = 15.sp),
            )
            Box(Modifier.padding(start = 1.dp).width(2.dp).height(18.dp).background(accent))
        }
        LazyRow(Modifier.weight(1f).padding(start = 4.dp)) {
            items(controller.emojiResults) { e ->
                Box(
                    Modifier.size(40.dp, 44.dp).clickable { controller.onEmoji(e) },
                    contentAlignment = Alignment.Center,
                ) { BasicText(e, style = TextStyle(fontSize = 24.sp)) }
            }
        }
    }
}

// ---- panels in place of the keys ----

/**
 * The emoji panel (canvas "Emoji panel"): an 8-wide grid, then abcd, the tabs
 * (recent, smileys, nature, food, travel, symbols — the open one white with
 * the accent bar) and backspace.
 */
@Composable
internal fun EmojiPanel(controller: KeyboardController, colors: KeyboardColors, accent: Color, keyHeight: Dp) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val tab = controller.emojiTab
        val list = controller.emojiFor(tab)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list.isEmpty()) {
                BasicText(
                    if (tab == EmojiTab.RECENT) "emoji you use show up here" else "",
                    modifier = Modifier.align(Alignment.Center),
                    style = TextStyle(color = colors.secondary, fontSize = 15.sp),
                )
            } else {
                LazyVerticalGrid(GridCells.Fixed(8), Modifier.fillMaxSize()) {
                    items(list) { e ->
                        Box(
                            Modifier
                                .height(42.dp)
                                .clickable { controller.onEmoji(e) }
                                .semantics { contentDescription = e },
                            contentAlignment = Alignment.Center,
                        ) { BasicText(e, style = TextStyle(fontSize = 26.sp)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PressableKey(
                modifier = Modifier.weight(1.5f),
                height = 46.dp,
                base = colors.functionKey,
                accent = accent,
                lit = false,
                description = "letters",
                repeats = false,
                onPress = { controller.backToLetters() },
                onRepeat = {},
                onTouch = { controller.haptic(HapticKind.KEY_TAP) },
            ) { pressed -> KeyLabel("abcd", if (pressed) Color.White else colors.text, 15, FontWeight.SemiBold) }
            for (t in EmojiTab.entries) {
                val on = t == tab
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(colors.functionKey)
                        .underline(on, accent)
                        .clickable { controller.selectEmojiTab(t) }
                        .semantics {
                            contentDescription = t.label
                            selected = on
                        },
                    contentAlignment = Alignment.Center,
                ) { PanelIconView(t.icon, if (on) colors.text else colors.secondary) }
            }
            PressableKey(
                modifier = Modifier.weight(1.5f),
                height = 46.dp,
                base = colors.functionKey,
                accent = accent,
                lit = false,
                description = "backspace",
                repeats = true,
                onPress = { controller.backspace() },
                onRepeat = { controller.backspace() },
                onTouch = { controller.haptic(HapticKind.KEY_TAP) },
            ) { pressed -> KeyIcon(KeyGlyph.BACKSPACE, if (pressed) Color.White else colors.text) }
        }
    }
}

/**
 * The clipboard panel (canvas "Clipboard"): clips of the last hour two to a
 * row; tap to paste, hold to pin or delete; "clear" removes all but pinned.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ClipboardPanel(controller: KeyboardController, colors: KeyboardColors, accent: Color) {
    var holding by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "CLIPBOARD",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
            )
            if (controller.clips.any { !it.pinned }) {
                BasicText(
                    "clear",
                    modifier = Modifier.clickable { controller.clearClips() }.padding(horizontal = 6.dp, vertical = 4.dp),
                    style = TextStyle(color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
        if (controller.clips.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BasicText("nothing copied in the last hour", style = TextStyle(color = colors.secondary, fontSize = 15.sp))
            }
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(2),
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(controller.clips, key = { it.text }) { clip ->
                    Box(
                        Modifier
                            .height(68.dp)
                            .background(colors.functionKey)
                            .combinedClickable(
                                onClick = {
                                    if (holding == clip.text) holding = null else controller.pasteClip(clip.text)
                                },
                                onLongClick = {
                                    controller.haptic(HapticKind.LONG_PRESS)
                                    holding = clip.text
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .semantics { contentDescription = (if (clip.pinned) "pinned: " else "") + clip.text },
                    ) {
                        if (holding == clip.text) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                ClipAction(if (clip.pinned) "unpin" else "pin", accent) {
                                    controller.togglePin(clip)
                                    holding = null
                                }
                                ClipAction("delete", accent) {
                                    controller.deleteClip(clip)
                                    holding = null
                                }
                            }
                        } else {
                            Row {
                                if (clip.pinned) {
                                    PanelIconView(PanelIcon.PIN, colors.secondary, size = 14.dp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                BasicText(
                                    clip.text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(color = colors.text, fontSize = 14.sp, lineHeight = 19.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
        BasicText(
            "Clips are kept for one hour unless pinned. Hold a clip to pin or delete it.",
            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
        )
    }
}

@Composable
private fun ClipAction(label: String, accent: Color, onTap: () -> Unit) {
    BasicText(
        label,
        modifier = Modifier.clickable(onClick = onTap).padding(horizontal = 8.dp, vertical = 6.dp),
        style = TextStyle(color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

/**
 * One-handed mode's side panel (canvas "One-handed mode"): move the keys to the
 * other side, or back to full size.
 */
@Composable
internal fun OneHandPanel(keysOnRight: Boolean, controller: KeyboardController, colors: KeyboardColors) {
    Column(
        Modifier.width(66.dp).fillMaxHeight().background(colors.panel),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clickable { controller.switchOneHandSide() }
                .semantics { contentDescription = if (keysOnRight) "move keyboard to the left" else "move keyboard to the right" },
            contentAlignment = Alignment.Center,
        ) { PanelIconView(if (keysOnRight) PanelIcon.CHEVRON_LEFT else PanelIcon.CHEVRON_RIGHT, colors.text, size = 24.dp) }
        Box(
            Modifier
                .size(48.dp)
                .clickable { controller.fullSize() }
                .semantics { contentDescription = "full-size keyboard" },
            contentAlignment = Alignment.Center,
        ) { PanelIconView(PanelIcon.FULL_SIZE, colors.text) }
    }
}

/**
 * The voice panel (canvas "Voice typing"): the big accent mic (tap to stop, or
 * to listen again), "listening…" with what's heard so far, the language, and
 * abcd back to the keys.
 */
@Composable
internal fun VoicePanel(controller: KeyboardController, colors: KeyboardColors, accent: Color) {
    val voice = controller.voice
    val state = voice.state
    val listening = state == VoiceState.Listening
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .background(if (listening) accent else colors.functionKey, androidx.compose.foundation.shape.CircleShape)
                    .clickable { controller.voiceTap() }
                    .semantics { contentDescription = if (listening) "stop listening" else "start listening" },
                contentAlignment = Alignment.Center,
            ) { PanelIconView(PanelIcon.MIC, if (listening) Color.White else colors.text, size = 36.dp) }
            val headline = when {
                listening && voice.partial.isNotEmpty() -> voice.partial
                listening -> "listening…"
                state is VoiceState.Problem -> state.message
                else -> "tap to speak"
            }
            BasicText(
                headline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = colors.text,
                    fontSize = if (state is VoiceState.Problem) 17.sp else 26.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
            )
            BasicText(
                VoiceTyping.labelFor(controller.language) + if (listening) " · tap to stop" else "",
                style = TextStyle(color = colors.secondary, fontSize = 13.sp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 4.dp)
                .size(64.dp, 44.dp)
                .background(colors.functionKey)
                .clickable { controller.closeVoice() }
                .semantics { contentDescription = "letters" },
            contentAlignment = Alignment.Center,
        ) { KeyLabel("abcd", colors.text, 15, FontWeight.SemiBold) }
    }
}
