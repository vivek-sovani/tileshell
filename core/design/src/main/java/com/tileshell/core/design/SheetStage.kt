package com.tileshell.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

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
 *
 * On a window split at a hinge ([LocalHinge]; Surface Duo, a half-open Galaxy Z
 * Fold) nothing is drawn under the hinge. A stage that does not set
 * [spanHinge] keeps to one screen: the right one beside a vertical hinge (the
 * one Start is on), the bottom one below a horizontal hinge (the top one when
 * [dockTop]), and what it hosts sees no hinge. A panorama hub sets [spanHinge]
 * because it lays itself out around the hinge ([HubPanorama], [HubAppBar]).
 * The stage must be window-sized; it is, wherever it is used.
 */
@Composable
fun SheetStage(
    rightHalf: Boolean,
    dockTop: Boolean = false,
    spanHinge: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val hinge = LocalHinge.current
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val sideBySide = hinge?.splitOf(vertical = true, extent = window.width.toFloat())
    val stacked = hinge?.splitOf(vertical = false, extent = window.height.toFloat())
    Box(modifier = modifier.fillMaxSize()) {
        val panel: Modifier
        val keepsHinge: Boolean
        when {
            spanHinge || (sideBySide == null && stacked == null) -> {
                keepsHinge = spanHinge
                panel = Modifier
                    .align(if (dockTop) Alignment.TopEnd else Alignment.BottomEnd)
                    .then(
                        if (rightHalf && sideBySide == null && stacked == null) Modifier.fillMaxWidth(0.5f).fillMaxHeight()
                        else Modifier.fillMaxSize(),
                    )
            }
            sideBySide != null -> {
                keepsHinge = false
                panel = Modifier
                    .align(Alignment.BottomEnd)
                    .width(with(density) { sideBySide.second.toDp() })
                    .fillMaxHeight()
            }
            else -> {
                keepsHinge = false
                val screen = stacked!!
                panel = Modifier
                    .align(if (dockTop) Alignment.TopEnd else Alignment.BottomEnd)
                    .fillMaxWidth()
                    .height(with(density) { (if (dockTop) screen.first else screen.second).toDp() })
            }
        }
        Box(modifier = panel) {
            CompositionLocalProvider(LocalHinge provides if (keepsHinge) hinge else null) {
                content()
            }
        }
    }
}
