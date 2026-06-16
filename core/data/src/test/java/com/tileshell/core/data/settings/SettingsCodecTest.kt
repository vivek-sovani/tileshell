package com.tileshell.core.data.settings

import com.tileshell.core.data.TileColors
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the [SettingsCodec] round-trip and its tolerance to bad input. */
class SettingsCodecTest {

    @Test
    fun `round-trips all fields`() {
        val settings = LauncherSettings(
            followSystemTheme = false,
            dark = false,
            accentId = "magenta",
            glass = false,
            transparency = 0.3f,
            blur = true,
            wallpaperId = "ocean",
            customWallpaperUri = "content://media/external/images/42",
            tiledWallpaper = true,
            feedEnabled = false,
        )
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
    }

    @Test
    fun `bad feedEnabled keeps the default`() {
        assertEquals(LauncherSettings().feedEnabled, SettingsCodec.decode("feedEnabled=nope").feedEnabled)
    }

    @Test
    fun `followSystemTheme decodes and bad value keeps default`() {
        assertEquals(false, SettingsCodec.decode("followSystemTheme=false").followSystemTheme)
        assertEquals(
            LauncherSettings().followSystemTheme,
            SettingsCodec.decode("followSystemTheme=sometimes").followSystemTheme,
        )
    }

    @Test
    fun `transparency out of range is clamped`() {
        assertEquals(1f, SettingsCodec.decode("transparency=4.0").transparency, 0f)
        assertEquals(0f, SettingsCodec.decode("transparency=-2.0").transparency, 0f)
    }

    @Test
    fun `bad transparency keeps the default`() {
        assertEquals(
            LauncherSettings().transparency,
            SettingsCodec.decode("transparency=loads").transparency,
            0f,
        )
    }

    @Test
    fun `empty custom wallpaper decodes to null`() {
        assertEquals(null, SettingsCodec.decode("customWallpaper=").customWallpaperUri)
    }

    @Test
    fun `custom wallpaper uri with equals signs round-trips`() {
        val uri = "content://x/y?id=7&w=1"
        assertEquals(uri, SettingsCodec.decode("customWallpaper=$uri").customWallpaperUri)
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
