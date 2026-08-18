package com.tileshell.core.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Points along a superellipse (Lamé curve) boundary, centered in a
 * [width]×[height] box:
 * `x(t) = sign(cos t)·|cos t|^(2/n)·(width/2)`, `y(t) = sign(sin t)·|sin
 * t|^(2/n)·(height/2)`, offset to the box's centre. [exponent] `n` is how
 * "square" the curve is — `n=2` is a plain ellipse; higher `n` bulges the
 * curve out toward the corners, and `n≈5` is the One UI / iOS "squircle"
 * look this exists for (as `n→∞` it approaches a rectangle). Unlike a
 * `RoundedCornerShape`, which snaps from a straight edge into a circular
 * arc, this curve's curvature changes continuously — that continuity is the
 * whole visual difference and the reason a corner radius can't express it.
 *
 * Pure Kotlin (`kotlin.math` only, no `Path`/`Canvas`), so it's directly
 * unit-testable — `SquircleShape` below builds a real Compose `Path` from
 * these points, which native graphics calls this project's plain-JVM unit
 * tests can't exercise (confirmed empirically: constructing a bare
 * `androidx.compose.ui.graphics.Path` throws in this test setup, since
 * there's no Robolectric and `returnDefaultValues` isn't enabled).
 *
 * Returns an empty list for a non-positive [width] or [height].
 */
fun superellipsePoints(width: Float, height: Float, exponent: Float, steps: Int = 64): List<Pair<Float, Float>> {
    require(steps >= 4) { "steps must be at least 4 to form a closed shape" }
    if (width <= 0f || height <= 0f) return emptyList()
    val n = exponent.coerceAtLeast(0.1f)
    val cx = width / 2.0
    val cy = height / 2.0
    return (0 until steps).map { i ->
        val t = 2.0 * PI * i / steps
        val ct = cos(t)
        val st = sin(t)
        val x = sign(ct) * abs(ct).pow(2.0 / n) * cx
        val y = sign(st) * abs(st).pow(2.0 / n) * cy
        (cx + x).toFloat() to (cy + y).toFloat()
    }
}

/** The exponent that gives the One UI / iOS "squircle" look. */
const val SQUIRCLE_EXPONENT = 5f

/**
 * A [Shape] built from [superellipsePoints] — the real superellipse curve,
 * not a `RoundedCornerShape` approximation. Used only for icon masking
 * (`IconShapes.kt`); tiles keep their existing `RoundedCornerShape` clip
 * since at the small radii tiles use (a few dp on a large surface) the two
 * are visually indistinguishable, so the squircle only earns its keep at the
 * larger, more-rounded scale an icon mask renders at.
 */
class SquircleShape(private val exponent: Float = SQUIRCLE_EXPONENT) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val points = superellipsePoints(size.width, size.height, exponent)
        if (points.isEmpty()) return Outline.Rectangle(Rect(Offset.Zero, size))
        val path = Path().apply {
            moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) lineTo(points[i].first, points[i].second)
            close()
        }
        return Outline.Generic(path)
    }
}
