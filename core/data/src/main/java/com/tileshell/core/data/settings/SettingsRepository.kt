package com.tileshell.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.tileshell.core.data.TileColors
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * Typed ("Proto"-style) DataStore serializer for [LauncherSettings]. Uses the
 * flat [SettingsCodec] text encoding rather than the protobuf toolchain — see
 * docs/DECISIONS.md (S17) — keeping the schema transactional, Flow-backed and
 * dependency-light. A new/missing store reads as [defaultValue]; a corrupt one
 * decodes tolerantly to the defaults.
 */
object SettingsSerializer : Serializer<LauncherSettings> {
    override val defaultValue = LauncherSettings()

    override suspend fun readFrom(input: InputStream): LauncherSettings =
        SettingsCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: LauncherSettings, output: OutputStream) {
        output.write(SettingsCodec.encode(t).encodeToByteArray())
    }
}

private val Context.settingsDataStore: DataStore<LauncherSettings> by dataStore(
    fileName = "launcher_settings.pb",
    serializer = SettingsSerializer,
)

/**
 * Reads and writes the personalization settings (FR-7). All writes are
 * transactional through DataStore; the [settings] flow emits the live value so
 * the UI re-applies theme/accent the instant a setter lands.
 */
class SettingsRepository(private val store: DataStore<LauncherSettings>) {

    val settings: Flow<LauncherSettings> = store.data

    suspend fun setDark(dark: Boolean) {
        store.updateData { it.copy(dark = dark) }
    }

    /** Sets the global accent; ignores ids outside the 14-colour palette. */
    suspend fun setAccent(accentId: String) {
        if (accentId !in TileColors.IDS) return
        store.updateData { it.copy(accentId = accentId) }
    }

    /**
     * Toggle transparent-tile ("glass") mode (FR-7). Mutually exclusive with
     * [tiledWallpaper] — both paint a tile's fill (glass tint vs. a window onto the
     * wallpaper), so enabling one turns the other off rather than leaving a toggle
     * on that silently has no visible effect.
     */
    suspend fun setGlass(glass: Boolean) {
        store.updateData {
            if (glass) it.copy(glass = true, tiledWallpaper = false) else it.copy(glass = false)
        }
    }

    /** Set the tile-transparency slider value; clamped to 0..1. */
    suspend fun setTransparency(transparency: Float) {
        store.updateData { it.copy(transparency = transparency.coerceIn(0f, 1f)) }
    }

    /** Toggle the blur-wallpaper effect (FR-7). */
    suspend fun setBlur(blur: Boolean) {
        store.updateData { it.copy(blur = blur) }
    }

    /** Select a bundled gradient wallpaper, clearing any custom/Bing/slideshow photo and resetting crop. */
    suspend fun setWallpaper(wallpaperId: String) {
        store.updateData {
            it.copy(wallpaperId = wallpaperId, customWallpaperUri = null, bingWallpaper = false,
                wallpaperSlideshowEnabled = false,
                wallpaperAlignX = 0.5f, wallpaperAlignY = 0.5f, wallpaperZoom = 1f)
        }
    }

    /** Set a user-picked custom wallpaper with its focal-point alignment and zoom (FR-7). */
    suspend fun setCustomWallpaper(uri: String, alignX: Float = 0.5f, alignY: Float = 0.5f, zoom: Float = 1f) {
        store.updateData {
            it.copy(customWallpaperUri = uri, bingWallpaper = false, wallpaperSlideshowEnabled = false,
                wallpaperAlignX = alignX.coerceIn(0f, 1f),
                wallpaperAlignY = alignY.coerceIn(0f, 1f),
                wallpaperZoom = zoom.coerceIn(LauncherSettings.MIN_WALLPAPER_ZOOM, LauncherSettings.MAX_WALLPAPER_ZOOM))
        }
    }

    /**
     * Turn the Microsoft Bing image-of-the-day wallpaper on or off. Turning it on
     * only flips the flag (the centred image arrives once `BingWallpaperWorker`
     * downloads it via [setBingImage]) and turns the slideshow off (the two photo
     * sources are mutually exclusive); turning it off clears the downloaded photo
     * so the previously selected gradient ([wallpaperId]) shows again.
     */
    suspend fun setBingWallpaper(on: Boolean) {
        store.updateData {
            if (on) it.copy(bingWallpaper = true, wallpaperSlideshowEnabled = false)
            else it.copy(bingWallpaper = false, customWallpaperUri = null,
                wallpaperAlignX = 0.5f, wallpaperAlignY = 0.5f, wallpaperZoom = 1f)
        }
    }

