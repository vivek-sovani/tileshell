package com.tileshell.core.data

import android.content.Context

/**
 * Caches the most recent Start-screen screenshot captured while Start was actually
 * visible (foreground), so the headless auto-backup worker can reuse it. PixelCopy
 * can only read from a live, on-screen window, so a screenshot can never be taken
 * from within the WorkManager job itself — this cache is the bridge between "last
 * time we had a real window" and "now, when we don't."
 *
 * [contentHash] lets a reader confirm the cached image still matches the current
 * layout before reusing it; a stale screenshot from a since-changed layout is
 * worse than no screenshot, so callers should treat a hash mismatch as a miss.
 */
object CachedScreenshotPrefs {
    private const val PREFS = "tileshell.prefs"
    private const val KEY_PATH = "cached_screenshot_path"
    private const val KEY_HASH = "cached_screenshot_hash"
    private const val KEY_LAST_ATTEMPT = "cached_screenshot_last_attempt_ms"

    fun save(context: Context, path: String, contentHash: String) {
        prefs(context).edit()
            .putString(KEY_PATH, path)
            .putString(KEY_HASH, contentHash)
            .apply()
    }

    /** Returns the cached screenshot path only if it still matches [currentHash]. */
    fun pathFor(context: Context, currentHash: String): String? {
        val p = prefs(context)
        if (p.getString(KEY_HASH, null) != currentHash) return null
        return p.getString(KEY_PATH, null)
    }

    /** The current cached path regardless of hash — used to clean up a superseded file. */
    fun currentPath(context: Context): String? = prefs(context).getString(KEY_PATH, null)

    /**
     * Throttles capture attempts to at most once per [minIntervalMs]. The caller (Start
     * backgrounding) fires on every app switch, and a PixelCopy + JPEG encode is wasteful
     * to repeat that often. Returns true — and records the attempt — only once the
     * interval has elapsed since the last attempt.
     */
    fun claimAttempt(context: Context, minIntervalMs: Long): Boolean {
        val p = prefs(context)
        val last = p.getLong(KEY_LAST_ATTEMPT, 0L)
        val now = System.currentTimeMillis()
        if (now - last < minIntervalMs) return false
        p.edit().putLong(KEY_LAST_ATTEMPT, now).apply()
        return true
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
