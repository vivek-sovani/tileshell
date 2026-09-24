package com.tileshell.core.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `accentOnCard leaves an already-legible bright accent untouched in dark theme`() {
        val brightBlue = Color(0xFF2B78E4) // this app's default accent
        assertEquals(brightBlue, Glass.accentOnCard(dark = true, accent = brightBlue))
    }

    @Test
    fun `accentOnCard lightens a dark accent in dark theme instead of returning it unchanged`() {
        // Real user-reported bug (screenshot): "on" Quick Panel tiles (wifi,
        // bluetooth, the theme tile) under widget-card style were unreadable
        // against their own dark card in dark theme, because this used to
        // just return the raw accent with no adjustment at all.
        val darkBlue = Color(0xFF14247A)
        val fixed = Glass.accentOnCard(dark = true, accent = darkBlue)
        assertTrue(fixed != darkBlue)
        assertTrue(contrastRatio(fixed, Color.Black) >= 4.5f)
    }

    @Test
    fun `accentOnCard still darkens toward black for light theme first, per the existing rule`() {
        val brightBlue = Color(0xFF2B78E4)
        val lightThemeResult = Glass.accentOnCard(dark = false, accent = brightBlue)
        // Darker than the raw accent (the pre-existing 18%-toward-black blend)
        // and still legible against a light card.
        assertTrue(lightThemeResult.red < brightBlue.red || lightThemeResult.blue < brightBlue.blue)
        assertTrue(contrastRatio(lightThemeResult, Color.White) >= 4.5f)
    }
}
