package com.tileshell.feature.livetiles.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.tileshell.feature.livetiles.BatterySample

/**
 * The widget's battery curve as a pushed bitmap (RemoteViews can't draw a
 * Composable) — the same drawing as the in-app `BatteryCurve`: level over time
 * to [nowMillis], screen-on stretches shaded in [bandColor].
 */
fun batteryCurveBitmap(
    samples: List<BatterySample>,
    nowMillis: Long,
    lineColor: Int,
    bandColor: Int,
    widthPx: Int = 480,
    heightPx: Int = 120,
): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    if (samples.size < 2) return bitmap
    val canvas = Canvas(bitmap)
    val t0 = samples.first().time
    val t1 = maxOf(nowMillis, samples.last().time + 1)
    val minLevel = (samples.minOf { it.level } - 10).coerceIn(0, 90)
    val pad = heightPx * 0.06f
    fun x(t: Long) = widthPx * (t - t0) / (t1 - t0).toFloat()
    fun y(level: Int) = pad + (heightPx - 2 * pad) * (1f - (level - minLevel) / (100f - minLevel))
    val band = Paint().apply { color = bandColor }
    samples.forEachIndexed { i, s ->
        if (!s.screenOn) return@forEachIndexed
        val end = samples.getOrNull(i + 1)?.time ?: t1
        canvas.drawRect(x(s.time), 0f, maxOf(x(end), x(s.time) + 1f), heightPx.toFloat(), band)
    }
    val path = Path()
    samples.forEachIndexed { i, s -> if (i == 0) path.moveTo(x(s.time), y(s.level)) else path.lineTo(x(s.time), y(s.level)) }
    path.lineTo(x(t1), y(samples.last().level))
    canvas.drawPath(
        path,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = lineColor
            strokeWidth = heightPx * 0.035f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        },
    )
    return bitmap
}
