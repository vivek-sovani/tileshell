package com.tileshell.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for the pure superellipse math (`superellipsePoints`) — the
 * only part of the icon-shape masking that's exercisable in this project's
 * plain-JVM unit tests. `SquircleShape`/`IconShape.toComposeShape()` build
 * real `Path`/`Shape` objects, whose graphics calls throw here (no
 * Robolectric, `returnDefaultValues` not enabled — confirmed empirically),
 * so those are left to on-device verification.
 */
class SquircleTest {

    @Test
    fun `degenerate size returns no points`() {
        assertTrue(superellipsePoints(0f, 100f, SQUIRCLE_EXPONENT).isEmpty())
        assertTrue(superellipsePoints(100f, 0f, SQUIRCLE_EXPONENT).isEmpty())
        assertTrue(superellipsePoints(-5f, 100f, SQUIRCLE_EXPONENT).isEmpty())
    }

    @Test
    fun `every point stays within the bounding box`() {
        val points = superellipsePoints(120f, 80f, SQUIRCLE_EXPONENT, steps = 128)
        for ((x, y) in points) {
            assertTrue("x=$x out of [0,120]", x in -0.01f..120.01f)
            assertTrue("y=$y out of [0,80]", y in -0.01f..80.01f)
        }
    }

    @Test
    fun `the curve is closed — first and last points are adjacent on the boundary`() {
        // superellipsePoints doesn't repeat the start point at the end (the
        // caller's Path.close() does that), but consecutive points around the
        // wrap-around must still be close together, not a jump across the shape.
        val points = superellipsePoints(100f, 100f, SQUIRCLE_EXPONENT, steps = 64)
        val (lastX, lastY) = points.last()
        val (firstX, firstY) = points.first()
        val gap = kotlin.math.hypot((lastX - firstX).toDouble(), (lastY - firstY).toDouble())
        // Adjacent-step gap for 64 steps around a 100x100 box is small; a wrap
        // bug would put opposite-side points here instead, tens of px apart.
        assertTrue("gap between wrap-around points is too large: $gap", gap < 20.0)
    }

    @Test
    fun `at the cardinal points the curve touches the box edges regardless of exponent`() {
        // t=0 -> rightmost edge; every exponent's cos/sin math passes through
        // the same four cardinal points, which is what keeps the mask filling
        // its full allotted size rather than shrinking as the exponent changes.
        for (exponent in listOf(1.5f, 2f, SQUIRCLE_EXPONENT, 12f)) {
            val points = superellipsePoints(100f, 100f, exponent, steps = 64)
            val rightmost = points.maxOf { it.first }
            assertEquals("exponent=$exponent", 100f, rightmost, 0.5f)
        }
    }

    @Test
    fun `higher exponent pulls the diagonal point further toward the corner`() {
        // At t=45 degrees, a higher exponent bulges the curve outward toward
        // the (width,height) corner — this is the actual visual difference a
        // squircle has over a plain ellipse, and the property that proves the
        // exponent parameter does something rather than being decorative.
        fun diagonalDistanceFromCenter(exponent: Float): Double {
            val steps = 72
            // Nearest sample index to exactly 45 degrees.
            val index = steps / 8
            val (x, y) = superellipsePoints(100f, 100f, exponent, steps)[index]
            return kotlin.math.hypot((x - 50f).toDouble(), (y - 50f).toDouble())
        }
        val ellipse = diagonalDistanceFromCenter(2f)
        val squircle = diagonalDistanceFromCenter(SQUIRCLE_EXPONENT)
        val nearRect = diagonalDistanceFromCenter(20f)
        assertTrue("squircle ($squircle) must bulge past a plain ellipse ($ellipse)", squircle > ellipse)
        assertTrue("a near-rectangle exponent ($nearRect) must bulge past the squircle ($squircle)", nearRect > squircle)
    }

    @Test
    fun `requesting fewer than 4 steps is rejected`() {
        var threw = false
        try {
            superellipsePoints(100f, 100f, SQUIRCLE_EXPONENT, steps = 3)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("steps < 4 can't form a closed shape and must be rejected", threw)
    }

    @Test
    fun `a zero or negative exponent is coerced rather than producing NaN`() {
        val points = superellipsePoints(100f, 100f, 0f, steps = 32)
        assertTrue(points.isNotEmpty())
        for ((x, y) in points) {
            assertTrue(!x.isNaN() && !y.isNaN())
        }
    }

    // `IconShape`/`toComposeShape()` tests live in
    // feature/start/src/test/.../IconCellShapeTest.kt — that enum is a
    // :core:data persisted setting and the mapping to a Shape lives in
    // :feature:start, the only module depending on both (see IconCellView.kt's
    // doc comment on toComposeShape). :core:design has no dependency on
    // :core:data, so neither type is reachable from this test file.
}