    /**
     * Store the freshly downloaded Bing image URI (called by `BingWallpaperWorker`).
     * No-ops if the user has since turned Bing off, so a late download can't
     * resurrect the wallpaper after it was dismissed.
     */
    suspend fun setBingImage(uri: String) {
        store.updateData {
            // Keep the user's chosen framing (alignX/Y/zoom) across daily refreshes.
            if (!it.bingWallpaper) it else it.copy(customWallpaperUri = uri)
        }
    }

    /**
     * Pin a specific image from the Bing history viewer (called by
     * `BingWallpaperWorker`'s "pin this day" path). Unlike [setCustomWallpaper],
     * this *keeps* [LauncherSettings.bingWallpaper] on rather than switching to a
     * plain fixed photo — the user reached this picker from within Bing mode, so
     * the personalize wallpaper-type selector should keep showing "bing", not
     * silently reclassify to "photo". The daily worker will still refresh over it
     * on its next scheduled run, same as any other day's Bing image.
     */
    suspend fun setPinnedBingImage(uri: String) {
        store.updateData {
            it.copy(customWallpaperUri = uri, bingWallpaper = true, wallpaperSlideshowEnabled = false,
                wallpaperAlignX = 0.5f, wallpaperAlignY = 0.5f, wallpaperZoom = 1f)
        }
    }

    /** Update the focal-point alignment and zoom of the current custom wallpaper. */
    suspend fun setWallpaperAlignment(alignX: Float, alignY: Float, zoom: Float = 1f) {
        store.updateData {
            it.copy(wallpaperAlignX = alignX.coerceIn(0f, 1f),
                wallpaperAlignY = alignY.coerceIn(0f, 1f),
                wallpaperZoom = zoom.coerceIn(LauncherSettings.MIN_WALLPAPER_ZOOM, LauncherSettings.MAX_WALLPAPER_ZOOM))
        }
    }

    /** Remove all wallpaper (custom/Bing/slideshow photo + gradient), leaving the theme bg colour. */
    suspend fun clearWallpaper() {
        store.updateData {
            it.copy(wallpaperId = "none", customWallpaperUri = null, bingWallpaper = false,
                wallpaperSlideshowEnabled = false)
        }
    }

    /**
     * Turn the wallpaper slideshow on or off — rotates [LauncherSettings.customWallpaperUri]
     * through `WallpaperSlideshowStore`'s photos on a timer instead of a single fixed
     * photo. Mutually exclusive with [bingWallpaper]. The caller (StartViewModel) is
     * responsible for scheduling/cancelling `WallpaperSlideshowWorker` and for applying
     * the first photo immediately on enable, same division of responsibility as
     * [setBingWallpaper]/`BingWallpaperWorker`.
     */
    suspend fun setWallpaperSlideshowEnabled(enabled: Boolean) {
        store.updateData {
            if (enabled) it.copy(wallpaperSlideshowEnabled = true, bingWallpaper = false)
            else it.copy(wallpaperSlideshowEnabled = false)
        }
    }

    /** Set the slideshow rotation interval in minutes; clamped to WorkManager's periodic floor. */
    suspend fun setWallpaperSlideshowInterval(minutes: Int) {
        store.updateData {
            it.copy(wallpaperSlideshowIntervalMin = minutes.coerceIn(
                LauncherSettings.MIN_SLIDESHOW_INTERVAL_MIN, LauncherSettings.MAX_SLIDESHOW_INTERVAL_MIN))
        }
    }

    /**
     * Advance the slideshow to [uri] at [index] (called by `WallpaperSlideshowWorker`
     * and from the UI when photos are (re)picked while the slideshow is already on).
     * Resets alignment/zoom to centred/1x since the crop of the previous photo rarely
     * suits a different one. No-ops if the slideshow has since been turned off.
     */
    suspend fun setWallpaperSlide(uri: String, index: Int) {
        store.updateData {
            if (!it.wallpaperSlideshowEnabled) it
            else it.copy(customWallpaperUri = uri, wallpaperSlideshowIndex = index,
                wallpaperAlignX = 0.5f, wallpaperAlignY = 0.5f, wallpaperZoom = 1f)
        }
    }

