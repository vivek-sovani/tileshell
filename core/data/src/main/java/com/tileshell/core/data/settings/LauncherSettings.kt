package com.tileshell.core.data.settings

import com.tileshell.core.data.TileColors

enum class TileFill { FLAT, GRADIENT }
enum class FontStyle { SYSTEM, OUTFIT, NUNITO }

/**
 * Which cell renderer the Start grid uses (the "icons mode" arc, for a user
 * who wants a normal Android-style home screen instead of Windows Phone
 * tiles). [TILES] is the original WP tile grid, unchanged. [ICONS] shares the
 * exact same layout engine — persistence, gestures, folders, drawer, backup —
 * and only changes what a SMALL (1×1) cell renders: a shaped app icon with a
 * label beneath instead of a filled tile. A cell at MEDIUM or larger still
 * renders as a live tile/stack/folder exactly as in TILES mode either way —
 * this is derived purely from the tile's own size, not a second stored flag,
 * so live tiles, stacks and folders keep working in ICONS mode with no
 * separate code path, and switching styles rewrites nothing (a tile's stored
 * size is just ignored while the smaller renderer is active).
 */
enum class HomeStyle { TILES, ICONS }

/**
 * The mask applied to an app's own icon in ICONS home style (Personalize's
 * "icon shape" row, shown only while `homeStyle == ICONS`). [ORIGINAL] is
 * the fresh-install default — no masking, so a brand-new ICONS-mode install
 * looks exactly like today's real app icons until the user opts into a
 * shape. Tiles never take this setting — see `:core:design`'s `Squircle.kt`
 * doc comment for why tile corners stay on the existing `RoundedCornerShape`
 * instead of sharing this setting. The mapping from this enum to an actual
 * Compose `Shape` lives in `:feature:start` (the only module that needs both
 * this type and `:core:design`'s masking primitives), not here — this file
 * only ever holds the persisted value, matching [TileFill]/[FontStyle]/
 * [TileColorSource]/[HomeStyle] above.
 */
enum class IconShape { CIRCLE, SQUIRCLE, ROUNDED, SQUARE, ORIGINAL }

/**
 * How the Start grid closes gaps left by a removed/resized tile (user-selectable
 * "tile arrangement"): [DENSE] always repacks every tile toward the top-left on
 * every change (the launcher's original behaviour, matching the HTML prototype's
 * CSS `grid-auto-flow: dense`); [STICKY] mirrors real Windows Phone — a tile
 * stays at its anchored grid cell and a gap it leaves behind stays open until the
 * user drags something into it, except a *fully* empty row (no tile touching any
 * column), which always collapses. [FREE] is stickier still: nothing moves unless
 * the user moves it — no push-down on drop, and even a fully empty row stays open.
 * Dropping onto an occupied cell swaps the two tiles instead of displacing anything
 * (see `GridPacker.swapPlacement`). [STICKY] and [FREE] are both "anchored" modes
 * for placement purposes (see `GridPacker.packSticky`'s `slotOf`); only [FREE]
 * skips the full-empty-row collapse and the push-down-on-drop/resize behaviour.
 * A new tile always appends after the current bottom row in every mode (never
 * backfills an earlier gap).
 */
enum class TilePackMode { DENSE, STICKY, FREE }

/** True for either mode that renders from a tile's anchored `gridSlot` (i.e. not [TilePackMode.DENSE]). */
val TilePackMode.isAnchored: Boolean get() = this != TilePackMode.DENSE

/**
 * Default colour for a tile that has no explicit per-tile override (FR-7):
 * [GLOBAL_ACCENT] paints every tile the single global accent; [APP_ICON] tints
 * each app tile with the dominant colour of its launcher icon (a freshly pinned
 * app then shows in its own brand colour); [WALLPAPER_ACCENT] tints every tile
 * with the same wallpaper-derived accent colour the feed/glance page and Quick
 * Panel already use (see `rememberFeedPalette`), so tiles, feed, and Quick
 * Panel all read as one coordinated palette. A per-tile override still wins.
 */
enum class TileColorSource { GLOBAL_ACCENT, APP_ICON, WALLPAPER_ACCENT }

