package com.tileshell.feature.livetiles.health

import java.io.File
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One recorded point of a walk's route. [newSegment]: the line does not join the point before (first fix, after a pause). */
data class RoutePoint(val lat: Double, val lon: Double, val timeMs: Long, val newSegment: Boolean = false)

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = p2 - p1
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Keeps the usable GPS fixes of a walk: poor fixes (accuracy worse than [maxAccuracy] m) are dropped, a point is kept
 * only once it is [minStep] m from the last one (standing still adds nothing), and a jump faster than [maxSpeed] m/s
 * is a glitch and dropped (unless it repeats: then the old point is what was wrong). [meters] is the length of the
 * kept route, not counting the gap over a pause.
 */
class RouteTracker(
    private val maxAccuracy: Float = 25f,
    private val minStep: Double = 4.0,
    private val maxSpeed: Double = 12.0,
) {
    val points = ArrayList<RoutePoint>()
    var meters: Double = 0.0
        private set
    private var last: RoutePoint? = null
    private var rejected = 0

    /** Next fix starts a new line (after a pause). */
    fun breakSegment() {
        last = null
        rejected = 0
    }

    /** True when the fix became a route point. */
    fun add(lat: Double, lon: Double, accuracy: Float, timeMs: Long): Boolean {
        if (accuracy > maxAccuracy) return false
        val l = last
        if (l == null) {
            val p = RoutePoint(lat, lon, timeMs, newSegment = true)
            points += p
            last = p
            rejected = 0
            return true
        }
        val d = distanceMeters(l.lat, l.lon, lat, lon)
        if (d < minStep) return false
        val dt = (timeMs - l.timeMs) / 1000.0
        if (dt > 0 && d / dt > maxSpeed) {
            if (++rejected < 5) return false
            val p = RoutePoint(lat, lon, timeMs, newSegment = true)
            points += p
            last = p
            rejected = 0
            return true
        }
        val p = RoutePoint(lat, lon, timeMs)
        points += p
        last = p
        meters += d
        rejected = 0
        return true
    }
}

fun encodeRoute(points: List<RoutePoint>): String =
    points.joinToString("\n") { String.format(Locale.US, "%d\t%.5f\t%.5f\t%d", it.timeMs, it.lat, it.lon, if (it.newSegment) 1 else 0) }

fun decodeRoute(text: String): List<RoutePoint> = text.lineSequence().mapNotNull { line ->
    val f = line.split('\t')
    if (f.size < 3) return@mapNotNull null
    RoutePoint(
        f[1].toDoubleOrNull() ?: return@mapNotNull null,
        f[2].toDoubleOrNull() ?: return@mapNotNull null,
        f[0].toLongOrNull() ?: return@mapNotNull null,
        f.getOrNull(3) == "1",
    )
}.toList()

/** The route points where each whole [intervalM] of route length was reached. */
fun routeMarks(points: List<RoutePoint>, intervalM: Double): List<RoutePoint> {
    val out = ArrayList<RoutePoint>()
    var total = 0.0
    var next = intervalM
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        if (b.newSegment) continue
        total += distanceMeters(a.lat, a.lon, b.lat, b.lon)
        while (total >= next) {
            out += b
            next += intervalM
        }
    }
    return out
}

/** Route points as x / y in a [width] x [height] box with [pad] around, north up, longitude scaled by latitude. */
fun projectRoute(points: List<RoutePoint>, width: Float, height: Float, pad: Float): List<Pair<Float, Float>> {
    if (points.isEmpty()) return emptyList()
    val lat0 = points.map { it.lat }.average()
    val k = cos(Math.toRadians(lat0))
    val xs = points.map { it.lon * k }
    val ys = points.map { -it.lat }
    val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
    val w = max(maxX - minX, 1e-6)
    val h = max(maxY - minY, 1e-6)
    val scale = min((width - 2 * pad) / w, (height - 2 * pad) / h)
    val ox = pad + ((width - 2 * pad) - w * scale) / 2
    val oy = pad + ((height - 2 * pad) - h * scale) / 2
    return points.indices.map { i -> ((ox + (xs[i] - minX) * scale).toFloat()) to ((oy + (ys[i] - minY) * scale).toFloat()) }
}

internal fun routeFile(filesDir: File, startMs: Long) = File(filesDir, "walk_$startMs.route")
