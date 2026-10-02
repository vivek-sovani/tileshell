package com.tileshell.feature.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val KEY_GAP = 6.dp
private val ROW_GAP = 8.dp
private const val PRESS_FLASH_MS = 140L
private const val LONG_PRESS_MS = 350L
private const val REPEAT_START_MS = 400L
private const val REPEAT_EVERY_MS = 50L

/** Long-press bar: 36 × 48 cells, 2dp apart, inside 2dp padding and a 2dp border. */
private val POPUP_CELL_W = 36.dp
private val POPUP_CELL_H = 48.dp
private val POPUP_GAP = 2.dp
private val POPUP_INSET = 4.dp

/** A drag this far along the space bar starts moving the cursor; each step is one character. */
private val CURSOR_START = 12.dp
private val CURSOR_STEP = 10.dp

private class Touch(val keyId: String, val key: Key, val box: KeyBox, val down: Offset, val downAt: Long) {
    var mode = Mode.PRESS

    /** The key already acted (backspace, shift, or an earlier key committed by a later one). */
    var done = false
    var timer: Job? = null
    var lastX = down.x
    val path = ArrayList<Offset>()

    enum class Mode { PRESS, POPUP, CURSOR, SWIPE }
}

private data class Popup(val keyId: String, val options: List<String>, val left: Float, val top: Float, val selected: Int)

/**
 * The keys, with one touch handler for the whole area (each key no longer
 * handles its own touches), because three gestures cross keys: a long press
 * opens a bar of accents above the key and the finger slides along it; a drag
 * on the space bar moves the cursor; and a swipe from a letter across others
 * types a word. A plain tap types on lift (so a long press can still become an
 * accent); backspace and shift act on touch; touching a new key commits a key
 * still held, so fast two-thumb typing keeps its order.
 */
