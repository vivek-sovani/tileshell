package com.tileshell.feature.livetiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.TileIcons

private val ARRANGE_ROW_HEIGHT = 56.dp

/**
 * "arrange tile": who the favourites tile shows and in what order. The top
 * list is on the tile (drag the grip to reorder, − to take someone off); a
 * dashed line marks how many the pinned tile has room for, the rest going
 * into "+ more". Below are the other starred contacts (+ adds them at the
 * end). Every change saves at once.
 */
@Composable
internal fun ArrangeFavouritesTile(
    stars: List<PersonSummary>,
    tileKeys: List<String>,
    tokens: ColorTokens,
    accent: Color,
    onChange: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    BackHandler { onDone() }
    val context = LocalContext.current
    val capacity by FavouritesTileCapacity.capacity(context).collectAsStateWithLifecycle()
    val byKey = remember(stars) { stars.associateBy { it.lookupKey } }
    // Edited locally while dragging; saved when the finger lifts.
    var keys by remember(tileKeys) { mutableStateOf(tileKeys.filter { it in byKey }) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ARRANGE_ROW_HEIGHT.toPx() }
    val others = stars.filter { it.lookupKey !in keys }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("arrange tile", color = tokens.fg, fontSize = 24.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clickable(onClickLabel = "done", onClick = onDone),
            ) {
                Icon(TileIcons["check"], contentDescription = "done", tint = tokens.fg, modifier = Modifier.size(22.dp))
            }
        }

        ArrangeHeader("on the tile · drag to reorder", tokens)
        if (keys.isEmpty()) {
            Text(
                "the tile is empty. add people from the list below",
                color = tokens.fgDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        keys.forEachIndexed { index, key ->
            val person = byKey[key] ?: return@forEachIndexed
            val cap = capacity
            if (cap != null && index == cap) CapacityLine(cap, accent)
            val dragging = draggingKey == key
            // Keyed by person, so a row (and the drag running in it) moves
            // with them when the list reorders mid-drag.
            key(key) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ARRANGE_ROW_HEIGHT)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .background(if (dragging) tokens.chip else Color.Transparent)
                        .alpha(if (cap != null && index >= cap && !dragging) 0.55f else 1f)
                        .semantics {
                            customActions = listOfNotNull(
                                if (index > 0) CustomAccessibilityAction("move up") {
                                    keys = moveItem(keys, index, index - 1); onChange(keys); true
                                } else null,
                                if (index < keys.lastIndex) CustomAccessibilityAction("move down") {
                                    keys = moveItem(keys, index, index + 1); onChange(keys); true
                                } else null,
                            )
                        },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            // Keyed on tileKeys too: a save makes a new `keys`
                            // state, which a still-running gesture wouldn't see.
                            .pointerInput(key, tileKeys) {
                                detectDragGestures(
                                    onDragStart = { draggingKey = key; dragOffset = 0f },
                                    onDragEnd = { draggingKey = null; dragOffset = 0f; onChange(keys) },
                                    onDragCancel = { draggingKey = null; dragOffset = 0f; onChange(keys) },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    // Swap with the neighbour once the row has moved
                                    // half a row past it.
                                    val from = keys.indexOf(key)
                                    val steps = (dragOffset / rowPx).let { if (it > 0) (it + 0.5f).toInt() else (it - 0.5f).toInt() }
                                    if (from >= 0 && steps != 0) {
                                        val to = (from + steps).coerceIn(0, keys.lastIndex)
                                        if (to != from) {
                                            keys = moveItem(keys, from, to)
                                            dragOffset -= (to - from) * rowPx
                                        }
                                    }
                                }
                            },
                    ) {
                        Icon(TileIcons["grip"], contentDescription = "drag to reorder", tint = tokens.fgDim, modifier = Modifier.size(20.dp))
                    }
                    ArrangePerson(person, tokens, Modifier.weight(1f))
                    ArrangeButton("minus", "remove ${person.name} from tile", tokens) {
                        keys = keys - key
                        onChange(keys)
                    }
                }
            }
        }

        if (others.isNotEmpty()) {
            ArrangeHeader("other favourites", tokens)
            others.forEach { person ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(ARRANGE_ROW_HEIGHT),
                ) {
                    Spacer(Modifier.width(44.dp))
                    ArrangePerson(person, tokens, Modifier.weight(1f))
                    ArrangeButton("plus", "add ${person.name} to tile", tokens) {
                        keys = keys + person.lookupKey
                        onChange(keys)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ArrangeHeader(text: String, tokens: ColorTokens) {
    Text(text, color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun ArrangePerson(person: PersonSummary, tokens: ColorTokens, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        ContactAvatar(person, size = 36.dp, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(person.name.lowercase(), color = tokens.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArrangeButton(icon: String, description: String, tokens: ColorTokens, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
    ) {
        Icon(TileIcons[icon], contentDescription = description, tint = tokens.fgDim, modifier = Modifier.size(20.dp))
    }
}

/** The dashed cut: people below it are on the tile's "+ more". */
@Composable
private fun CapacityLine(capacity: Int, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        DashedRule(accent, Modifier.weight(1f))
        Text(
            "your tile fits $capacity · the rest go in \"+ more\"",
            color = accent,
            fontSize = 11.sp,
            maxLines = 1,
        )
        DashedRule(accent, Modifier.weight(1f))
    }
}

@Composable
private fun DashedRule(color: Color, modifier: Modifier) {
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
    }
}
