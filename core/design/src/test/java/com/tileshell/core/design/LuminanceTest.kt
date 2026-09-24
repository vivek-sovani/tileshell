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
    fun `ensureContrast leaves an already-legible colour untouched`() {
        // White already clears any reasonable minRatio against black.
        assertEquals(Color.White, ensureContrast(Color.White, Color.Black))
    }

    @Test
    fun `ensureContrast lightens a dark colour that fails contrast against a dark background`() {
        // The reported bug: a dark, desaturated blue accent drawn on Quick
        // Panel's own dark widget-card background (approximated here by
        // near-black) was returned completely unchanged, well under any
        // legible contrast ratio.
        val darkBlue = Color(0xFF14247A)
        val against = Color.Black
        assertTrue(contrastRatio(darkBlue, against) < 4.5f)
        val fixed = ensureContrast(darkBlue, against)
        assertTrue(contrastRatio(fixed, against) >= 4.5f)
        // Nudged toward white (the higher-contrast direction against black),
        // not toward black itself.
        assertTrue(fixed.red >= darkBlue.red && fixed.green >= darkBlue.green && fixed.blue >= darkBlue.blue)
    }

    @Test
    fun `ensureContrast darkens a light colour that fails contrast against a light background`() {
        val paleBlue = Color(0xFFAFC8F5)
        val against = Color.White
        assertTrue(contrastRatio(paleBlue, against) < 4.5f)
        val fixed = ensureContrast(paleBlue, against)
        assertTrue(contrastRatio(fixed, against) >= 4.5f)
    }

    @Test
    fun `ensureContrast never overshoots past pure black or white`() {
        // An against-colour with no legible pick at all (mid-grey) still
        // terminates at one of the two extremes rather than looping forever.
        val fixed = ensureContrast(Color.Black, Color.Gray, minRatio = 100f)
        assertTrue(fixed == Color.White || fixed == Color.Black)
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
