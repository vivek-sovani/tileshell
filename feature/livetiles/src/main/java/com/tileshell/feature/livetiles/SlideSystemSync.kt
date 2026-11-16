package com.tileshell.feature.livetiles

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.tileshell.core.data.settings.WallpaperSyncTarget
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pushes a slideshow photo to the real Android wallpaper (home, lock or both,
 * as chosen, both when never chosen), like a single photo is. Slides
 * are centre-cropped to the screen. Best effort: a failure changes nothing.
 * Blocking (decode and write): call off the main thread.
 */
object SlideSystemSync {
    fun apply(context: Context, target: WallpaperSyncTarget, uri: String) {
        val flags = when (target) {
            WallpaperSyncTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            WallpaperSyncTarget.LOCK -> WallpaperManager.FLAG_LOCK
            WallpaperSyncTarget.HOME_AND_LOCK -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            WallpaperSyncTarget.NONE -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK // never chosen: both
        }
        runCatching {
            val m = context.resources.displayMetrics
            val sw = m.widthPixels
            val sh = m.heightPixels
            val resolver = context.contentResolver
            val parsed = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sw && bounds.outHeight / (sample * 2) >= sh) sample *= 2
            val bitmap = resolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return
            // Centre crop to the screen's shape.
            val scale = max(sw.toFloat() / bitmap.width, sh.toFloat() / bitmap.height)
            val cw = (sw / scale).roundToInt().coerceAtMost(bitmap.width)
            val ch = (sh / scale).roundToInt().coerceAtMost(bitmap.height)
            val left = (bitmap.width - cw) / 2
            val top = (bitmap.height - ch) / 2
            WallpaperManager.getInstance(context).setBitmap(bitmap, Rect(left, top, left + cw, top + ch), true, flags)
        }
    }
}