    /**
     * Toggle "wallpaper behind tiles" mode (the dark screen + show-through tiles).
     * Mutually exclusive with [glass] — see [setGlass].
     */
    suspend fun setTiledWallpaper(on: Boolean) {
        store.updateData {
            if (on) it.copy(tiledWallpaper = true, glass = false) else it.copy(tiledWallpaper = false)
        }
    }

    /** Toggle the left "feed" page (the 3rd pager page reached by swiping right). */
    suspend fun setFeedEnabled(enabled: Boolean) {
        store.updateData { it.copy(feedEnabled = enabled) }
    }

    /** Toggle following the device dark-mode setting (vs. the manual [dark] choice). */
    suspend fun setFollowSystemTheme(follow: Boolean) {
        store.updateData { it.copy(followSystemTheme = follow) }
    }

    /** Set the tile corner radius (0–20 dp; matches the feed widget cards). */
    suspend fun setCornerRadius(radius: Float) {
        store.updateData { it.copy(cornerRadius = radius.coerceIn(0f, 20f)) }
    }

    /** Set the inter-tile gap (0–16 dp). */
    suspend fun setTileGap(gap: Float) {
        store.updateData { it.copy(tileGap = gap.coerceIn(0f, 16f)) }
    }

    /** Switch the default tile colour source (global accent vs app-icon colour). */
    suspend fun setTileColorSource(source: TileColorSource) {
        store.updateData { it.copy(tileColorSource = source) }
    }

    /**
     * Reset the tile-style controls (corner radius, spacing, grid columns, fill,
     * colour source, font) to their defaults, so a user who over-personalised can
     * recover. Theme, accent, glass and wallpaper are deliberate choices and kept.
     */
    suspend fun resetTileStyle() {
        val d = LauncherSettings()
        store.updateData {
            it.copy(
                cornerRadius = d.cornerRadius,
                tileGap = d.tileGap,
                columns = d.columns,
                tileFill = d.tileFill,
                tileColorSource = d.tileColorSource,
                fontStyle = d.fontStyle,
            )
        }
    }

    /** Switch tile fill between flat solid and diagonal gradient. */
    suspend fun setTileFill(fill: TileFill) {
        store.updateData { it.copy(tileFill = fill) }
    }

    /** Switch the UI font (system default, Outfit, or Nunito). */
    suspend fun setFontStyle(style: FontStyle) {
        store.updateData { it.copy(fontStyle = style) }
    }

    /** Set the Start grid column count (small-tile columns per row); clamped to 4..6. */
    suspend fun setColumns(columns: Int) {
        store.updateData {
            it.copy(columns = columns.coerceIn(
                LauncherSettings.MIN_COLUMNS, LauncherSettings.MAX_COLUMNS))
        }
    }

    /** Switch the Start grid's gap-closing behaviour (dense repack vs. WP-style sticky gaps). */
    suspend fun setTilePackMode(mode: TilePackMode) {
        store.updateData { it.copy(tilePackMode = mode) }
    }

    /** Replace all settings with a restored backup value atomically. */
    suspend fun restoreSettings(settings: LauncherSettings) {
        store.updateData { settings }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        store.updateData { it.copy(autoBackupEnabled = enabled) }
    }

    suspend fun setAutoBackupIntervalHours(hours: Int) {
        store.updateData { it.copy(autoBackupIntervalHours = hours.coerceIn(1, 24)) }
    }

    suspend fun setEdgeStripEnabled(enabled: Boolean) {
        store.updateData { it.copy(edgeStripEnabled = enabled) }
    }

    suspend fun setEdgeStripPosition(position: String) {
        store.updateData { it.copy(edgeStripPosition = position) }
    }

    suspend fun setEdgeStripApps(apps: List<String>) {
        store.updateData { it.copy(edgeStripApps = apps) }
    }

    suspend fun setEdgeStripBackground(bgId: String) {
        store.updateData { it.copy(edgeStripBackgroundId = bgId) }
    }

    suspend fun setEdgeStripHandleSize(size: String) {
        if (size !in setOf("thin", "thick")) return
        store.updateData { it.copy(edgeStripHandleSize = size) }
    }

    /** Toggle "lock layout": while on, Start never enters edit mode on a long-press. */
    suspend fun setLockLayout(locked: Boolean) {
        store.updateData { it.copy(lockLayout = locked) }
    }

    companion object {
        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.settingsDataStore)
    }
}
