package com.tileshell.core.data

/**
 * The tile, icon or row an app is being launched from: its bounds (window
 * coordinates), colour and corner radius. A tap records it ([set]);
 * [AppLauncher] takes it ([take]) for the launch that follows and, once the
 * app has started, tells the Start screen ([onLaunched]) so it can draw the
 * tile growing to fill the screen (and shrinking back when the app is left).
 * The system's own launch animation can't be replaced by a third-party launcher
 * (scale-up, clip-reveal and custom animations were all ignored on the phone,
 * and the task-level custom animation is hidden API), so the animation is
 * drawn by TileShell itself. An origin older than [VALID_MS] is ignored, so a
 * tap that opened something else can't colour a later, unrelated launch.
 */
object LaunchOrigin {
    const val VALID_MS = 1000L

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        fun isEmpty() = width <= 0 || height <= 0
    }

    /** What was tapped: [color] is ARGB (0 = none), [cornerPx] the tile's corner radius. */
    data class Origin(val bounds: Bounds, val color: Int, val cornerPx: Float)

    /** An app that has just been started from [origin]. */
    data class Launched(val origin: Origin, val packageName: String)

    private var pending: Origin? = null
    private var at = 0L

    /** Set by the Start screen while it can draw the animation; called on the thread that launched. */
    @Volatile
    var onLaunched: ((Launched) -> Unit)? = null

    fun set(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int = 0,
        cornerPx: Float = 0f,
        now: Long = System.currentTimeMillis(),
    ) {
        pending = Origin(Bounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()), color, cornerPx)
        at = now
    }

    /** The recorded origin if still fresh, consuming it either way. */
    fun take(now: Long = System.currentTimeMillis()): Origin? {
        val origin = pending
        pending = null
        return origin?.takeIf { now - at <= VALID_MS && !it.bounds.isEmpty() }
    }

    fun notifyLaunched(origin: Origin?, packageName: String) {
        if (origin != null) onLaunched?.invoke(Launched(origin, packageName))
    }
}
