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
            bingWallpaper = true,
            tiledWallpaper = true,
            feedEnabled = false,
            cornerRadius = 8f,
            tileFill = TileFill.GRADIENT,
            fontStyle = FontStyle.NUNITO,
            columns = 6,
        )
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
    }

    @Test
    fun `bingWallpaper decodes and bad value keeps default`() {
        assertEquals(true, SettingsCodec.decode("bingWallpaper=true").bingWallpaper)
        assertEquals(
            LauncherSettings().bingWallpaper,
            SettingsCodec.decode("bingWallpaper=daily").bingWallpaper,
        )
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

    @Test
    fun `wallpaper alignment round-trips`() {
        val s = LauncherSettings(wallpaperAlignX = 0.25f, wallpaperAlignY = 0.75f)
        val decoded = SettingsCodec.decode(SettingsCodec.encode(s))
        assertEquals(0.25f, decoded.wallpaperAlignX, 0.0001f)
        assertEquals(0.75f, decoded.wallpaperAlignY, 0.0001f)
    }

    @Test
    fun `wallpaper alignment out of range is clamped`() {
        assertEquals(1f, SettingsCodec.decode("wallAlignX=2.5").wallpaperAlignX, 0f)
        assertEquals(0f, SettingsCodec.decode("wallAlignY=-1.0").wallpaperAlignY, 0f)
    }

    @Test
    fun `missing wallpaper alignment defaults to centre`() {
        val d = SettingsCodec.decode("")
        assertEquals(0.5f, d.wallpaperAlignX, 0f)
        assertEquals(0.5f, d.wallpaperAlignY, 0f)
    }

    @Test
    fun `tile gap round-trips and out-of-range is clamped`() {
        assertEquals(8f, SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(tileGap = 8f))).tileGap, 0.0001f)
        assertEquals(16f, SettingsCodec.decode("tileGap=99").tileGap, 0f)
        assertEquals(0f, SettingsCodec.decode("tileGap=-4").tileGap, 0f)
        assertEquals(LauncherSettings().tileGap, SettingsCodec.decode("tileGap=wide").tileGap, 0f)
    }

    @Test
    fun `corner radius round-trips and out-of-range is clamped`() {
        val s = LauncherSettings(cornerRadius = 6f)
        assertEquals(6f, SettingsCodec.decode(SettingsCodec.encode(s)).cornerRadius, 0.0001f)
        assertEquals(20f, SettingsCodec.decode("cornerRadius=99").cornerRadius, 0f)
        assertEquals(0f, SettingsCodec.decode("cornerRadius=-3").cornerRadius, 0f)
    }

    @Test
    fun `tileFill round-trips and unknown value keeps default`() {
        assertEquals(TileFill.GRADIENT, SettingsCodec.decode("tileFill=GRADIENT").tileFill)
        assertEquals(LauncherSettings().tileFill, SettingsCodec.decode("tileFill=SPARKLE").tileFill)
    }

    @Test
    fun `columns round-trips and out-of-range is clamped`() {
        assertEquals(5, SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(columns = 5))).columns)
        assertEquals(6, SettingsCodec.decode("columns=9").columns)
        assertEquals(4, SettingsCodec.decode("columns=1").columns)
        assertEquals(LauncherSettings().columns, SettingsCodec.decode("columns=lots").columns)
    }

    @Test
    fun `fontStyle round-trips and unknown value keeps default`() {
        assertEquals(FontStyle.NUNITO, SettingsCodec.decode("fontStyle=NUNITO").fontStyle)
        assertEquals(FontStyle.OUTFIT, SettingsCodec.decode("fontStyle=OUTFIT").fontStyle)
        assertEquals(LauncherSettings().fontStyle, SettingsCodec.decode("fontStyle=COMIC").fontStyle)
    }

    @Test
    fun `wallpaper zoom round-trips and out-of-range is clamped`() {
        val s = LauncherSettings(wallpaperZoom = 2.2f)
        assertEquals(2.2f, SettingsCodec.decode(SettingsCodec.encode(s)).wallpaperZoom, 0.0001f)
        assertEquals(3f, SettingsCodec.decode("wallZoom=9").wallpaperZoom, 0f)
        assertEquals(1f, SettingsCodec.decode("wallZoom=0.2").wallpaperZoom, 0f)
        assertEquals(LauncherSettings().wallpaperZoom, SettingsCodec.decode("wallZoom=deep").wallpaperZoom, 0f)
    }

    @Test
    fun `wallpaper slideshow fields round-trip and out-of-range is clamped`() {
        val s = LauncherSettings(
            wallpaperSlideshowEnabled = true,
            wallpaperSlideshowIntervalMin = 60,
            wallpaperSlideshowIndex = 3,
        )
        val decoded = SettingsCodec.decode(SettingsCodec.encode(s))
        assertEquals(true, decoded.wallpaperSlideshowEnabled)
        assertEquals(60, decoded.wallpaperSlideshowIntervalMin)
        assertEquals(3, decoded.wallpaperSlideshowIndex)

        assertEquals(180, SettingsCodec.decode("slideshowInterval=999").wallpaperSlideshowIntervalMin)
        assertEquals(15, SettingsCodec.decode("slideshowInterval=1").wallpaperSlideshowIntervalMin)
        assertEquals(
            LauncherSettings().wallpaperSlideshowEnabled,
            SettingsCodec.decode("slideshowEnabled=maybe").wallpaperSlideshowEnabled,
        )
    }
}
