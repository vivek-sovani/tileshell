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
 * Hosts a slide-up (or, with [dockTop], slide-down) sheet: scrim + panel. In
 * landscape the launcher splits into a feed (left) + Start (right) panel;
 * sheets launched from Start then dock to the right half — scrim included —
 * so the feed panel on the left stays visible and undimmed. In portrait (or
 * when [rightHalf] is false) the stage fills the whole screen as before.
 *
 * The [content] runs in a [BoxScope]: a scrim `Box(Modifier.fillMaxSize())`
 * plus a panel aligned to [Alignment.BottomCenter] (or [Alignment.TopCenter]
 * while [dockTop] is set — used by the Quick Panel, which docks to the top
 * like a real device's quick settings panel rather than sliding up from the
 * bottom like every other sheet) both resolve against the stage's bounds, so
 * they automatically shrink to the half in landscape.
 */
@Composable
fun SheetStage(
    rightHalf: Boolean,
    dockTop: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(if (dockTop) Alignment.TopEnd else Alignment.BottomEnd)
                .then(
                    if (rightHalf) Modifier.fillMaxWidth(0.5f).fillMaxHeight()
                    else Modifier.fillMaxSize(),
                ),
            content = content,
        )
    }
}
