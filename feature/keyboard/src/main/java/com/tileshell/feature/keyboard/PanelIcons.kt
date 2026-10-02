package com.tileshell.feature.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun rect(x: Float, y: Float, w: Float, h: Float) = "M$x $y h$w v$h h${-w} Z"
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

/**
 * The canvas's toolbar, emoji-tab and panel icons, as its own SVG paths on a
 * 24-unit grid (monoline, 1.6 stroke). [filled] parts are solid; [knobs] are
 * filled with the background so a line breaks behind them.
 */
enum class PanelIcon(
    val paths: List<String>,
    val stroke: Float = 1.6f,
    val filled: List<String> = emptyList(),
    val knobs: List<String> = emptyList(),
) {
    MENU(listOf(rect(4f, 4f, 6.5f, 6.5f), rect(13.5f, 4f, 6.5f, 6.5f), rect(4f, 13.5f, 6.5f, 6.5f), rect(13.5f, 13.5f, 6.5f, 6.5f))),
    CLIPBOARD(listOf(rect(5f, 4.5f, 14f, 16.5f), rect(9f, 2.5f, 6f, 4f))),
    ONE_HANDED(listOf(rect(2.5f, 6f, 19f, 12f), "M10 12 H18 M10 12 L13 9 M10 12 L13 15")),
    SETTINGS(
        listOf("M3.5 7 H20.5 M3.5 12 H20.5 M3.5 17 H20.5"),
        knobs = listOf(circle(9f, 7f, 2.2f), circle(15.5f, 12f, 2.2f), circle(7f, 17f, 2.2f)),
    ),
    RECENT(listOf(circle(12f, 12f, 8.5f), "M12 7.5 V12 L15 14")),
    SMILEYS(listOf(circle(12f, 12f, 8.5f), "M8.3 14 Q12 17.8 15.7 14")),
    NATURE(listOf("M5 19 C5 9 11 5 19 5 C19 13 15 19 5 19 Z M5 19 L13 11")),
    FOOD(listOf("M5 9 H16 V14 A4.5 4.5 0 0 1 11.5 18.5 H9.5 A4.5 4.5 0 0 1 5 14 Z M16 10.5 H18 A2 2 0 0 1 18 14.5 H16")),
    TRAVEL(listOf("M4 15 V11.5 L6.5 7 H17.5 L20 11.5 V15 Z M4 11.5 H20", circle(8f, 16.5f, 1.6f), circle(16f, 16.5f, 1.6f))),
    SYMBOLS(listOf("M12 19.5 C5 14.5 3.5 11.5 3.5 9 A4 4 0 0 1 12 7 A4 4 0 0 1 20.5 9 C20.5 11.5 19 14.5 12 19.5 Z")),
    SEARCH(listOf(circle(10.5f, 10.5f, 6f), "M15 15 L20 20")),
    CHEVRON_LEFT(listOf("M15 5 L8 12 L15 19"), stroke = 1.8f),
    CHEVRON_RIGHT(listOf("M9 5 L16 12 L9 19"), stroke = 1.8f),
    FULL_SIZE(listOf("M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15"), stroke = 1.8f),
    SPACE(listOf("M4.5 10 V15 H19.5 V10"), stroke = 1.8f),
    PIN(listOf("M9 4 H15 M10 4 V10 L7 13 H17 L14 10 V4 M12 13 V20"), stroke = 1.6f),
    CLOSE(listOf("M6 6 L18 18 M18 6 L6 18"), stroke = 1.8f),
    MIC(listOf("M9 6 A3 3 0 0 1 15 6 V11 A3 3 0 0 1 9 11 Z", "M5.5 11.5 A6.5 6.5 0 0 0 18.5 11.5 M12 18 V21.5")),
}

@Composable
internal fun PanelIconView(icon: PanelIcon, color: Color, size: Dp = 22.dp, background: Color = Color.Transparent) {
    val paths = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    val filled = remember(icon) { icon.filled.map { PathParser().parsePathString(it).toPath() } }
    val knobs = remember(icon) { icon.knobs.map { PathParser().parsePathString(it).toPath() } }
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        scale(u, u, pivot = Offset.Zero) {
            val stroke = Stroke(width = icon.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (p in paths) drawPath(p, color, style = stroke)
            for (p in filled) drawPath(p, color, style = Fill)
            for (p in knobs) {
                drawPath(p, background, style = Fill)
                drawPath(p, color, style = stroke)
            }
        }
    }
}
