package com.tileshell.feature.livetiles.health

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan
import kotlin.math.cos

/** Web Mercator in 0..1 (x east, y south), the frame OpenStreetMap tiles are cut in. */
fun mercatorX(lon: Double): Double = (lon + 180.0) / 360.0
fun mercatorY(lat: Double): Double {
    val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0
}

data class TileKey(val z: Int, val x: Int, val y: Int)

/**
 * A map window fitted round a route: [scale] pixels per mercator unit, [originX] / [originY] the mercator point at the
 * canvas's top-left, [zoom] the tile level whose tiles are drawn (stretched by up to 2x to fit).
 */
class MapFit(val zoom: Int, val scale: Double, val originX: Double, val originY: Double, val width: Float, val height: Float) {
    fun project(lat: Double, lon: Double): Pair<Float, Float> =
        ((mercatorX(lon) - originX) * scale).toFloat() to ((mercatorY(lat) - originY) * scale).toFloat()

    /** The tiles that cover the window. */
    fun tiles(): List<TileKey> {
        val n = 1 shl zoom
        val x0 = floor(originX * n).toInt().coerceIn(0, n - 1)
        val x1 = floor((originX + width / scale) * n).toInt().coerceIn(0, n - 1)
        val y0 = floor(originY * n).toInt().coerceIn(0, n - 1)
        val y1 = floor((originY + height / scale) * n).toInt().coerceIn(0, n - 1)
        return buildList { for (y in y0..y1) for (x in x0..x1) add(TileKey(zoom, x, y)) }
    }

    /** Where a tile lands on the canvas: left, top, side in pixels. */
    fun place(t: TileKey): Triple<Float, Float, Float> {
        val n = (1 shl t.z).toDouble()
        return Triple(((t.x / n - originX) * scale).toFloat(), ((t.y / n - originY) * scale).toFloat(), (scale / n).toFloat())
    }
}

/** Fits [points] into a [width] x [height] window with [pad] round it, north up; null for no points. */
fun fitMap(points: List<RoutePoint>, width: Float, height: Float, pad: Float): MapFit? {
    if (points.isEmpty()) return null
    val xs = points.map { mercatorX(it.lon) }
    val ys = points.map { mercatorY(it.lat) }
    val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
    val dx = max(maxX - minX, 1e-7)
    val dy = max(maxY - minY, 1e-7)
    var scale = min((width - 2 * pad) / dx, (height - 2 * pad) / dy)
    var zoom = floor(log2(scale / 256.0)).toInt().coerceIn(3, 17)
    // Stay under a handful of tiles however the route is shaped: a window is at most ~(w/256+2) x (h/256+2).
    scale = min(scale, 256.0 * 2.0.pow(zoom + 1) * 0.999)
    val cx = (minX + maxX) / 2
    val cy = (minY + maxY) / 2
    return MapFit(zoom, scale, cx - width / scale / 2, cy - height / scale / 2, width, height)
}

/** OpenStreetMap tiles, cached on disk (the cache folder the system may clear). Needs the network for tiles not yet cached. */
object MapTiles {
    private const val MAX_TILES = 20

    fun load(context: Context, keys: List<TileKey>): Map<TileKey, Bitmap> {
        val dir = File(context.applicationContext.cacheDir, "maptiles").apply { mkdirs() }
        val out = HashMap<TileKey, Bitmap>()
        keys.take(MAX_TILES).forEach { k ->
            val f = File(dir, "${k.z}_${k.x}_${k.y}.png")
            var bmp = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() else null
            if (bmp == null) {
                runCatching {
                    val c = (URL("https://tile.openstreetmap.org/${k.z}/${k.x}/${k.y}.png").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        setRequestProperty("User-Agent", "TileShell-launcher/1.0 (walk route map)")
                    }
                    if (c.responseCode == 200) {
                        val bytes = c.inputStream.use { it.readBytes() }
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) f.writeBytes(bytes)
                    }
                    c.disconnect()
                }
            }
            bmp?.let { out[k] = it }
        }
        // Keep the cache modest: drop the oldest files past 300 tiles.
        runCatching { dir.listFiles()?.sortedBy { it.lastModified() }?.let { l -> if (l.size > 300) l.take(l.size - 300).forEach { it.delete() } } }
        return out
    }
}