@Composable
internal fun KeyArea(
    controller: KeyboardController,
    colors: KeyboardColors,
    accent: Color,
    keyHeight: Dp,
    /** Letter size: 22 (spec), 20 in one-handed mode. */
    letterSp: Int = 22,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val rows = KeyboardLayouts.rowsFor(
        controller.layer,
        controller.languageKey,
        devanagariKeys = controller.devanagariKeys,
        shifted = controller.shift.upperCase,
    )
    val lit = remember { mutableStateMapOf<String, Boolean>() }
    var popup by remember { mutableStateOf<Popup?>(null) }
    var trail by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val ctl by rememberUpdatedState(controller)
    val currentRows by rememberUpdatedState(rows)
    val keyHeightPx = with(density) { keyHeight.toPx() }

    fun px(dp: Dp) = with(density) { dp.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val touches = HashMap<PointerId, Touch>()
                // Key bounds, recomputed whenever the rows or the area's width change.
                var boxesFor: Pair<List<KeyRow>, Int>? = null
                var boxes: List<List<KeyBox>> = emptyList()
                var decoder: SwipeDecoder? = null

                fun currentBoxes(): List<List<KeyBox>> {
                    val k = currentRows to size.width
                    if (boxesFor != k) {
                        boxes = KeyGeometry.layout(currentRows, size.width.toFloat(), keyHeightPx, px(ROW_GAP), px(KEY_GAP))
                        boxesFor = k
                        decoder = null
                    }
                    return boxes
                }

                fun hit(p: Offset): Triple<String, Key, KeyBox>? {
                    val b = currentBoxes()
                    val (r, i) = KeyGeometry.hit(b, p.x, p.y) ?: return null
                    val key = currentRows[r].keys[i]
                    return Triple(keyId(r, i, key), key, b[r][i])
                }

                fun swipeDecoder(): SwipeDecoder? {
                    val b = currentBoxes()
                    decoder?.let { return it }
                    val letters = currentRows.flatMapIndexed { r, row ->
                        row.keys.mapIndexedNotNull { i, k ->
                            if (k.kind == KeyKind.CHAR && k.label.length == 1 && k.label[0] in 'a'..'z') k to b[r][i] else null
                        }
                    }
                    val width = letters.firstOrNull()?.second?.width ?: return null
                    return SwipeDecoder(letters.associate { (k, box) -> k.label[0] to Pt(box.centerX, box.centerY) }, width)
                        .also { decoder = it }
                }

                fun light(id: String) {
                    lit[id] = true
                }

                fun unlight(t: Touch, scope: CoroutineScope) {
                    val held = System.currentTimeMillis() - t.downAt
                    scope.launch {
                        delay((PRESS_FLASH_MS - held).coerceAtLeast(0))
                        if (touches.values.none { it.keyId == t.keyId }) lit.remove(t.keyId)
                    }
                }

                fun popupIndex(p: Popup, x: Float): Int {
                    val cell = px(POPUP_CELL_W) + px(POPUP_GAP)
                    return ((x - p.left - px(POPUP_INSET)) / cell).toInt().coerceIn(0, p.options.lastIndex)
                }

                fun openPopup(t: Touch, options: List<String>) {
                    val r = t.box
                    val width = options.size * px(POPUP_CELL_W) + (options.size - 1) * px(POPUP_GAP) + 2 * px(POPUP_INSET)
                    val height = px(POPUP_CELL_H) + 2 * px(POPUP_INSET)
                    val left = (r.centerX - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
                    val p = Popup(t.keyId, options, left, r.top - height - px(4.dp), 0)
                    popup = p.copy(selected = popupIndex(p, r.centerX))
                    t.mode = Touch.Mode.POPUP
                    ctl.haptic(HapticKind.LONG_PRESS)
                }

                fun onDown(id: PointerId, pos: Offset) {
                    val (keyId, key, box) = hit(pos) ?: return
                    // A new key commits a letter still held, keeping fast typing in order.
                    for (other in touches.values) {
                        if (other.mode == Touch.Mode.PRESS && !other.done && other.key.kind in TYPED_ON_LIFT) {
                            other.timer?.cancel()
                            other.done = true
                            ctl.onKey(other.key)
                        }
                    }
                    val t = Touch(keyId, key, box, pos, System.currentTimeMillis())
                    touches[id] = t
                    light(keyId)
                    ctl.haptic(HapticKind.KEY_TAP)
                    when (key.kind) {
                        KeyKind.BACKSPACE -> {
                            ctl.onKey(key)
                            t.done = true
                            t.timer = scope.launch {
                                delay(REPEAT_START_MS)
                                while (true) {
                                    ctl.backspace()
                                    delay(REPEAT_EVERY_MS)
                                }
                            }
                        }
                        KeyKind.SHIFT -> {
                            ctl.onKey(key)
                            t.done = true
                        }
                        else -> {
                            val options = ctl.popupOptions(key)
                            if (options.isNotEmpty()) {
                                t.timer = scope.launch {
                                    delay(LONG_PRESS_MS)
                                    if (t.mode == Touch.Mode.PRESS && !t.done) openPopup(t, options)
                                }
                            }
                        }
                    }
                }

                fun onMove(t: Touch, pos: Offset) {
                    when (t.mode) {
                        Touch.Mode.PRESS -> {
                            if (t.done) return
                            if (t.key.kind == KeyKind.SPACE && abs(pos.x - t.down.x) > px(CURSOR_START)) {
                                t.timer?.cancel()
                                t.mode = Touch.Mode.CURSOR
                                t.lastX = pos.x
                                ctl.beginCursor()
                            } else if (t.key.kind == KeyKind.CHAR && ctl.swipeEnabled) {
                                swipeDecoder() ?: return
                                val w = t.box.width
                                if (SwipeDecoder.isSwipe(Pt(t.down.x, t.down.y), Pt(pos.x, pos.y), w)) {
                                    t.timer?.cancel()
                                    t.mode = Touch.Mode.SWIPE
                                    t.path += t.down
                                    t.path += pos
                                    trail = t.path.toList()
                                    lit.remove(t.keyId)
                                }
                            }
                        }
                        Touch.Mode.POPUP -> popup?.let { p -> popup = p.copy(selected = popupIndex(p, pos.x)) }
                        Touch.Mode.CURSOR -> {
                            val steps = ((pos.x - t.lastX) / px(CURSOR_STEP)).toInt()
                            if (steps != 0) {
                                ctl.moveCursor(steps)
                                t.lastX += steps * px(CURSOR_STEP)
                                ctl.haptic(HapticKind.TICK)
                            }
                        }
                        Touch.Mode.SWIPE -> {
                            val last = t.path.last()
                            if ((pos - last).getDistance() > 3f) {
                                t.path += pos
                                trail = t.path.toList()
                            }
                        }
                    }
                }

                fun onUp(t: Touch, cancelled: Boolean) {
                    t.timer?.cancel()
                    when (t.mode) {
                        Touch.Mode.PRESS -> if (!t.done && !cancelled) ctl.onKey(t.key)
                        Touch.Mode.POPUP -> {
                            val p = popup
                            popup = null
                            if (p != null && !cancelled) ctl.onPopupChoice(p.options[p.selected])
                        }
                        Touch.Mode.CURSOR -> ctl.endCursor()
                        Touch.Mode.SWIPE -> {
                            trail = emptyList()
                            val dec = swipeDecoder()
                            if (!cancelled && dec != null) ctl.onSwipe(t.path.map { Pt(it.x, it.y) }, dec)
                        }
                    }
                    unlight(t, scope)
                }

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            val t = touches[change.id]
                            when {
                                change.changedToDownIgnoreConsumed() -> onDown(change.id, change.position)
                                t != null && change.pressed -> onMove(t, change.position)
                                t != null -> {
                                    touches.remove(change.id)
                                    onUp(t, cancelled = false)
                                }
                            }
                            change.consume()
                        }
                    }
                }
            },
    ) {
        val unit = (maxWidth - KEY_GAP * 9) / 10
        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            rows.forEachIndexed { r, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (row.inset) (unit + KEY_GAP) / 2 else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
                ) {
                    row.keys.forEachIndexed { i, key ->
                        val id = keyId(r, i, key)
                        KeyFace(
                            key = key,
                            pressed = lit[id] == true,
                            controller = controller,
                            colors = colors,
                            accent = accent,
                            topRowDigit = if (r == 0 && controller.layer == KeyboardLayer.LETTERS) KeyPopups.topRowDigits[key.label] else null,
                            // Devanagari keys: 11 to a row, so a size smaller (the canvas's 19).
                            letterSp = if (controller.devanagariKeys) letterSp - 3 else letterSp,
                            modifier = Modifier.weight(key.units).height(keyHeight),
                        )
                    }
                }
            }
        }

        if (trail.size > 1) {
            Canvas(Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(trail[0].x, trail[0].y)
                    for (p in trail.drop(1)) lineTo(p.x, p.y)
                }
                drawPath(
                    path,
                    accent.copy(alpha = 0.85f),
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawCircle(Color.White, radius = 6.dp.toPx(), center = trail.last())
            }
        }

        popup?.let { p -> PopupBar(p, colors, accent) }
    }
}