/**
 * Persisted personalization (FR-7). Kept deliberately flat and framework-free so
 * it can be serialized by [SettingsCodec] and unit-tested without Android.
 *
 * @property followSystemTheme when true (default) the active theme follows the
 *   device dark-mode setting and [dark] is ignored for rendering; when false the
 *   manual [dark] choice is used. The manual choice is retained either way.
 * @property dark manual dark theme when true, light when false (prototype
 *   `state.theme`); only applied while [followSystemTheme] is false.
 * @property accentId one of the 14 [TileColors] ids — the single global accent
 *   (`state.accent`) used by app-list/chrome *and* every Start tile (one uniform
 *   tile colour across the Start screen, default blue; per-tile colourId ignored).
 * @property glass transparent-tile ("glass") mode on/off (`state.glass`); default
 *   off so a fresh install shows solid Nokia-blue tiles (the classic WP look)
 *   rather than a translucent glass fill.
 * @property transparency tile-transparency slider 0..1 feeding the alpha formula
 *   (`state.transparency`); only meaningful while [glass] is on.
 * @property blur blur-wallpaper toggle (`state.blur`)
 * @property wallpaperId id of the selected bundled gradient (`state.wall`), or
 *   `"none"` (mirrors `Wallpapers.NONE_ID` in `:core:design`, not imported here to
 *   avoid a cross-module dependency) for a flat theme-background fill — the
 *   default, so a fresh install has no wallpaper. Ignored while
 *   [customWallpaperUri] is set.
 * @property customWallpaperUri persisted content URI of a user-picked photo
 *   (`state.customWall`), or null for a bundled gradient. Also reused to hold the
 *   downloaded Bing image when [bingWallpaper] is on.
 * @property bingWallpaper when true the wallpaper is the Microsoft Bing image of
 *   the day, refreshed daily by `BingWallpaperWorker` into [customWallpaperUri].
 *   Selecting a bundled gradient or the user's own photo clears this flag.
 * @property tiledWallpaper "wallpaper behind tiles" mode: the screen goes dark and
 *   the wallpaper shows only *through* the tiles (each tile a window onto the same
 *   screen-anchored image), so all gaps/borders stay dark. WP photo-background look.
 * @property feedEnabled whether the left "feed" page (the 3rd pager page reached by
 *   swiping right from Start) is present. Default on; when off the pager clamps to
 *   Start⇄app-list and the feed surface is not composed.
 * @property wallpaperAlignX horizontal focal point for the custom wallpaper photo
 *   [0..1]: 0 = left edge visible, 0.5 = centred, 1 = right edge visible. Only
 *   meaningful while [customWallpaperUri] is set; ignored for bundled gradients.
 * @property wallpaperAlignY vertical focal point, same 0..1 scale.
 * @property wallpaperZoom zoom level applied on top of the cover-fit crop
 *   ([MIN_WALLPAPER_ZOOM]..[MAX_WALLPAPER_ZOOM], 1 = no zoom). Set from the crop
 *   overlay's pinch gesture; only meaningful while [customWallpaperUri] is set.
 * @property wallpaperSlideshowEnabled rotates [customWallpaperUri] through the
 *   photos in `WallpaperSlideshowStore` on a timer (`WallpaperSlideshowWorker`)
 *   instead of showing one fixed photo. Mutually exclusive with [bingWallpaper].
 * @property wallpaperSlideshowIntervalMin minutes between rotations
 *   ([MIN_SLIDESHOW_INTERVAL_MIN]..[MAX_SLIDESHOW_INTERVAL_MIN]; WorkManager's
 *   periodic-work floor is 15 min).
 * @property wallpaperSlideshowIndex index of the currently shown photo within
 *   the slideshow list, so the worker knows which one to advance past.
 * @property lockLayout when true, a long-press on Start never enters edit
 *   mode — no jiggle, no drag/resize/unpin/colour-picker — so the layout
 *   can't be changed by accident. Toggled from Personalize; unrelated to the
 *   settings-gear device screen lock.
 * @property userName the name shown in the feed's "good morning, `<name>`"
 *   greeting. Blank by default; best-effort auto-seeded once from the device's
 *   own contact profile (see `StartViewModel.init`), and freely editable from
 *   Personalize afterward. Blank renders the greeting with no name/comma.
 * @property liveTilesEnabled master on/off switch for live-tile flipping/
 *   updates (clock, weather, notifications, etc.). Default on; folded into
 *   the existing `rememberLiveTilesActive` gate alongside battery saver and
 *   system animation settings when off — a purely cosmetic pause, not a data
 *   toggle (badges/counts still update, only the flip animation stops).
 * @property feedNoBackground forces the feed/glance screen to a flat theme
 *   background (and the plain global accent for its cards/chrome) regardless
 *   of Start's own wallpaper choice — an independent opt-out, since the feed
 *   otherwise always shows a colour gradient synthesized from Start's
 *   wallpaper even when that wallpaper is a photo or stock gradient the user
 *   is happy to see behind Start's tiles but not behind the feed's text.
 * @property hideStatusBar hides the Android system status bar (clock/battery/
 *   signal strip) at the top of the screen while TileShell is in the
 *   foreground, like several other launchers offer. Default **on** — per
 *   explicit user request, this is the out-of-the-box look, not something
 *   that needs opting into from Personalize (the toggle there is only for
 *   anyone who wants the bar back). The bar can still be
 *   pulled down temporarily with a swipe from the top edge. Default off.
 */
