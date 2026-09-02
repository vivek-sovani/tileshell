package com.tileshell.feature.livetiles.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * The real phase-accurate crescent/gibbous/full disc, ported from the in-app
 * [com.tileshell.feature.livetiles.MoonPhaseVisual] Canvas draw (user-
 * reported: a generic glyph — [ic_widget_moonphase]'s static drawable — isn't
 * good enough for a gadget whose entire point is showing *tonight's* shape).
 * Same two-half-ellipse construction, just on plain [android.graphics] Canvas/
 * Path instead of Compose's `DrawScope` — pure 2D drawing, so (like
 * [accentGradientBitmap]) this needs no window/Activity/Compose composition
 * and is safe to call from a background Worker. [onAccent] doubles as the lit
 * colour; shadow/rim are the same alpha-scaled derivatives
 * [com.tileshell.feature.livetiles.MoonPhaseVisual] uses (0.18/0.4) against
 * `FaceText`, which in a widget's context just *is* [onAccent].
 */
fun moonPhaseBitmap(fraction: Double, onAccent: Int, sizePx: Int = 128): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val r = sizePx / 2f
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    fun withAlpha(alpha: Float): Int {
        val a = (Color.alpha(onAccent) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(onAccent), Color.green(onAccent), Color.blue(onAccent))
    }

    val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = onAccent; style = Paint.Style.FILL }
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0.18f); style = Paint.Style.FILL }
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(0.4f)
        style = Paint.Style.STROKE
        strokeWidth = sizePx / 64f
    }

    fun halfEllipsePath(rx: Float, rightSide: Boolean): Path = Path().apply {
        moveTo(cx, cy - r)
        arcTo(RectF(cx - rx, cy - r, cx + rx, cy + r), -90f, if (rightSide) 180f else -180f)
        close()
    }

    val litRight = fraction < 0.5
    val cosVal = cos(2 * PI * fraction).toFloat()
    val rx = abs(cosVal) * r
    val isGibbous = cosVal < 0f

    canvas.drawCircle(cx, cy, r, shadowPaint)
    canvas.drawPath(halfEllipsePath(r, litRight), litPaint)
    canvas.drawPath(halfEllipsePath(rx, if (isGibbous) !litRight else litRight), if (isGibbous) litPaint else shadowPaint)
    canvas.drawCircle(cx, cy, r - rimPaint.strokeWidth / 2f, rimPaint)

    return bitmap
}
