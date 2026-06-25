package com.tileshell.feature.start

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the directional reorder hysteresis ([shouldReorder]). */
class ShouldReorderTest {

    private val target = Rect(0f, 0f, 100f, 100f) // midpoints at x=50, y=50

    @Test
    fun `moving right commits only past the x midpoint`() {
        val right = Offset(10f, 0f)
        assertFalse(shouldReorder(target, Offset(40f, 50f), right)) // near edge, not yet
        assertTrue(shouldReorder(target, Offset(60f, 50f), right))  // crossed midpoint
    }

    @Test
    fun `moving left commits only past the x midpoint from the other side`() {
        val left = Offset(-10f, 0f)
        assertFalse(shouldReorder(target, Offset(60f, 50f), left))
        assertTrue(shouldReorder(target, Offset(40f, 50f), left))
    }

    @Test
    fun `a vertical-dominant move tests the y midpoint`() {
        val down = Offset(1f, 10f)
        assertFalse(shouldReorder(target, Offset(50f, 40f), down))
        assertTrue(shouldReorder(target, Offset(50f, 60f), down))
    }

    @Test
    fun `a degenerate target never reorders`() {
        assertFalse(shouldReorder(Rect(0f, 0f, 0f, 0f), Offset(0f, 0f), Offset(10f, 0f)))
    }
}