data class LauncherSettings(
    val followSystemTheme: Boolean = true,
    val dark: Boolean = true,
    val accentId: String = "blue",
    val glass: Boolean = false,
    val transparency: Float = 0.55f,
    val blur: Boolean = false,
    val wallpaperId: String = "none",
    val customWallpaperUri: String? = null,
    val bingWallpaper: Boolean = false,
    val tiledWallpaper: Boolean = false,
    val feedEnabled: Boolean = true,
    val wallpaperAlignX: Float = 0.5f,
    val wallpaperAlignY: Float = 0.5f,
    val wallpaperZoom: Float = 1f,
    val wallpaperSlideshowEnabled: Boolean = false,
    val wallpaperSlideshowIntervalMin: Int = 30,
    val wallpaperSlideshowIndex: Int = 0,
    val cornerRadius: Float = 0f,
    /**
     * Gap between tiles in dp (FR-7). Default ≈ the prototype's tight WP spacing;
     * raising it gives a spaced "rounded-card" look. Surfaced in Personalize only
     * while tiles are fully rounded. Clamped 0..16 on decode.
     */
    val tileGap: Float = 3f,
    val tileColorSource: TileColorSource = TileColorSource.GLOBAL_ACCENT,
    val tileFill: TileFill = TileFill.FLAT,
    val fontStyle: FontStyle = FontStyle.OUTFIT,
    /**
     * Number of small-tile columns in the Start grid: 4 (default), 5, or 6.
     * Tile footprints stay constant (small 1, medium 2, wide 4 = 2× medium); a
     * larger count simply packs more columns of small tiles into a row. Clamped
     * to 4..6 on decode.
     */
    val columns: Int = DEFAULT_COLUMNS,
    /**
     * Gap-closing behaviour for the Start grid. Defaults to [TilePackMode.STICKY]
     * (windows phone style) per the user's own real-WP-device comparison — a
     * fresh install's default layout has no anchored tiles yet, so it renders
     * identically to dense packing until the user actually unpins/resizes/drags
     * something, at which point gaps start being preserved rather than repacked.
     */
    val tilePackMode: TilePackMode = TilePackMode.STICKY,
    /** Which cell renderer the Start grid uses — WP tiles, or Android-style icons. */
    val homeStyle: HomeStyle = HomeStyle.TILES,
    /** Icon mask applied in ICONS home style; unused in TILES. */
    val iconShape: IconShape = IconShape.ORIGINAL,
    /** Periodic background layout snapshot saves (for LayoutHistorySheet). */
    val autoBackupEnabled: Boolean = true,
    /** Hours between automatic snapshots: 1, 4, 6, 12, or 24. */
    val autoBackupIntervalHours: Int = 6,
    /** Optional edge-strip overlay: a thin row/column of app shortcuts at a screen edge. */
    val edgeStripEnabled: Boolean = false,
    /** Edge where the strip appears: "bottom" (horizontal) or "left" (vertical). */
    val edgeStripPosition: String = "bottom",
    /** Ordered package names shown in the edge strip, pipe-separated in the codec. */
    val edgeStripApps: List<String> = emptyList(),
    /** Wallpaper id for the strip background, or "none" for a semi-transparent surface. */
    val edgeStripBackgroundId: String = "none",
    /** Pull-tab handle pill weight: "thin" (subtle bar) or "thick" (bold bar). Panel height is constant. */
    val edgeStripHandleSize: String = "thick",
    val lockLayout: Boolean = false,
    val userName: String = "",
    val liveTilesEnabled: Boolean = true,
    val feedNoBackground: Boolean = false,
    val hideStatusBar: Boolean = true,
) {
    companion object {
        const val DEFAULT_COLUMNS = 4
        const val MIN_COLUMNS = 4
        const val MAX_COLUMNS = 6
        const val MIN_WALLPAPER_ZOOM = 1f
        const val MAX_WALLPAPER_ZOOM = 3f
        const val MIN_SLIDESHOW_INTERVAL_MIN = 15
        const val MAX_SLIDESHOW_INTERVAL_MIN = 180
    }
}

