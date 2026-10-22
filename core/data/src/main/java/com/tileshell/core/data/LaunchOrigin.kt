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
     * The launch options for an app window that grows from roughly where
     * [bounds] is, or null when [context] has no activity window to size it
     * against. A custom window animation (the pivot is the nearest of a 3 x 4
     * grid of `launch_enter_RC` resources, as animation resources can't take
     * a runtime pivot) because the system's scale-up request is ignored by
     * some makers' system UI; a custom one is honoured everywhere. The
     * icon-to-window morph of the system launchers needs system-only remote
     * animations a third-party launcher can't have.
     */
    fun optionsFor(context: Context, bounds: Bounds?): Bundle? {
        if (bounds == null) return null
        val view = activityOf(context)?.window?.decorView ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        val (row, column) = pivotCell(bounds, view.width, view.height)
        return runCatching {
            ActivityOptions.makeCustomAnimation(context, enterAnimation(row, column), R.anim.launch_exit).toBundle()
        }.getOrNull()
    }

    /** The row (0..3) and column (0..2) of the pivot grid nearest the centre of [bounds] on a [width] x [height] screen. */
    fun pivotCell(bounds: Bounds, width: Int, height: Int): Pair<Int, Int> {
        val x = (bounds.left + bounds.right) / 2f / width
        val y = (bounds.top + bounds.bottom) / 2f / height
        val column = (x * COLUMNS).toInt().coerceIn(0, COLUMNS - 1)
        val row = (y * ROWS).toInt().coerceIn(0, ROWS - 1)
        return row to column
    }

    private const val COLUMNS = 3
    private const val ROWS = 4

    private fun enterAnimation(row: Int, column: Int): Int = when (row * COLUMNS + column) {
        0 -> R.anim.launch_enter_00
        1 -> R.anim.launch_enter_01
        2 -> R.anim.launch_enter_02
        3 -> R.anim.launch_enter_10
        4 -> R.anim.launch_enter_11
        5 -> R.anim.launch_enter_12
        6 -> R.anim.launch_enter_20
        7 -> R.anim.launch_enter_21
        8 -> R.anim.launch_enter_22
        9 -> R.anim.launch_enter_30
        10 -> R.anim.launch_enter_31
        else -> R.anim.launch_enter_32
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
