package com.tileshell.feature.livetiles

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** The fixed warm sun colour — the one deliberate departure from "everything tints to FaceText" in this app's live tiles. */
private val SunColor = Color(0xFFFFB347)

/**
 * The richer multi-element weather illustration (user-requested: "can we
 * show similar images" to a One UI screenshot) — the in-app twin of the
 * home-screen widget's [com.tileshell.feature.livetiles.widget
 * .weatherConditionBitmap], reimplemented on Compose's `DrawScope` instead of
 * `android.graphics.Canvas` since this renders live in the app's own Compose
 * tree (no RemoteViews bitmap needed here). Same construction, same per-
 * condition branches, same fixed-amber-sun exception to the tint rule —
 * keeping the two implementations in visual lockstep is the point, so a
 * fix/tweak to one should get mirrored to the other.
 */
@Composable
fun WeatherConditionVisual(condition: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val s = size.minDimension

        fun drawSun(radius: Float, sunCx: Float, sunCy: Float, withRays: Boolean) {
            drawCircle(color = SunColor, radius = radius, center = Offset(sunCx, sunCy))
            if (withRays) {
                val rayLen = radius * 0.55f
                val rayGap = radius * 1.25f
                val strokeW = s / 28f
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val sx = sunCx + (cos(angle) * rayGap).toFloat()
                    val sy = sunCy + (sin(angle) * rayGap).toFloat()
                    val ex = sunCx + (cos(angle) * (rayGap + rayLen)).toFloat()
                    val ey = sunCy + (sin(angle) * (rayGap + rayLen)).toFloat()
                    drawLine(SunColor, Offset(sx, sy), Offset(ex, ey), strokeWidth = strokeW, cap = StrokeCap.Round)
                }
            }
        }

        fun cloudPath(width: Float, height: Float, offsetY: Float): Path {
            val left = cx - width / 2f
            val top = cy - height / 2f + offsetY
            return Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = left,
                        top = top + height * 0.35f,
                        right = left + width,
                        bottom = top + height,
                        radiusX = height * 0.32f,
                        radiusY = height * 0.32f,
                    ),
                )
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = Offset(left + width * 0.32f, top + height * 0.35f),
                        radius = height * 0.4f,
                    ),
                )
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = Offset(left + width * 0.62f, top + height * 0.22f),
                        radius = height * 0.5f,
                    ),
                )
            }
        }

        val accentStroke = Stroke(width = s / 26f, cap = StrokeCap.Round)

        when {
            condition.contains("clear") || condition.contains("mostly clear") ->
                drawSun(s * 0.28f, cx, cy, withRays = true)

            condition.contains("thunderstorm") -> {
                drawPath(cloudPath(s * 0.78f, s * 0.42f, -s * 0.08f), tint.copy(alpha = tint.alpha * 0.55f))
                val bolt = Path().apply {
                    moveTo(cx + s * 0.04f, cy + s * 0.12f)
                    lineTo(cx - s * 0.08f, cy + s * 0.32f)
                    lineTo(cx, cy + s * 0.32f)
                    lineTo(cx - s * 0.06f, cy + s * 0.5f)
                    lineTo(cx + s * 0.12f, cy + s * 0.26f)
                    lineTo(cx + s * 0.02f, cy + s * 0.26f)
                    close()
                }
                drawPath(bolt, SunColor)
            }

            condition.contains("snow") -> {
                drawPath(cloudPath(s * 0.78f, s * 0.42f, -s * 0.1f), tint)
                val flakeY = cy + s * 0.2f
                listOf(cx - s * 0.22f, cx, cx + s * 0.22f).forEach { x ->
                    listOf(0.0, 60.0, 120.0).forEach { angleDeg ->
                        val rad = Math.toRadians(angleDeg)
                        val dx = (cos(rad) * s * 0.05f).toFloat()
                        val dy = (sin(rad) * s * 0.05f).toFloat()
                        drawLine(tint, Offset(x - dx, flakeY - dy), Offset(x + dx, flakeY + dy), strokeWidth = accentStroke.width, cap = StrokeCap.Round)
                    }
                }
            }

            condition.contains("rain") || condition.contains("drizzle") -> {
                drawPath(cloudPath(s * 0.78f, s * 0.42f, -s * 0.1f), tint)
                val dropY0 = cy + s * 0.14f
                val dropY1 = cy + s * 0.32f
                listOf(-0.2f, 0f, 0.2f).forEach { dx ->
                    drawLine(
                        tint,
                        Offset(cx + s * dx, dropY0),
                        Offset(cx + s * dx - s * 0.05f, dropY1),
                        strokeWidth = accentStroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }

            condition.contains("fog") -> {
                listOf(-0.12f, 0f, 0.12f, 0.24f).forEach { dy ->
                    drawLine(
                        tint,
                        Offset(cx - s * 0.32f, cy + s * dy),
                        Offset(cx + s * 0.32f, cy + s * dy),
                        strokeWidth = accentStroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }

            condition.contains("overcast") ->
                drawPath(cloudPath(s * 0.8f, s * 0.46f, 0f), tint)

            else -> {
                // "partly cloudy" and any unmapped phrase: sun peeking from behind a cloud.
                drawSun(s * 0.22f, cx - s * 0.12f, cy - s * 0.14f, withRays = false)
                drawPath(cloudPath(s * 0.66f, s * 0.38f, s * 0.08f), tint)
            }
        }
    }
}
