package com.tileshell.feature.start

import androidx.compose.runtime.Composable
import com.tileshell.core.data.settings.LauncherSettings
import com.tileshell.core.data.settings.TileColorSource
import com.tileshell.core.design.Wallpapers.NONE_ID
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.Wallpapers
import com.tileshell.feature.start.feed.rememberFeedPalette

/**
 * The accent id for a hub drawn outside Start (a hub that is its own activity): the same rule as
 * Start's own `uiAccentId` — the wallpaper's colour when tiles take theirs from the wallpaper and
 * "hub colour" follows the tiles, else the chosen accent — so a hub looks the same wherever it runs.
 */
@Composable
fun rememberHubAccentId(settings: LauncherSettings): String {
    if (settings.tileColorSource != TileColorSource.WALLPAPER_ACCENT || !settings.hubsFollowTiles) return settings.accentId
    val accent = TileAccents.forId(settings.accentId)
    val noWallpaper = settings.wallpaperId == NONE_ID && settings.customWallpaperUri == null
    if (noWallpaper) return settings.accentId
    val photo = settings.customWallpaperUri?.let { rememberWallpaperBitmap(it) }
    val color = rememberFeedPalette(photo, Wallpapers.forId(settings.wallpaperId), accent).second
    return TileAccents.idForColor(color)
}
