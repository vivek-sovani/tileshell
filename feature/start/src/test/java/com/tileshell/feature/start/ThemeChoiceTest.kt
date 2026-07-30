package com.tileshell.feature.start

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the Quick Panel's single tap-to-cycle theme tile. */
class ThemeChoiceTest {

    @Test
    fun `follow-system wins over dark-light when set`() {
        assertEquals(ThemeChoice.AUTO, themeChoiceFor(dark = true, followSystemTheme = true))
        assertEquals(ThemeChoice.AUTO, themeChoiceFor(dark = false, followSystemTheme = true))
    }

    @Test
    fun `dark or light chosen when not following the system`() {
        assertEquals(ThemeChoice.DARK, themeChoiceFor(dark = true, followSystemTheme = false))
        assertEquals(ThemeChoice.LIGHT, themeChoiceFor(dark = false, followSystemTheme = false))
    }

    @Test
    fun `cycles dark to light to auto and back to dark`() {
        assertEquals(ThemeChoice.LIGHT, nextThemeChoice(ThemeChoice.DARK))
        assertEquals(ThemeChoice.AUTO, nextThemeChoice(ThemeChoice.LIGHT))
        assertEquals(ThemeChoice.DARK, nextThemeChoice(ThemeChoice.AUTO))
    }
}