/**
 * Tiny line-oriented `key=value` codec for [LauncherSettings]. Pure Kotlin (no
 * org.json, no protobuf toolchain) so the round-trip is JVM-unit-testable, and
 * tolerant: unknown keys, malformed lines, and out-of-range values fall back to
 * the defaults rather than throwing (a corrupt store reads as defaults). The
 * value runs to the end of the line, so content URIs (which may contain `=`)
 * round-trip intact.
 */
object SettingsCodec {

    fun encode(settings: LauncherSettings): String = buildString {
        append("followSystemTheme=").append(settings.followSystemTheme).append('\n')
        append("dark=").append(settings.dark).append('\n')
        append("accent=").append(settings.accentId).append('\n')
        append("glass=").append(settings.glass).append('\n')
        append("transparency=").append(settings.transparency).append('\n')
        append("blur=").append(settings.blur).append('\n')
        append("wallpaper=").append(settings.wallpaperId).append('\n')
        append("customWallpaper=").append(settings.customWallpaperUri.orEmpty()).append('\n')
        append("bingWallpaper=").append(settings.bingWallpaper).append('\n')
        append("tiledWallpaper=").append(settings.tiledWallpaper).append('\n')
        append("feedEnabled=").append(settings.feedEnabled).append('\n')
        append("wallAlignX=").append(settings.wallpaperAlignX).append('\n')
        append("wallAlignY=").append(settings.wallpaperAlignY).append('\n')
        append("wallZoom=").append(settings.wallpaperZoom).append('\n')
        append("slideshowEnabled=").append(settings.wallpaperSlideshowEnabled).append('\n')
        append("slideshowInterval=").append(settings.wallpaperSlideshowIntervalMin).append('\n')
        append("slideshowIndex=").append(settings.wallpaperSlideshowIndex).append('\n')
        append("cornerRadius=").append(settings.cornerRadius).append('\n')
        append("tileGap=").append(settings.tileGap).append('\n')
        append("tileColorSource=").append(settings.tileColorSource.name).append('\n')
        append("tileFill=").append(settings.tileFill.name).append('\n')
        append("fontStyle=").append(settings.fontStyle.name).append('\n')
        append("columns=").append(settings.columns).append('\n')
        append("tilePackMode=").append(settings.tilePackMode.name).append('\n')
        append("homeStyle=").append(settings.homeStyle.name).append('\n')
        append("iconShape=").append(settings.iconShape.name).append('\n')
        append("autoBackup=").append(settings.autoBackupEnabled).append('\n')
        append("autoBackupInterval=").append(settings.autoBackupIntervalHours).append('\n')
        append("edgeStripEnabled=").append(settings.edgeStripEnabled).append('\n')
        append("edgeStripPosition=").append(settings.edgeStripPosition).append('\n')
        append("edgeStripApps=").append(settings.edgeStripApps.joinToString("|")).append('\n')
        append("edgeStripBg=").append(settings.edgeStripBackgroundId).append('\n')
        append("edgeStripHandleSize=").append(settings.edgeStripHandleSize).append('\n')
        append("lockLayout=").append(settings.lockLayout).append('\n')
        append("userName=").append(settings.userName).append('\n')
        append("liveTiles=").append(settings.liveTilesEnabled).append('\n')
        append("feedNoBg=").append(settings.feedNoBackground).append('\n')
        append("hideStatusBar=").append(settings.hideStatusBar)
    }

