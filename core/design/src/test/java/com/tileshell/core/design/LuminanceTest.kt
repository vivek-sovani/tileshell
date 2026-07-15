package com.tileshell.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminanceTest {

    @Test
    fun `dark screen background is not light`() {
        assertFalse(isLightBackground(DarkColorTokens.bg))
    }

    @Test
    fun `light screen background is light`() {
        assertTrue(isLightBackground(LightColorTokens.bg))
    }

    @Test
    fun `pure black and white are the luminance extremes`() {
        assertEquals(0f, perceivedLuminance(Color.Black), 1e-6f)
        assertEquals(1f, perceivedLuminance(Color.White), 1e-6f)
    }

    @Test
    fun `a bundled gradient's dark base is not light, even lifted toward the light theme`() {
        // Matches Wallpapers.themedBase's ~45% lift for the Aurora gradient.
        val lifted = lerp(Wallpapers.Aurora.base, LightColorTokens.bg, 0.45f)
        assertFalse(isLightBackground(lifted))
    }
}
