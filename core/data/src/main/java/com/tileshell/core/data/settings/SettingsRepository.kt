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

    /** Select a bundled gradient wallpaper, clearing any custom photo and resetting crop. */
    suspend fun setWallpaper(wallpaperId: String) {
        store.updateData {
            it.copy(wallpaperId = wallpaperId, customWallpaperUri = null,
                wallpaperAlignX = 0.5f, wallpaperAlignY = 0.5f)
        }
    }

    /** Set a user-picked custom wallpaper with its focal-point alignment (FR-7). */
    suspend fun setCustomWallpaper(uri: String, alignX: Float = 0.5f, alignY: Float = 0.5f) {
        store.updateData {
            it.copy(customWallpaperUri = uri,
                wallpaperAlignX = alignX.coerceIn(0f, 1f),
                wallpaperAlignY = alignY.coerceIn(0f, 1f))
        }
    }

    /** Update the focal-point alignment of the current custom wallpaper. */
    suspend fun setWallpaperAlignment(alignX: Float, alignY: Float) {
        store.updateData {
            it.copy(wallpaperAlignX = alignX.coerceIn(0f, 1f),
                wallpaperAlignY = alignY.coerceIn(0f, 1f))
        }
    }

    /** Remove all wallpaper (custom photo + gradient), leaving the theme bg colour. */
    suspend fun clearWallpaper() {
        store.updateData { it.copy(wallpaperId = "none", customWallpaperUri = null) }
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

    /** Set the tile corner radius (0–12 dp). */
    suspend fun setCornerRadius(radius: Float) {
        store.updateData { it.copy(cornerRadius = radius.coerceIn(0f, 12f)) }
    }

    /** Switch tile fill between flat solid and diagonal gradient. */
    suspend fun setTileFill(fill: TileFill) {
        store.updateData { it.copy(tileFill = fill) }
    }

    /** Switch the UI font (system default, Outfit, or Nunito). */
    suspend fun setFontStyle(style: FontStyle) {
        store.updateData { it.copy(fontStyle = style) }
    }

    companion object {
        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.settingsDataStore)
    }
}
