package com.tileshell.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Windows Phone 8's toggle (user-chosen): a thin outlined track whose inner bar
 * fills with the accent when on, and a tall solid thumb that stands out above
 * and below the track. The thumb can be dragged across (user-requested):
 * released past the middle it switches, otherwise it springs back. A tap on
 * the row still toggles, and the thumb slides either way.
 */
@Composable
fun LumiaSwitch(
    on: Boolean,
    accent: Color,
    tokens: ColorTokens,
    onChange: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    // Track 44dp wide; the 10dp thumb travels its full width, overlapping the
    // border at either end as WP's did. (Sized down from 52dp: read bulky.)
    val travelPx = with(density) { 34.dp.toPx() }
    val thumb = remember { Animatable(if (on) travelPx else 0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(on) { if (!dragging) thumb.animateTo(if (on) travelPx else 0f) }
    // Fill and outline follow the thumb, so a drag past the middle previews it.
    val lit = thumb.value > travelPx / 2f
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(22.dp)
            .pointerInput(on) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        val nowOn = thumb.value > travelPx / 2f
                        scope.launch { thumb.animateTo(if (nowOn) travelPx else 0f) }
                        if (nowOn != on) onChange(nowOn)
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { thumb.animateTo(if (on) travelPx else 0f) }
                    },
                ) { change, amount ->
                    change.consume()
                    scope.launch { thumb.snapTo((thumb.value + amount).coerceIn(0f, travelPx)) }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track: thin outline with an inner bar, filled up to the thumb when on.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .border(1.5.dp, if (lit) tokens.fg else tokens.fgDim)
                .padding(3.dp),
        ) {
            val fillWidth = with(density) { (thumb.value).toDp() }
            if (lit) Box(Modifier.width(fillWidth).fillMaxHeight().background(accent))
        }
        // Thumb: taller than the track.
        Box(
            modifier = Modifier
                .offset { IntOffset(thumb.value.toInt(), 0) }
                .width(10.dp)
                .fillMaxHeight()
                .background(tokens.fg),
        )
    }
}
