package com.tileshell.feature.livetiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons

/** "on"/"off" — pure so the label wording is unit-testable without a Composable. */
fun flashlightStatusText(on: Boolean): String = if (on) "on" else "off"

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live flashlight tile: unlike every other widget in this package it has
 * no data to display and nothing to flip to — the whole tile *is* the
 * control, exactly like a Quick Panel toggle tile brought onto Start. Tapping
 * it (while [interactive]) calls [rememberTorchOn]'s toggle directly; there's
 * no separate management sheet to open; see [LiveFace.FLASHLIGHT] (flips =
 * false, matching sticky note's own "the front already is the whole thing"
 * reasoning).
 */
@Composable
fun FlashlightTileFace(size: TileSize, interactive: Boolean, modifier: Modifier = Modifier) {
    val (on, toggle) = rememberTorchOn()
    val narrow = size.narrowLive
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = interactive, onClick = toggle)
            .padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Icon(
            imageVector = TileIcons["flashlight"],
            contentDescription = null,
            tint = FaceText.copy(alpha = if (on) 1f else 0.6f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = flashlightStatusText(on),
            color = FaceText,
            fontSize = if (narrow) 20.sp else 26.sp,
            letterSpacing = (-0.5).sp,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = "flashlight",
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (narrow) 11.sp else 13.sp,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

/**
 * The compact 1×1 flashlight icon (ICONS home style / SMALL tile) — mirrors
 * [BatterySmallFace]'s shape, but stays a real toggle (see [FlashlightTileFace])
 * rather than a read-only display, since a flashlight tile's whole purpose is
 * the tap.
 */
@Composable
fun FlashlightSmallFace(interactive: Boolean, modifier: Modifier = Modifier) {
    val (on, toggle) = rememberTorchOn()
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = interactive, onClick = toggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TileIcons["flashlight"],
            contentDescription = null,
            tint = FaceText.copy(alpha = if (on) 1f else 0.6f),
        )
    }
}