private fun keyId(row: Int, index: Int, key: Key) = "$row:$index:${key.kind}:${key.label}"

/** Keys that type on lift (and so may be committed early by the next key). */
private val TYPED_ON_LIFT = setOf(KeyKind.CHAR, KeyKind.SYMBOL)

@Composable
private fun PopupBar(p: Popup, colors: KeyboardColors, accent: Color) {
    Row(
        modifier = Modifier
            .offset { IntOffset(p.left.roundToInt(), p.top.roundToInt()) }
            .shadow(10.dp)
            .background(colors.letterKey)
            .border(2.dp, colors.text)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(POPUP_GAP),
    ) {
        p.options.forEachIndexed { i, option ->
            val selected = i == p.selected
            Box(
                Modifier
                    .size(POPUP_CELL_W, POPUP_CELL_H)
                    .background(if (selected) accent else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(option, style = TextStyle(color = if (selected) Color.White else colors.text, fontSize = 22.sp))
            }
        }
    }
}

/**
 * One key as drawn: accent while pressed (or shift on), square, the spec's
 * type sizes; the top row's digit in the corner. While the space bar moves the
 * cursor every label blanks out and the space bar reads ‹ ›.
 */
@Composable
private fun KeyFace(
    key: Key,
    pressed: Boolean,
    controller: KeyboardController,
    colors: KeyboardColors,
    accent: Color,
    topRowDigit: String?,
    modifier: Modifier,
    letterSp: Int = 22,
) {
    val shift = controller.shift
    val cursor = controller.cursorMode
    val shiftLit = key.kind == KeyKind.SHIFT && shift.upperCase
    val hot = (pressed || shiftLit) && !cursor || (cursor && key.kind == KeyKind.SPACE)
    val bg = when {
        hot -> accent
        cursor || key.isFunction -> colors.functionKey
        else -> colors.letterKey
    }
    val fg = if (hot) Color.White else if (key.kind == KeyKind.SPACE) colors.secondary else colors.text
    val label = when (key.kind) {
        KeyKind.CHAR -> when {
            key.independent != null && controller.fullVowels -> key.independent
            shift.upperCase && !controller.devanagariKeys -> key.label.uppercase()
            else -> key.label
        }
        KeyKind.ENTER -> controller.enterAction.label ?: ""
        KeyKind.SPACE -> controller.spaceLabel
        else -> key.display ?: key.label
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
    Box(
        modifier = modifier
            .background(bg)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { controller.onKey(key); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (cursor) {
            if (key.kind == KeyKind.SPACE) KeyLabel("‹        ›", fg, 20, FontWeight.Normal)
            return@Box
        }
        when (key.kind) {
            KeyKind.SHIFT -> KeyIcon(KeyGlyph.SHIFT, fg, filled = shift == ShiftState.LOCKED)
            KeyKind.BACKSPACE -> KeyIcon(KeyGlyph.BACKSPACE, fg)
            KeyKind.EMOJI -> KeyIcon(KeyGlyph.EMOJI, fg)
            KeyKind.LANGUAGE -> KeyIcon(KeyGlyph.GLOBE, fg)
            KeyKind.ENTER -> when (controller.enterAction) {
                EnterAction.NEW_LINE -> KeyIcon(KeyGlyph.ENTER, fg)
                // "search" didn't fit the key: a magnifier, as other keyboards show.
                EnterAction.SEARCH -> PanelIconView(PanelIcon.SEARCH, fg, size = 24.dp)
                else -> KeyLabel(label, fg, 15, FontWeight.SemiBold)
            }
            KeyKind.CHAR, KeyKind.SYMBOL ->
                if (controller.layer == KeyboardLayer.NUMPAD) {
                    // The number pad: 24 digit, its phone letters under it.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KeyLabel(label, fg, 24, FontWeight.Normal)
                        key.sub?.let {
                            BasicText(
                                it,
                                modifier = Modifier.padding(top = 3.dp),
                                style = TextStyle(color = if (hot) Color.White else colors.secondary, fontSize = 10.sp, letterSpacing = 0.08.em),
                            )
                        }
                    }
                } else {
                    KeyLabel(label, fg, letterSp, FontWeight.Normal)
                }
            KeyKind.SPACE ->
                if (controller.layer == KeyboardLayer.NUMPAD) PanelIconView(PanelIcon.SPACE, fg, size = 24.dp)
                else KeyLabel(label, fg, 13, FontWeight.Normal)
            KeyKind.LAYER, KeyKind.PAGE -> KeyLabel(label, fg, 15, FontWeight.SemiBold)
        }
        if (topRowDigit != null) {
            BasicText(
                topRowDigit,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 4.dp),
                style = TextStyle(color = if (hot) Color.White else colors.secondary, fontSize = 10.sp),
            )
        }
    }
}
