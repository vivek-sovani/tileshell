package com.tileshell.feature.keyboard

/** A key's bounds in pixels, inside the key area. */
data class KeyBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** Squared distance from a point to this box (0 inside). */
    fun distance2(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return dx * dx + dy * dy
    }
}

/**
 * Where every key sits, worked out from the layout itself — the same sums the
 * rows' weights make (each row: gaps first, the rest shared by key units; row 2
 * inset by half a unit and half a gap). The touch handler hit-tests with this
 * rather than with positions reported after layout, which went stale when the
 * rows changed without the keys moving (the globe key appearing, &123): touches
 * on unchanged rows then found no key. Pure, so it's unit-tested.
 */
object KeyGeometry {

    fun layout(
        rows: List<KeyRow>,
        width: Float,
        keyHeight: Float,
        rowGap: Float,
        keyGap: Float,
    ): List<List<KeyBox>> {
        val unit = (width - keyGap * 9) / 10
        return rows.mapIndexed { r, row ->
            val inset = if (row.inset) (unit + keyGap) / 2 else 0f
            val inner = width - 2 * inset
            val space = inner - keyGap * (row.keys.size - 1)
            val perUnit = space / row.units
            val top = r * (keyHeight + rowGap)
            var x = inset
            row.keys.map { key ->
                val w = key.units * perUnit
                KeyBox(x, top, x + w, top + keyHeight).also { x += w + keyGap }
            }
        }
    }

    /** The key under (x, y), or the nearest one for a touch between keys: (row, index). */
    fun hit(boxes: List<List<KeyBox>>, x: Float, y: Float): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestDist = Float.MAX_VALUE
        boxes.forEachIndexed { r, row ->
            row.forEachIndexed { i, b ->
                val d = b.distance2(x, y)
                if (d < bestDist) {
                    bestDist = d
                    best = r to i
                }
            }
        }
        return best
    }
}
