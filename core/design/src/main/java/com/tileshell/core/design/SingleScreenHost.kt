package com.tileshell.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keeps a window-sized, phone-shaped screen (the first-run setup wizard, the
 * welcome card) from stretching across a wide or split window. Beside a
 * vertical hinge ([LocalHinge]) the content takes the right screen, the one
 * Start is on; below a horizontal hinge, the bottom screen; on a wide window
 * with no hinge (an unfolded Fold, a tablet) it is a centred column no wider
 * than [maxWidth]. [background] fills the rest, so nothing shows through or
 * can be tapped behind it. On a phone the content is drawn as it is, with no
 * wrapper.
 */
@Composable
fun SingleScreenHost(
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    maxWidth: Dp = 560.dp,
    content: @Composable () -> Unit,
) {
    val hinge = LocalHinge.current
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val sideBySide = hinge?.splitOf(vertical = true, extent = window.width.toFloat())
    val stacked = hinge?.splitOf(vertical = false, extent = window.height.toFloat())
    val wide = with(density) { window.width.toDp() } > maxWidth
    if (sideBySide == null && stacked == null && !wide) {
        content()
        return
    }
    Box(modifier = modifier.fillMaxSize().background(background)) {
        val panel = when {
            sideBySide != null -> Modifier
                .align(Alignment.CenterEnd)
                .width(with(density) { sideBySide.second.toDp() })
                .fillMaxHeight()
            stacked != null -> Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { stacked.second.toDp() })
            else -> Modifier
                .align(Alignment.Center)
                .widthIn(max = maxWidth)
                .fillMaxHeight()
        }
        Box(modifier = panel) {
            CompositionLocalProvider(LocalHinge provides null) { content() }
        }
    }
}
