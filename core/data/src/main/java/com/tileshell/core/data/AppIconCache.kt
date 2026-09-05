package com.tileshell.core.data

import android.graphics.Bitmap
import android.util.LruCache

/**
 * One process-wide cache of decoded app-icon bitmaps, shared by every icon
 * loader in the app.
 *
 * Before this there was no icon cache of any kind — not an [LruCache], not a
 * memo, nothing. Six near-duplicate loaders (`:feature:start`'s
 * `rememberTileAppIcon` and `rememberMaskableIcon`, `:feature:applist`'s
 * `rememberMaskableAppIcon`, `:feature:livetiles`' `rememberAppIconBitmap` and
 * its own `rememberMaskableAppIcon`, plus the edge-strip and hidden-apps
 * sheets) each decoded the same package's icon independently and kept nothing.
 * Every scroll-recycle in the app list or the Start grid destroyed the
 * `produceState` and re-ran `getActivityIcon` → `toBitmap` from scratch, and
 * for shortcut tiles a `LauncherApps.getShortcuts` IPC on top of that.
 *
 * Sized in **bytes**, not entries: a cache counted in entries is an OOM risk
 * here because the decode resolution is not fixed — a "show as icon" tile
 * stretched past 120dp decodes at ~360px (≈518 KB) while an app-list row
 * decodes at 96px (≈36 KB), a 14× spread.
 *
 * Keys must include the decode size as well as the component, since the same
 * icon is legitimately decoded at several resolutions ([iconCacheKey]).
 *
 * Values are treated as immutable by every caller — they are wrapped in a
 * Compose `ImageBitmap` and only ever drawn — so sharing one instance across
 * callers and threads is safe. [LruCache] is itself synchronized.
 */
object AppIconCache {

    /**
     * Roughly 1/16th of a typical app heap. Small next to the wallpaper and
     * widget bitmaps this app already holds, and enough for a few hundred
     * app-list icons at 96px.
     */
    private val maxBytes: Int =
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceIn(2 * 1024, 16 * 1024)

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        // Unit is KB, matching maxBytes, so the arithmetic stays in Int range
        // for even a very large bitmap.
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    /** Cache key for one component at one decode size. */
    fun iconCacheKey(packageName: String, activityName: String, sizePx: Int): String =
        "$packageName/$activityName@$sizePx"

    operator fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /**
     * Returns the cached bitmap for [key], or computes, stores and returns one.
     * [compute] runs only on a miss, and a null result is not cached — a failed
     * decode should be retried rather than remembered as "no icon".
     */
    inline fun getOrPut(key: String, compute: () -> Bitmap?): Bitmap? =
        get(key) ?: compute()?.also { put(key, it) }

    /**
     * Drops everything. Called when packages change, since an app update can
     * legitimately change its icon and a stale entry would outlive it.
     */
    fun clear() {
        cache.evictAll()
    }
}
