package com.tileshell.core.data.settings

import com.tileshell.core.data.TileColors

enum class TileFill { FLAT, GRADIENT }
enum class FontStyle { SYSTEM, OUTFIT, NUNITO }

/**
 * Default colour for a tile that has no explicit per-tile override (FR-7):
 * [GLOBAL_ACCENT] paints every tile the single global accent; [APP_ICON] tints
 * each app tile with the dominant colour of its launcher icon (a freshly pinned
 * app then shows in its own brand colour). A per-tile override still wins.
 */
enum class TileColorSource { GLOBAL_ACCENT, APP_ICON }

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
    /** Periodic background layout snapshot saves (for LayoutHistorySheet). */
    val autoBackupEnabled: Boolean = true,
    /** Hours between automatic snapshots: 1, 4, 6, 12, or 24. */
    val autoBackupIntervalHours: Int = 6,
) {
    companion object {
        const val DEFAULT_COLUMNS = 4
        const val MIN_COLUMNS = 4
        const val MAX_COLUMNS = 6
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
        append("cornerRadius=").append(settings.cornerRadius).append('\n')
        append("tileGap=").append(settings.tileGap).append('\n')
        append("tileColorSource=").append(settings.tileColorSource.name).append('\n')
        append("tileFill=").append(settings.tileFill.name).append('\n')
        append("fontStyle=").append(settings.fontStyle.name).append('\n')
        append("columns=").append(settings.columns).append('\n')
        append("autoBackup=").append(settings.autoBackupEnabled).append('\n')
        append("autoBackupInterval=").append(settings.autoBackupIntervalHours)
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
        var cornerRadius = d.cornerRadius
        var tileGap = d.tileGap
        var tileColorSource = d.tileColorSource
        var tileFill = d.tileFill
        var fontStyle = d.fontStyle
        var columns = d.columns
        var autoBackupEnabled = d.autoBackupEnabled
        var autoBackupIntervalHours = d.autoBackupIntervalHours
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
                "cornerRadius" -> value.toFloatOrNull()?.let { cornerRadius = it.coerceIn(0f, 20f) }
                "tileGap" -> value.toFloatOrNull()?.let { tileGap = it.coerceIn(0f, 16f) }
                "tileColorSource" ->
                    TileColorSource.entries.find { it.name == value }?.let { tileColorSource = it }
                "tileFill" -> TileFill.entries.find { it.name == value }?.let { tileFill = it }
                "fontStyle" -> FontStyle.entries.find { it.name == value }?.let { fontStyle = it }
                "columns" -> value.toIntOrNull()?.let {
                    columns = it.coerceIn(LauncherSettings.MIN_COLUMNS, LauncherSettings.MAX_COLUMNS)
                }
                "autoBackup" -> autoBackupEnabled = value.toBooleanStrictOrNull() ?: autoBackupEnabled
                "autoBackupInterval" -> value.toIntOrNull()?.let {
                    autoBackupIntervalHours = it.coerceIn(1, 24)
                }
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
            cornerRadius = cornerRadius,
            tileGap = tileGap,
            tileColorSource = tileColorSource,
            tileFill = tileFill,
            fontStyle = fontStyle,
            columns = columns,
            autoBackupEnabled = autoBackupEnabled,
            autoBackupIntervalHours = autoBackupIntervalHours,
        )
    }
}
