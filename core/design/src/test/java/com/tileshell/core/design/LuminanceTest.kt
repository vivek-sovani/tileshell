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

    @Test
    fun `contrast ratio of black and white is the maximum, 21 to 1`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun `contrast ratio of a colour against itself is 1`() {
        assertEquals(1f, contrastRatio(Color(0xFF2B78E4), Color(0xFF2B78E4)), 0.01f)
    }

    @Test
    fun `contrast ratio is symmetric`() {
        val a = Color(0xFF2B78E4)
        val b = Color(0xFF1452CC)
        assertEquals(contrastRatio(a, b), contrastRatio(b, a), 0.001f)
    }

    @Test
    fun `prefersDarkText picks black on a light background`() {
        assertTrue(prefersDarkText(Color.White))
    }

    @Test
    fun `prefersDarkText picks white on a dark background`() {
        assertFalse(prefersDarkText(Color.Black))
    }

    @Test
    fun `prefersDarkText corrects a real case isLightBackground's coarse threshold gets wrong`() {
        // This app's own "lime" accent (#7CB518): its perceived luminance
        // (0.573) sits just under isLightBackground's 0.6 cutoff, so the old
        // "useDarkText = isLightBackground(accent)" call picked WHITE text —
        // but white's real contrast ratio against this colour is only ~2.5:1
        // (below even WCAG's large-text 3:1 minimum), while black's is
        // ~8.5:1. prefersDarkText picks correctly; isLightBackground doesn't.
        val lime = Color(0xFF7CB518)
        assertFalse(isLightBackground(lime))
        assertTrue(prefersDarkText(lime))
        assertTrue(contrastRatio(Color.Black, lime) > contrastRatio(Color.White, lime))
    }
}
