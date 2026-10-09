package com.tileshell.feature.start

/** A button in the bar above a selected tile (the "bar above" edit controls). */
internal enum class BarAction(val label: String) {
    UNPIN("unpin"),
    OPEN_FOLDER("open"),
    COLOUR("colour"),
    SIZE("size"),
}

/** The bar's buttons, left to right: a folder opens, any other tile unpins; then colour and size. Pure. */
internal fun barActionsFor(isFolder: Boolean): List<BarAction> =
    listOf(if (isFolder) BarAction.OPEN_FOLDER else BarAction.UNPIN, BarAction.COLOUR, BarAction.SIZE)

internal data class BarRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

/**
 * Where the bar sits for a tile at ([tileLeft], [tileTop])..([tileRight], [tileBottom]) in a grid [gridWidth] wide:
 * centred over the tile but kept inside the grid, [gap] above it, or [gap] below it when the tile is so near the top
 * that there is no room above (the first row). Pure: the gesture that handles the taps and the composable that draws
 * the bar both use it, so they always agree.
 */
internal fun controlBarRect(
    tileLeft: Float,
    tileTop: Float,
    tileRight: Float,
    tileBottom: Float,
    gridWidth: Float,
    barWidth: Float,
    barHeight: Float,
    gap: Float,
): BarRect {
    val centre = (tileLeft + tileRight) / 2f
    val left = (centre - barWidth / 2f).coerceIn(0f, maxOf(0f, gridWidth - barWidth))
    val above = tileTop - gap - barHeight
    val top = if (above >= 0f) above else tileBottom + gap
    return BarRect(left, top, left + barWidth, top + barHeight)
}

/** Which of [count] equal buttons across [rect] the point [x] falls on. Pure. */
internal fun barButtonIndexAt(x: Float, rect: BarRect, count: Int): Int =
    (((x - rect.left) / (rect.width / count)).toInt()).coerceIn(0, count - 1)
