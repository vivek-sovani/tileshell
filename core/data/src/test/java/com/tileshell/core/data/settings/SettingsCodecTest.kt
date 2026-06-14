package com.tileshell.core.data.settings

import com.tileshell.core.data.TileColors
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the [SettingsCodec] round-trip and its tolerance to bad input. */
class SettingsCodecTest {

    @Test
    fun `round-trips both fields`() {
        val settings = LauncherSettings(dark = false, accentId = "magenta")
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
    }

    @Test
    fun `round-trips the defaults`() {
        val defaults = LauncherSettings()
        assertEquals(defaults, SettingsCodec.decode(SettingsCodec.encode(defaults)))
    }

    @Test
    fun `empty text decodes to defaults`() {
        assertEquals(LauncherSettings(), SettingsCodec.decode(""))
    }

    @Test
    fun `unknown accent id falls back to the default accent`() {
        val decoded = SettingsCodec.decode("dark=false\naccent=chartreuse")
        assertEquals(LauncherSettings().accentId, decoded.accentId)
        // The valid field on the same blob is still honoured.
        assertEquals(false, decoded.dark)
    }

    @Test
    fun `malformed lines and bad booleans are ignored`() {
        val decoded = SettingsCodec.decode("garbage\n=oops\ndark=maybe\naccent=teal")
        assertEquals(LauncherSettings().dark, decoded.dark) // "maybe" rejected
        assertEquals("teal", decoded.accentId)
    }

    @Test
    fun `every palette id is accepted`() {
        TileColors.IDS.forEach { id ->
            assertEquals(id, SettingsCodec.decode("accent=$id").accentId)
        }
    }
}
