package com.tileshell.feature.start

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import com.tileshell.core.data.settings.WallpaperSyncTarget
import com.tileshell.core.design.Wallpapers
import com.tileshell.core.design.renderWallpaperToBitmap
import kotlin.math.roundToInt

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
     * decoded the same downsampled way the in-app background already is —
     * and, since the decoded photo is usually bigger than the screen and
     * the user has their own chosen [alignX]/[alignY]/[zoom] framing for it
     * (see [wallpaperCropGeometry], the exact same math the in-app renderer
     * uses), passed to `WallpaperManager` as a `visibleCropHint` so the
     * pushed wallpaper is framed identically, not just centre-cropped by
     * the OS's own default) when set, else the bundled [gradientId]
     * rasterized to a bitmap already at the device's own screen size (no
     * crop hint needed — it already exactly fills the box). A no-op for
     * [WallpaperSyncTarget.NONE] or if the bitmap can't be produced;
     * failures are swallowed — this is a best-effort sync, never something
     * a settings change should crash on.
     */
    fun apply(
        context: Context,
        target: WallpaperSyncTarget,
        gradientId: String,
        customWallpaperUri: String?,
        alignX: Float,
        alignY: Float,
        zoom: Float,
        dark: Boolean,
    ) {
        val flags = when (target) {
            WallpaperSyncTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperSyncTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperSyncTarget.HOME_AND_LOCK -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            WallpaperSyncTarget.NONE -> return
        }
        val metrics = context.resources.displayMetrics
        val boxWidth = metrics.widthPixels.toFloat()
        val boxHeight = metrics.heightPixels.toFloat()

        var cropHint: Rect? = null
        val bitmap: Bitmap = if (!customWallpaperUri.isNullOrBlank()) {
            val decoded = decodeWallpaper(context, customWallpaperUri)?.asAndroidBitmap() ?: return
            cropHint = visibleCropHint(decoded, boxWidth, boxHeight, alignX, alignY, zoom)
            decoded
        } else {
            runCatching {
                renderWallpaperToBitmap(Wallpapers.forId(gradientId), boxWidth.roundToInt(), boxHeight.roundToInt(), dark)
            }.getOrNull() ?: return
        }
        runCatching { WallpaperManager.getInstance(context).setBitmap(bitmap, cropHint, true, flags) }
    }

    /**
     * The region of [bitmap] (in its own pixel coordinates) that
     * [wallpaperCropGeometry]'s alignment/zoom would show inside a
     * [boxWidth]×[boxHeight] box — the inverse of that function's draw
     * geometry, converting "where the scaled image sits relative to the
     * box" back into "which slice of the source image is visible."
     */
    private fun visibleCropHint(
        bitmap: Bitmap,
        boxWidth: Float,
        boxHeight: Float,
        alignX: Float,
        alignY: Float,
        zoom: Float,
    ): Rect {
        val crop = wallpaperCropGeometry(
            bitmap.width.toFloat(), bitmap.height.toFloat(), boxWidth, boxHeight, alignX, alignY, zoom,
        )
        val left = (-crop.left / crop.scale).coerceIn(0f, bitmap.width.toFloat())
        val top = (-crop.top / crop.scale).coerceIn(0f, bitmap.height.toFloat())
        val right = (left + boxWidth / crop.scale).coerceIn(0f, bitmap.width.toFloat())
        val bottom = (top + boxHeight / crop.scale).coerceIn(0f, bitmap.height.toFloat())
        return Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
    }
}