    fun decode(text: String): LauncherSettings {
        val d = LauncherSettings()
        var followSystemTheme = d.followSystemTheme
        var dark = d.dark
        var accentId = d.accentId
        var glass = d.glass
        var transparency = d.transparency
        var blur = d.blur
        var wallpaperId = d.wallpaperId
        var customWallpaperUri = d.customWallpaperUri
        var bingWallpaper = d.bingWallpaper
        var tiledWallpaper = d.tiledWallpaper
        var feedEnabled = d.feedEnabled
        var wallpaperAlignX = d.wallpaperAlignX
        var wallpaperAlignY = d.wallpaperAlignY
        var wallpaperZoom = d.wallpaperZoom
        var wallpaperSlideshowEnabled = d.wallpaperSlideshowEnabled
        var wallpaperSlideshowIntervalMin = d.wallpaperSlideshowIntervalMin
        var wallpaperSlideshowIndex = d.wallpaperSlideshowIndex
        var cornerRadius = d.cornerRadius
        var tileGap = d.tileGap
        var tileColorSource = d.tileColorSource
        var tileFill = d.tileFill
        var fontStyle = d.fontStyle
        var columns = d.columns
        var tilePackMode = d.tilePackMode
        var homeStyle = d.homeStyle
        var iconShape = d.iconShape
        var autoBackupEnabled = d.autoBackupEnabled
        var autoBackupIntervalHours = d.autoBackupIntervalHours
        var edgeStripEnabled = d.edgeStripEnabled
        var edgeStripPosition = d.edgeStripPosition
        var edgeStripApps = d.edgeStripApps
        var edgeStripBackgroundId = d.edgeStripBackgroundId
        var edgeStripHandleSize = d.edgeStripHandleSize
        var lockLayout = d.lockLayout
        var userName = d.userName
        var liveTilesEnabled = d.liveTilesEnabled
        var feedNoBackground = d.feedNoBackground
        var hideStatusBar = d.hideStatusBar
        text.lineSequence().forEach { line ->
            val sep = line.indexOf('=')
            if (sep <= 0) return@forEach
            val key = line.substring(0, sep).trim()
            val value = line.substring(sep + 1).trim()
            when (key) {
                "followSystemTheme" -> followSystemTheme = value.toBooleanStrictOrNull() ?: followSystemTheme
                "dark" -> dark = value.toBooleanStrictOrNull() ?: dark
                "accent" -> if (value in TileColors.IDS) accentId = value
                "glass" -> glass = value.toBooleanStrictOrNull() ?: glass
                "transparency" -> value.toFloatOrNull()?.let { transparency = it.coerceIn(0f, 1f) }
                "blur" -> blur = value.toBooleanStrictOrNull() ?: blur
                "wallpaper" -> if (value.isNotEmpty()) wallpaperId = value
                "customWallpaper" -> customWallpaperUri = value.ifEmpty { null }
                "bingWallpaper" -> bingWallpaper = value.toBooleanStrictOrNull() ?: bingWallpaper
                "tiledWallpaper" -> tiledWallpaper = value.toBooleanStrictOrNull() ?: tiledWallpaper
                "feedEnabled" -> feedEnabled = value.toBooleanStrictOrNull() ?: feedEnabled
                "wallAlignX" -> value.toFloatOrNull()?.let { wallpaperAlignX = it.coerceIn(0f, 1f) }
                "wallAlignY" -> value.toFloatOrNull()?.let { wallpaperAlignY = it.coerceIn(0f, 1f) }
                "wallZoom" -> value.toFloatOrNull()?.let {
                    wallpaperZoom = it.coerceIn(LauncherSettings.MIN_WALLPAPER_ZOOM, LauncherSettings.MAX_WALLPAPER_ZOOM)
                }
                "slideshowEnabled" -> wallpaperSlideshowEnabled =
                    value.toBooleanStrictOrNull() ?: wallpaperSlideshowEnabled
                "slideshowInterval" -> value.toIntOrNull()?.let {
                    wallpaperSlideshowIntervalMin = it.coerceIn(
                        LauncherSettings.MIN_SLIDESHOW_INTERVAL_MIN, LauncherSettings.MAX_SLIDESHOW_INTERVAL_MIN)
                }
                "slideshowIndex" -> value.toIntOrNull()?.let { wallpaperSlideshowIndex = it.coerceAtLeast(0) }
                "cornerRadius" -> value.toFloatOrNull()?.let { cornerRadius = it.coerceIn(0f, 20f) }
                "tileGap" -> value.toFloatOrNull()?.let { tileGap = it.coerceIn(0f, 16f) }
                "tileColorSource" ->
                    TileColorSource.entries.find { it.name == value }?.let { tileColorSource = it }
                "tileFill" -> TileFill.entries.find { it.name == value }?.let { tileFill = it }
                "fontStyle" -> FontStyle.entries.find { it.name == value }?.let { fontStyle = it }
                "columns" -> value.toIntOrNull()?.let {
                    columns = it.coerceIn(LauncherSettings.MIN_COLUMNS, LauncherSettings.MAX_COLUMNS)
                }
                "tilePackMode" -> TilePackMode.entries.find { it.name == value }?.let { tilePackMode = it }
                "homeStyle" -> HomeStyle.entries.find { it.name == value }?.let { homeStyle = it }
                "iconShape" -> IconShape.entries.find { it.name == value }?.let { iconShape = it }
                "autoBackup" -> autoBackupEnabled = value.toBooleanStrictOrNull() ?: autoBackupEnabled
                "autoBackupInterval" -> value.toIntOrNull()?.let {
                    autoBackupIntervalHours = it.coerceIn(1, 24)
                }
                "edgeStripEnabled" -> edgeStripEnabled = value.toBooleanStrictOrNull() ?: edgeStripEnabled
                "edgeStripPosition" -> if (value == "bottom" || value == "left") edgeStripPosition = value
                "edgeStripApps" -> edgeStripApps = if (value.isEmpty()) emptyList()
                    else value.split("|").filter { it.isNotBlank() }
                "edgeStripBg" -> if (value.isNotEmpty()) edgeStripBackgroundId = value
                "edgeStripHandleSize" -> if (value in setOf("thin", "thick")) edgeStripHandleSize = value
                "lockLayout" -> lockLayout = value.toBooleanStrictOrNull() ?: lockLayout
                "userName" -> userName = value
                "liveTiles" -> liveTilesEnabled = value.toBooleanStrictOrNull() ?: liveTilesEnabled
                "feedNoBg" -> feedNoBackground = value.toBooleanStrictOrNull() ?: feedNoBackground
                "hideStatusBar" -> hideStatusBar = value.toBooleanStrictOrNull() ?: hideStatusBar
            }
        }
        return LauncherSettings(
            followSystemTheme = followSystemTheme,
            dark = dark,
            accentId = accentId,
            glass = glass,
            transparency = transparency,
            blur = blur,
            wallpaperId = wallpaperId,
            customWallpaperUri = customWallpaperUri,
            bingWallpaper = bingWallpaper,
            tiledWallpaper = tiledWallpaper,
            feedEnabled = feedEnabled,
            wallpaperAlignX = wallpaperAlignX,
            wallpaperAlignY = wallpaperAlignY,
            wallpaperZoom = wallpaperZoom,
            wallpaperSlideshowEnabled = wallpaperSlideshowEnabled,
            wallpaperSlideshowIntervalMin = wallpaperSlideshowIntervalMin,
            wallpaperSlideshowIndex = wallpaperSlideshowIndex,
            cornerRadius = cornerRadius,
            tileGap = tileGap,
            tileColorSource = tileColorSource,
            tileFill = tileFill,
            fontStyle = fontStyle,
            columns = columns,
            tilePackMode = tilePackMode,
            homeStyle = homeStyle,
            iconShape = iconShape,
            autoBackupEnabled = autoBackupEnabled,
            autoBackupIntervalHours = autoBackupIntervalHours,
            edgeStripEnabled = edgeStripEnabled,
            edgeStripPosition = edgeStripPosition,
            edgeStripApps = edgeStripApps,
            edgeStripBackgroundId = edgeStripBackgroundId,
            edgeStripHandleSize = edgeStripHandleSize,
            lockLayout = lockLayout,
            userName = userName,
            liveTilesEnabled = liveTilesEnabled,
            feedNoBackground = feedNoBackground,
            hideStatusBar = hideStatusBar,
        )
    }
}
