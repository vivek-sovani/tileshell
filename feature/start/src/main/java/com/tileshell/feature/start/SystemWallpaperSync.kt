package com.tileshell.feature.start

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.tileshell.core.data.settings.WallpaperSyncTarget
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.renderWallpaperToBitmap

/**
 * Pushes TileShell's own wallpaper (gradient or photo) to the real Android
 * [WallpaperManager] — this app otherwise only ever draws its wallpaper
 * in-app (see [WallpaperBackground.kt]), so the real lock screen (drawn
 * entirely by the system, outside the launcher's control) never reflects
 * it. Requires the normal, auto-granted-at-install SET_WALLPAPER permission.
 *
 * Called from [StartViewModel] on an IO dispatcher — bitmap decode/render
 * plus `WallpaperManager.setBitmap`'s own disk write are both blocking.
 */
object SystemWallpaperSync {

    /**
     * Applies [target] using [customWallpaperUri] (a real photo/Bing image,
     * decoded the same downsampled way the in-app background already is)
     * when set, else the bundled [gradientId] rasterized to a bitmap at the
     * device's own screen size. A no-op for [WallpaperSyncTarget.NONE] or if
     * the bitmap can't be produced; failures are swallowed — this is a
     * best-effort sync, never something a settings change should crash on.
     */
    fun apply(
        context: Context,
        target: WallpaperSyncTarget,
        gradientId: String,
        customWallpaperUri: String?,
        dark: Boolean,
    ) {
        val flags = when (target) {
            WallpaperSyncTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperSyncTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperSyncTarget.HOME_AND_LOCK -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            WallpaperSyncTarget.NONE -> return
        }
        val bitmap = resolveBitmap(context, gradientId, customWallpaperUri, dark) ?: return
        runCatching { WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags) }
    }

    private fun resolveBitmap(
        context: Context,
        gradientId: String,
        customWallpaperUri: String?,
        dark: Boolean,
    ): Bitmap? {
        if (!customWallpaperUri.isNullOrBlank()) {
            return decodeWallpaper(context, customWallpaperUri)?.asAndroidBitmap()
        }
        val metrics = context.resources.displayMetrics
        return runCatching {
            renderWallpaperToBitmap(Wallpapers.forId(gradientId), metrics.widthPixels, metrics.heightPixels, dark)
        }.getOrNull()
    }
}
