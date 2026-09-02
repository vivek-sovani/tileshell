package com.tileshell.feature.livetiles

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The in-app twin of the home-screen widget's [com.tileshell.feature.livetiles
 * .widget.batteryGaugeBitmap] — same colour-coded fill (green/amber/red by
 * level, a fixed blue whenever [isCharging]), reimplemented on Compose's
 * `DrawScope` since this renders live in the app's own Compose tree. Keep
 * the two in visual lockstep: a fix/tweak to one should get mirrored here.
 */
@Composable
fun BatteryGaugeVisual(percent: Int, tint: Color, isCharging: Boolean, modifier: Modifier = Modifier) {
    val levelColor = when {
        isCharging -> Color(0xFF42A5F5)
        percent < 20 -> Color(0xFFE53935)
        percent < 70 -> Color(0xFFFFB300)
        else -> Color(0xFF43A047)
    }
    Canvas(modifier = modifier) {
        val nubWidth = size.width * 0.08f
        val bodyWidth = size.width - nubWidth
        val strokeWidth = size.height / 16f
        val corner = size.height / 6f
        val bodyInset = strokeWidth / 2f
        val bodyRect = Rect(bodyInset, bodyInset, bodyWidth - bodyInset, size.height - bodyInset)

        drawRoundRect(
            color = tint.copy(alpha = tint.alpha * 0.18f),
            topLeft = bodyRect.topLeft,
            size = bodyRect.size,
            cornerRadius = CornerRadius(corner, corner),
        )
        val clampedPercent = percent.coerceIn(0, 100)
        if (clampedPercent > 0) {
            val fillMargin = strokeWidth * 1.3f
            val fillMaxWidth = bodyRect.width - fillMargin * 2f
            val fillWidth = fillMaxWidth * (clampedPercent / 100f)
            val fillCorner = (corner - fillMargin).coerceAtLeast(0f)
            drawRoundRect(
                color = levelColor,
                topLeft = Offset(bodyRect.left + fillMargin, bodyRect.top + fillMargin),
                size = androidx.compose.ui.geometry.Size(fillWidth, bodyRect.height - fillMargin * 2f),
                cornerRadius = CornerRadius(fillCorner, fillCorner),
            )
        }
        drawRoundRect(
            color = tint,
            topLeft = bodyRect.topLeft,
            size = bodyRect.size,
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = strokeWidth),
        )

        val nubHeight = size.height * 0.4f
        val nubTop = (size.height - nubHeight) / 2f
        val nubPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = bodyWidth - bodyInset,
                    top = nubTop,
                    right = size.width - bodyInset,
                    bottom = nubTop + nubHeight,
                    cornerRadius = CornerRadius(corner / 2f, corner / 2f),
                ),
            )
        }
        drawPath(nubPath, color = levelColor)
    }
}
