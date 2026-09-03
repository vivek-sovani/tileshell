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
            tilePackMode = TilePackMode.STICKY,
            themedIcons = true,
        )
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
    }

    @Test
    fun `bad themedIcons keeps the default`() {
        assertEquals(true, SettingsCodec.decode("themedIcons=true").themedIcons)
        assertEquals(LauncherSettings().themedIcons, SettingsCodec.decode("themedIcons=maybe").themedIcons)
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
    fun `tilePackMode round-trips and unknown value keeps default`() {
        assertEquals(TilePackMode.STICKY, SettingsCodec.decode("tilePackMode=STICKY").tilePackMode)
        assertEquals(TilePackMode.DENSE, SettingsCodec.decode("tilePackMode=DENSE").tilePackMode)
        assertEquals(TilePackMode.FREE, SettingsCodec.decode("tilePackMode=FREE").tilePackMode)
        assertEquals(
            TilePackMode.FREE,
            SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(tilePackMode = TilePackMode.FREE))).tilePackMode,
        )
        assertEquals(LauncherSettings().tilePackMode, SettingsCodec.decode("tilePackMode=GARBLED").tilePackMode)
    }

    @Test
    fun `homeStyle round-trips and unknown value keeps default`() {
        assertEquals(HomeStyle.TILES, SettingsCodec.decode("homeStyle=TILES").homeStyle)
        assertEquals(HomeStyle.ICONS, SettingsCodec.decode("homeStyle=ICONS").homeStyle)
        assertEquals(
            HomeStyle.ICONS,
            SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(homeStyle = HomeStyle.ICONS))).homeStyle,
        )
        assertEquals(LauncherSettings().homeStyle, SettingsCodec.decode("homeStyle=GARBLED").homeStyle)
    }

    @Test
    fun `iconShape round-trips and unknown value keeps default`() {
        assertEquals(IconShape.ORIGINAL, SettingsCodec.decode("iconShape=ORIGINAL").iconShape)
        assertEquals(IconShape.CIRCLE, SettingsCodec.decode("iconShape=CIRCLE").iconShape)
        assertEquals(IconShape.SQUIRCLE, SettingsCodec.decode("iconShape=SQUIRCLE").iconShape)
        assertEquals(IconShape.ROUNDED, SettingsCodec.decode("iconShape=ROUNDED").iconShape)
        assertEquals(IconShape.SQUARE, SettingsCodec.decode("iconShape=SQUARE").iconShape)
        assertEquals(
            IconShape.SQUIRCLE,
            SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(iconShape = IconShape.SQUIRCLE))).iconShape,
        )
        assertEquals(LauncherSettings().iconShape, SettingsCodec.decode("iconShape=GARBLED").iconShape)
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

    @Test
    fun `lockLayout decodes and bad value keeps default`() {
        assertEquals(true, SettingsCodec.decode("lockLayout=true").lockLayout)
        assertEquals(
            LauncherSettings().lockLayout,
            SettingsCodec.decode("lockLayout=nope").lockLayout,
        )
        val s = LauncherSettings(lockLayout = true)
        assertEquals(true, SettingsCodec.decode(SettingsCodec.encode(s)).lockLayout)
    }

    @Test
    fun `hideStatusBar decodes and bad value keeps default`() {
        assertEquals(true, SettingsCodec.decode("hideStatusBar=true").hideStatusBar)
        assertEquals(
            LauncherSettings().hideStatusBar,
            SettingsCodec.decode("hideStatusBar=nope").hideStatusBar,
        )
        val s = LauncherSettings(hideStatusBar = true)
        assertEquals(true, SettingsCodec.decode(SettingsCodec.encode(s)).hideStatusBar)
    }

    @Test
    fun `userName round-trips and defaults to blank`() {
        assertEquals("", LauncherSettings().userName)
        val s = LauncherSettings(userName = "vivek")
        assertEquals("vivek", SettingsCodec.decode(SettingsCodec.encode(s)).userName)
        assertEquals("", SettingsCodec.decode("userName=").userName)
    }

    @Test
    fun `liveTilesEnabled decodes and bad value keeps default, default is on`() {
        assertEquals(true, LauncherSettings().liveTilesEnabled)
        assertEquals(false, SettingsCodec.decode("liveTiles=false").liveTilesEnabled)
        assertEquals(
            LauncherSettings().liveTilesEnabled,
            SettingsCodec.decode("liveTiles=nope").liveTilesEnabled,
        )
        val s = LauncherSettings(liveTilesEnabled = false)
        assertEquals(false, SettingsCodec.decode(SettingsCodec.encode(s)).liveTilesEnabled)
    }

    @Test
    fun `feedNoBackground decodes and bad value keeps default, default is off`() {
        assertEquals(false, LauncherSettings().feedNoBackground)
        assertEquals(true, SettingsCodec.decode("feedNoBg=true").feedNoBackground)
        assertEquals(
            LauncherSettings().feedNoBackground,
            SettingsCodec.decode("feedNoBg=nope").feedNoBackground,
        )
        val s = LauncherSettings(feedNoBackground = true)
        assertEquals(true, SettingsCodec.decode(SettingsCodec.encode(s)).feedNoBackground)
    }

    @Test
    fun `quickPanelTileOrder and quickPanelTileSizes round-trip and default to empty`() {
        assertEquals(emptyList<String>(), LauncherSettings().quickPanelTileOrder)
        assertEquals(emptyList<String>(), LauncherSettings().quickPanelTileSizes)
        val s = LauncherSettings(
            quickPanelTileOrder = listOf("bluetooth", "wifi", "location"),
            quickPanelTileSizes = listOf("flashlight:2", "wifi:1"),
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `taskAutoClearDaily defaults on, round-trips, and a bad value keeps default`() {
        assertEquals(true, LauncherSettings().taskAutoClearDaily)
        assertEquals(false, SettingsCodec.decode("taskAutoClearDaily=false").taskAutoClearDaily)
        assertEquals(
            LauncherSettings().taskAutoClearDaily,
            SettingsCodec.decode("taskAutoClearDaily=nope").taskAutoClearDaily,
        )
        val s = LauncherSettings(taskAutoClearDaily = false)
        assertEquals(false, SettingsCodec.decode(SettingsCodec.encode(s)).taskAutoClearDaily)
    }

    @Test
    fun `stock, commodity and sports refresh rates default, round-trip, and a bad value keeps default`() {
        assertEquals(LiveRefreshRate.DEFAULT, LauncherSettings().stockRefreshRate)
        assertEquals(LiveRefreshRate.DEFAULT, LauncherSettings().commodityRefreshRate)
        assertEquals(LiveRefreshRate.DEFAULT, LauncherSettings().sportsRefreshRate)

        assertEquals(LiveRefreshRate.EVERY_15_MIN, SettingsCodec.decode("stockRefreshRate=EVERY_15_MIN").stockRefreshRate)
        assertEquals(LiveRefreshRate.EVERY_5_MIN, SettingsCodec.decode("commodityRefreshRate=EVERY_5_MIN").commodityRefreshRate)
        assertEquals(LiveRefreshRate.EVERY_30_MIN, SettingsCodec.decode("sportsRefreshRate=EVERY_30_MIN").sportsRefreshRate)

        assertEquals(LiveRefreshRate.DEFAULT, SettingsCodec.decode("stockRefreshRate=GARBLED").stockRefreshRate)

        val s = LauncherSettings(
            stockRefreshRate = LiveRefreshRate.EVERY_1_MIN,
            commodityRefreshRate = LiveRefreshRate.EVERY_15_MIN,
            sportsRefreshRate = LiveRefreshRate.EVERY_30_MIN,
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }
}

class LiveRefreshRateResolveMsTest {

    @Test
    fun `DEFAULT resolves to the caller's own supplied interval`() {
        assertEquals(90_000L, LiveRefreshRate.DEFAULT.resolveMs(90_000L))
        assertEquals(60_000L, LiveRefreshRate.DEFAULT.resolveMs(60_000L))
    }

    @Test
    fun `every fixed rate resolves to its own interval regardless of the caller's default`() {
        assertEquals(60_000L, LiveRefreshRate.EVERY_1_MIN.resolveMs(90_000L))
        assertEquals(5 * 60_000L, LiveRefreshRate.EVERY_5_MIN.resolveMs(90_000L))
        assertEquals(15 * 60_000L, LiveRefreshRate.EVERY_15_MIN.resolveMs(90_000L))
        assertEquals(30 * 60_000L, LiveRefreshRate.EVERY_30_MIN.resolveMs(90_000L))
    }
}
