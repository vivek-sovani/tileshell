package com.tileshell.feature.livetiles.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * A richer, multi-element weather illustration — user-reported: a One UI
 * screenshot showed a proper sun/cloud/rain graphic, and our single monoline
 * glyph ([ic_widget_weather]) read as flat by comparison. Same pure-Canvas
 * technique as [accentGradientBitmap]/[moonPhaseBitmap]/[batteryGaugeBitmap]:
 * no window/Activity/Compose, safe from a background Worker.
 *
 * Deliberately not a literal One UI copy (that's Samsung's own asset) — a
 * from-scratch illustration in the same *spirit*: a filled sun disc with
 * rays, a soft two-lobe cloud, and condition-specific accents (rain streaks /
 * snowflakes / a lightning bolt / fog bands), picked by [weatherCodeToCondition]
 * 's own condition phrase so it stays in sync with the text already shown.
 * Two-tone: [onAccent] for the cloud/rays/rain (matching every other widget
 * face's contrast rule), plus a fixed warm amber for the sun itself — the one
 * deliberate departure from "everything tints to onAccent" elsewhere in this
 * batch, because a grey/white sun reads as "not a sun" the way a tinted cloud
 * still reads as a cloud.
 */
fun weatherConditionBitmap(condition: String, onAccent: Int, sizePx: Int = 160): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    val sunColor = Color.rgb(255, 179, 71)
    val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = onAccent; style = Paint.Style.FILL }
    val cloudShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(onAccent, 0.55f)
        style = Paint.Style.FILL
    }
    val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sunColor; style = Paint.Style.FILL }
    val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sunColor
        style = Paint.Style.STROKE
        strokeWidth = sizePx / 28f
        strokeCap = Paint.Cap.ROUND
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onAccent
        style = Paint.Style.STROKE
        strokeWidth = sizePx / 26f
        strokeCap = Paint.Cap.ROUND
    }

    fun drawSun(radius: Float, sunCx: Float, sunCy: Float, withRays: Boolean) {
        canvas.drawCircle(sunCx, sunCy, radius, sunPaint)
        if (withRays) {
            val rayLen = radius * 0.55f
            val rayGap = radius * 1.25f
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45).toDouble())
                val sx = sunCx + (Math.cos(angle) * rayGap).toFloat()
                val sy = sunCy + (Math.sin(angle) * rayGap).toFloat()
                val ex = sunCx + (Math.cos(angle) * (rayGap + rayLen)).toFloat()
                val ey = sunCy + (Math.sin(angle) * (rayGap + rayLen)).toFloat()
                canvas.drawLine(sx, sy, ex, ey, rayPaint)
            }
        }
    }

    fun cloudPath(width: Float, height: Float, offsetY: Float): Path {
        val w = width
        val h = height
        val rect = RectF(cx - w / 2f, cy - h / 2f + offsetY, cx + w / 2f, cy + h / 2f + offsetY)
        return Path().apply {
            addRoundRect(RectF(rect.left, rect.top + h * 0.35f, rect.right, rect.bottom), h * 0.32f, h * 0.32f, Path.Direction.CW)
            addCircle(rect.left + w * 0.32f, rect.top + h * 0.35f, h * 0.4f, Path.Direction.CW)
            addCircle(rect.left + w * 0.62f, rect.top + h * 0.22f, h * 0.5f, Path.Direction.CW)
        }
    }

    when {
        condition.contains("clear") || condition.contains("mostly clear") ->
            drawSun(sizePx * 0.28f, cx, cy, withRays = true)

        condition.contains("thunderstorm") -> {
            canvas.drawPath(cloudPath(sizePx * 0.78f, sizePx * 0.42f, -sizePx * 0.08f), cloudShadowPaint)
            val bolt = Path().apply {
                moveTo(cx + sizePx * 0.04f, cy + sizePx * 0.12f)
                lineTo(cx - sizePx * 0.08f, cy + sizePx * 0.32f)
                lineTo(cx, cy + sizePx * 0.32f)
                lineTo(cx - sizePx * 0.06f, cy + sizePx * 0.5f)
                lineTo(cx + sizePx * 0.12f, cy + sizePx * 0.26f)
                lineTo(cx + sizePx * 0.02f, cy + sizePx * 0.26f)
                close()
            }
            canvas.drawPath(bolt, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sunColor; style = Paint.Style.FILL })
        }

        condition.contains("snow") -> {
            canvas.drawPath(cloudPath(sizePx * 0.78f, sizePx * 0.42f, -sizePx * 0.1f), cloudPaint)
            val flakeY = cy + sizePx * 0.2f
            val xs = listOf(cx - sizePx * 0.22f, cx, cx + sizePx * 0.22f)
            xs.forEach { x ->
                for (angle in listOf(0.0, 60.0, 120.0)) {
                    val rad = Math.toRadians(angle)
                    val dx = (Math.cos(rad) * sizePx * 0.05f).toFloat()
                    val dy = (Math.sin(rad) * sizePx * 0.05f).toFloat()
                    canvas.drawLine(x - dx, flakeY - dy, x + dx, flakeY + dy, accentPaint)
                }
            }
        }

        condition.contains("rain") || condition.contains("drizzle") -> {
            canvas.drawPath(cloudPath(sizePx * 0.78f, sizePx * 0.42f, -sizePx * 0.1f), cloudPaint)
            val dropY0 = cy + sizePx * 0.14f
            val dropY1 = cy + sizePx * 0.32f
            listOf(-0.2f, 0f, 0.2f).forEach { dx ->
                canvas.drawLine(
                    cx + sizePx * dx,
                    dropY0,
                    cx + sizePx * dx - sizePx * 0.05f,
                    dropY1,
                    accentPaint,
                )
            }
        }

        condition.contains("fog") -> {
            val bandY = listOf(-0.12f, 0f, 0.12f, 0.24f)
            bandY.forEach { dy ->
                canvas.drawLine(
                    cx - sizePx * 0.32f,
                    cy + sizePx * dy,
                    cx + sizePx * 0.32f,
                    cy + sizePx * dy,
                    accentPaint,
                )
            }
        }

        condition.contains("overcast") ->
            canvas.drawPath(cloudPath(sizePx * 0.8f, sizePx * 0.46f, 0f), cloudPaint)

        else -> {
            // "partly cloudy" and any unmapped phrase: sun peeking from behind a cloud.
            drawSun(sizePx * 0.22f, cx - sizePx * 0.12f, cy - sizePx * 0.14f, withRays = false)
            canvas.drawPath(cloudPath(sizePx * 0.66f, sizePx * 0.38f, sizePx * 0.08f), cloudPaint)
        }
    }

    return bitmap
}

private fun withAlpha(color: Int, alpha: Float): Int {
    val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
    return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}
