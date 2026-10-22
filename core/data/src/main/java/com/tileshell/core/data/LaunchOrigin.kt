package com.tileshell.core.data

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle

/**
 * Where the app about to be launched should appear to grow from: the bounds
 * (window coordinates) of the tile, icon or row that was tapped. The tap sets
 * it ([set]); [AppLauncher] takes it ([take]) for the launch that follows, and
 * an origin older than [VALID_MS] is ignored, so a tap that opened something
 * else can't colour a later, unrelated launch.
 */
object LaunchOrigin {
    const val VALID_MS = 1000L

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        fun isEmpty() = width <= 0 || height <= 0
    }

    private var pending: Bounds? = null
    private var at = 0L

    fun set(left: Float, top: Float, right: Float, bottom: Float, now: Long = System.currentTimeMillis()) {
        pending = Bounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        at = now
    }

    /** The recorded bounds if still fresh, consuming them either way. */
    fun take(now: Long = System.currentTimeMillis()): Bounds? {
        val bounds = pending
        pending = null
        return bounds?.takeIf { now - at <= VALID_MS && !it.isEmpty() }
    }

    /**
     * The launch options for an app window that scales up from [bounds], or
     * null when [context] has no activity window to anchor to. Scale-up (the
     * app's window grows from the tile, with its own starting window) is what a
     * third-party home app can ask for; the icon-to-window morph of the
     * system launchers needs system-only remote animations.
     */
    fun optionsFor(context: Context, bounds: Bounds?): Bundle? {
        if (bounds == null) return null
        val view = activityOf(context)?.window?.decorView ?: return null
        return runCatching {
            ActivityOptions.makeScaleUpAnimation(view, bounds.left, bounds.top, bounds.width, bounds.height).toBundle()
        }.getOrNull()
    }

    private fun activityOf(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
