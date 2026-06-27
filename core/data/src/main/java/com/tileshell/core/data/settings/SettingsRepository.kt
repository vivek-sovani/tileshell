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

    /** Toggle transparent-tile ("glass") mode (FR-7). */
    suspend fun setGlass(glass: Boolean) {
        store.updateData { it.copy(glass = glass) }
    }

    /** Set the tile-transparency slider value; clamped to 0..1. */
    suspend fun setTransparency(transparency: Float) {
        store.updateData { it.copy(transparency = transparency.coerceIn(0f, 1f)) }
    }

    /** Toggle the blur-wallpaper effect (FR-7). */
    suspend fun setBlur(blur: Boolean) {
        store.updateData { it.copy(blur = blur) }
    }

    /** Select a bundled gradient wallpaper, clearing any custom/Bing photo and resetting crop. */
    suspend fun setWallpaper(wallpaperId: String) {
        store.updateData {
            it.copy(wallpaperId = wallpaperId, customWallpaperUri = null, bingWallpaper = false,
                wallpaperAlignX = 0.5f, wallpaperAlignY = 0.5f)
        }
    }

    /** Set a user-picked custom wallpaper with its focal-point alignment (FR-7). */
    suspend fun setCustomWallpaper(uri: String, alignX: Float = 0.5f, alignY: Float = 0.5f) {
        store.updateData {
            it.copy(customWallpaperUri = uri, bingWallpaper = false,
                wallpaperAlignX = alignX.coerceIn(0f, 1f),
                wallpaperAlignY = alignY.coerceIn(0f, 1f))
        }
    }

    /**
     * Turn the Microsoft Bing image-of-the-day wallpaper on or off. Turning it on
     * only flips the flag (the centred image arrives once `BingWallpaperWorker`
     * downloads it via [setBingImage]); turning it off clears the downloaded photo
     * so the previously selected gradient ([wallpaperId]) shows again.
     */
    suspend fun setBingWallpaper(on: Boolean) {
        store.updateData {
            if (on) it.copy(bingWallpaper = true)
            else it.copy(bingWallpaper = false, customWallpaperUri = null,
                wallpaperAlignX = 0.5f, wallpaperAlignY = 0.5f)
        }
    }

    /**
     * Store the freshly downloaded Bing image URI (called by `BingWallpaperWorker`).
     * No-ops if the user has since turned Bing off, so a late download can't
     * resurrect the wallpaper after it was dismissed.
     */
    suspend fun setBingImage(uri: String) {
        store.updateData {
            // Keep the user's chosen framing (alignX/Y) across daily refreshes.
            if (!it.bingWallpaper) it else it.copy(customWallpaperUri = uri)
        }
    }

    /** Update the focal-point alignment of the current custom wallpaper. */
    suspend fun setWallpaperAlignment(alignX: Float, alignY: Float) {
        store.updateData {
            it.copy(wallpaperAlignX = alignX.coerceIn(0f, 1f),
                wallpaperAlignY = alignY.coerceIn(0f, 1f))
        }
    }

    /** Remove all wallpaper (custom/Bing photo + gradient), leaving the theme bg colour. */
    suspend fun clearWallpaper() {
        store.updateData { it.copy(wallpaperId = "none", customWallpaperUri = null, bingWallpaper = false) }
    }

    /** Toggle "wallpaper behind tiles" mode (the dark screen + show-through tiles). */
    suspend fun setTiledWallpaper(on: Boolean) {
        store.updateData { it.copy(tiledWallpaper = on) }
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

    companion object {
        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.settingsDataStore)
    }
}
