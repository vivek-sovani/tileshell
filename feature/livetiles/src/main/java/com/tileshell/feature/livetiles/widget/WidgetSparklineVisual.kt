package com.tileshell.feature.livetiles.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * A plain-`Canvas` port of the in-app stock/commodity tile's `Sparkline`
 * Composable — RemoteViews can't host an arbitrary `Canvas`-drawing
 * Composable directly, only a pushed [Bitmap] (`setImageViewBitmap`), the
 * same reason [moonPhaseBitmap]/[batteryGaugeBitmap]/[weatherConditionBitmap]
 * exist. Line colour is the caller's choice (green/red by change sign,
 * matching the in-app tile's own `changeColor`), normalized min/max like the
 * Compose version — a flat/near-flat series still draws a visible mid-height
 * line rather than degenerating to a division by zero.
 */
fun sparklineBitmap(points: List<Double>, lineColor: Int, widthPx: Int = 320, heightPx: Int = 110): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    if (points.size < 2) return bitmap
    val canvas = Canvas(bitmap)

    val min = points.min()
    val max = points.max()
    val range = (max - min).let { if (it <= 0.0) 1.0 else it }
    val strokePad = heightPx * 0.1f
    val usableHeight = heightPx - strokePad * 2

    val path = Path()
    points.forEachIndexed { index, value ->
        val x = widthPx * index / (points.size - 1).toFloat()
        val normalized = ((value - min) / range).toFloat()
        val y = strokePad + usableHeight * (1f - normalized)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = lineColor
        strokeWidth = heightPx * 0.03f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(path, paint)
    return bitmap
}
