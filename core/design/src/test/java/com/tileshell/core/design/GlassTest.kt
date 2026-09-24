package com.tileshell.core.design

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassTest {

    @Test
    fun `alpha at fully opaque slider`() {
        // t = 0 → 0.62 + 0.05
        assertEquals(0.67f, Glass.alpha(0f), 1e-6f)
    }

    @Test
    fun `alpha at fully transparent slider`() {
        // t = 1 → 0.05
        assertEquals(0.05f, Glass.alpha(1f), 1e-6f)
    }

    @Test
    fun `alpha at midpoint`() {
        // t = 0.5 → 0.62*0.5 + 0.05 = 0.36
        assertEquals(0.36f, Glass.alpha(0.5f), 1e-6f)
    }

    @Test
    fun `alpha at prototype default transparency`() {
        // fresh() uses transparency 0.55 → 0.62*0.45 + 0.05 = 0.329
        assertEquals(0.329f, Glass.alpha(0.55f), 1e-6f)
    }

    @Test
    fun `alpha is clamped to valid range`() {
        assertEquals(1f, Glass.alpha(-1f), 0f) // 1.29 unclamped → 1
        assertEquals(0f, Glass.alpha(2f), 0f)  // -0.57 unclamped → 0
    }
}
