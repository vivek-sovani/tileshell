package com.tileshell.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Hosts a slide-up sheet (scrim + panel). In landscape the launcher splits into a
 * feed (left) + Start (right) panel; sheets launched from Start then dock to the
 * right half — scrim included — so the feed panel on the left stays visible and
 * undimmed. In portrait (or when [rightHalf] is false) the stage fills the whole
 * screen as before.
 *
 * The [content] runs in a [BoxScope]: a scrim `Box(Modifier.fillMaxSize())` plus a
 * panel `Column(Modifier.align(Alignment.BottomCenter)…)` both resolve against the
 * stage's bounds, so they automatically shrink to the half in landscape.
 */
@Composable
fun SheetStage(
    rightHalf: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(
                    if (rightHalf) Modifier.fillMaxWidth(0.5f).fillMaxHeight()
                    else Modifier.fillMaxSize(),
                ),
            content = content,
        )
    }
}
