package com.tileshell.feature.livetiles.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * A real battery gauge — outline + a fill bar proportional to [percent] —
 * user-requested: "big battery image on one side with storage indication",
 * not just a plain outline glyph with a text percentage beside it. Same
 * pure-Canvas technique as [accentGradientBitmap]/[moonPhaseBitmap]: no
 * window/Activity/Compose needed, safe from a background Worker.
 *
 * Shape mirrors `TileIcons["battery"]` (core/design/TileIcons.kt) — a
 * horizontal rounded-rect body with a small nub on the right — just drawn
 * bigger and filled instead of stroke-only.
 *
 * The fill is colour-coded (user-requested) — the standard traffic-light
 * convention (green/amber/red), or a fixed charging-blue whenever
 * [isCharging] is true regardless of level, matching how most OEM battery
 * indicators prioritise "plugged in" over the raw percentage. The *outline*
 * stays [onAccent] (still needs to read against the widget's own accent
 * background); only the fill — the "how much is left" signal — carries the
 * status colour.
 */
fun batteryGaugeBitmap(
    percent: Int,
    onAccent: Int,
    isCharging: Boolean = false,
    widthPx: Int = 220,
    heightPx: Int = 120,
): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val nubWidth = widthPx * 0.08f
    val bodyWidth = widthPx - nubWidth
    val strokeWidth = heightPx / 16f
    val corner = heightPx / 6f

    val levelColor = when {
        isCharging -> Color.rgb(66, 165, 245) // blue
        percent < 20 -> Color.rgb(229, 57, 53) // red
        percent < 70 -> Color.rgb(255, 179, 0) // amber
        else -> Color.rgb(67, 160, 71) // green
    }

    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onAccent
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = levelColor; style = Paint.Style.FILL }
    val dimFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((Color.alpha(onAccent) * 0.18f).toInt(), Color.red(onAccent), Color.green(onAccent), Color.blue(onAccent))
        style = Paint.Style.FILL
    }

    val bodyInset = strokeWidth / 2f
    val bodyRect = RectF(bodyInset, bodyInset, bodyWidth - bodyInset, heightPx - bodyInset)

    // Dim background fill for the "empty" portion, then the real fill on top.
    canvas.drawRoundRect(bodyRect, corner, corner, dimFillPaint)
    val clampedPercent = percent.coerceIn(0, 100)
    if (clampedPercent > 0) {
        val fillMargin = strokeWidth * 1.3f
        val fillMaxWidth = bodyRect.width() - fillMargin * 2f
        val fillRect = RectF(
            bodyRect.left + fillMargin,
            bodyRect.top + fillMargin,
            bodyRect.left + fillMargin + fillMaxWidth * (clampedPercent / 100f),
            bodyRect.bottom - fillMargin,
        )
        val fillCorner = (corner - fillMargin).coerceAtLeast(0f)
        canvas.drawRoundRect(fillRect, fillCorner, fillCorner, fillPaint)
    }
    canvas.drawRoundRect(bodyRect, corner, corner, outlinePaint)

    // The nub: a small rounded tab on the right edge, vertically centred.
    val nubHeight = heightPx * 0.4f
    val nubTop = (heightPx - nubHeight) / 2f
    val nubRect = RectF(bodyWidth - bodyInset, nubTop, widthPx - bodyInset, nubTop + nubHeight)
    val nubPath = Path().apply { addRoundRect(nubRect, corner / 2f, corner / 2f, Path.Direction.CW) }
    canvas.drawPath(nubPath, fillPaint)

    return bitmap
}
