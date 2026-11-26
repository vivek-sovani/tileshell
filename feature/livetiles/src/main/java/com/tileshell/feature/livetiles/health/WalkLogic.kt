package com.tileshell.feature.livetiles.health

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Pure pieces of the walk page: climbing from the barometer, pace and time wording, the saved-walk codec. */

/**
 * One finished walk. [meters] is the distance measured (GPS when the route was recorded, else the step estimate);
 * 0 on walks saved before it was kept, which fall back to the step estimate. [splits] are the walking times at each
 * full kilometre (mile) mark; [hasRoute] says a route file was saved.
 */
data class Walk(
    val startMs: Long,
    val endMs: Long,
    /** Time actually walking, without pauses. */
    val activeMs: Long,
    val steps: Int,
    val floors: Int,
    val meters: Int = 0,
    val splits: List<Long> = emptyList(),
    val hasRoute: Boolean = false,
)

/** The distance of a saved walk in km. */
fun walkKm(w: Walk, heightCm: Int): Double = if (w.meters > 0) w.meters / 1000.0 else distanceKm(w.steps, heightCm)

/** Pace for display, per km or per mile following the units setting. */
fun paceText(activeMs: Long, km: Double, imperial: Boolean): String? =
    paceLabel(activeMs, if (imperial) km * 0.621371 else km)?.let { "$it ${if (imperial) "/mi" else "/km"}" }

const val METERS_PER_FLOOR = 3.0
/** The smoothed height has to rise this far (metres) above its low point before it counts as a climb. */
const val ASCENT_HYSTERESIS_M = 2.0
/** Weight of each new pressure reading in the smoothed height (readings come a few times a second). */
const val ASCENT_SMOOTHING = 0.2
const val KEEP_WALKS = 50

/**
 * Adds up how far a walk climbed from a stream of heights in metres (from the barometer). The readings are smoothed
 * first (a phone's pressure sensor jitters by about a metre, which used to be added up as climbing), going down only
 * moves the low point, and a climb counts once the smoothed height is at least [hysteresis] above that low point.
 * Heights while the walker is not stepping ([moving] false: a lift, a closing door, the weather) count for nothing and
 * only re-set the reference.
 */
class AscentTracker(private val hysteresis: Double = ASCENT_HYSTERESIS_M, private val smoothing: Double = ASCENT_SMOOTHING) {
    private var low: Double? = null
    private var smooth: Double? = null
    var ascentMeters: Double = 0.0
        private set

    fun add(heightM: Double, moving: Boolean = true) {
        val prev = smooth
        val h = if (prev == null) heightM else prev + smoothing * (heightM - prev)
        smooth = h
        if (!moving) {
            low = h
            return
        }
        val l = low
        if (l == null || h < l) {
            low = h
        } else if (h - l >= hysteresis) {
            ascentMeters += h - l
            low = h
        }
    }

    /** Forgets the reference point (after a pause), keeping what was counted. */
    fun restart() {
        low = null
        smooth = null
    }

    val floors: Int get() = (ascentMeters / METERS_PER_FLOOR).toInt()
}

/** Height in metres from air pressure in hPa (the standard-atmosphere formula the platform uses). */
fun heightFromPressure(hPa: Float): Double = 44330.0 * (1.0 - Math.pow(hPa / 1013.25, 1.0 / 5.255))

/** "h:mm:ss" from an hour on, else "m:ss". */
fun clockDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

/** "1 h 05 min", "24 min", "40 s" for lists. */
fun spokenDuration(ms: Long): String {
    val m = ms / 60_000
    return when {
        m >= 60 -> String.format(Locale.US, "%d h %02d min", m / 60, m % 60)
        m >= 1 -> "$m min"
        else -> "${(ms / 1000).coerceAtLeast(0)} s"
    }
}

/** Minutes per kilometre as "m:ss", or null when there is not enough distance or time to say. */
fun paceLabel(activeMs: Long, km: Double): String? {
    if (km < 0.05 || activeMs < 10_000) return null
    val secPerKm = (activeMs / 1000.0 / km).toLong()
    if (secPerKm > 3600) return null
    return String.format(Locale.US, "%d:%02d", secPerKm / 60, secPerKm % 60)
}

/** Steps a minute, or null before there is a minute of walking to go on. */
fun stepsPerMinute(steps: Int, activeMs: Long): Int? =
    if (activeMs < 30_000) null else (steps * 60_000.0 / activeMs).toInt()

/** The local hours a walk overlapped, as hour numbers ([hourIndex]). */
fun walkHours(walk: Walk, zone: ZoneId = ZoneId.systemDefault()): List<Long> =
    (hourIndex(walk.startMs, zone)..hourIndex(maxOf(walk.endMs, walk.startMs), zone)).toList()

/** Hours of [epochDay] (0..23) in which a walk took place. */
fun walkHoursOfDay(walks: List<Walk>, epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Set<Int> =
    walks.flatMap { walkHours(it, zone) }.filter { it / 24 == epochDay }.map { (it % 24).toInt() }.toSet()

/** Newest first, [KEEP_WALKS] at most. */
fun addWalk(walks: List<Walk>, walk: Walk): List<Walk> = (walks + walk).sortedByDescending { it.startMs }.take(KEEP_WALKS)

fun encodeWalks(walks: List<Walk>): String =
    walks.joinToString("\n") {
        "${it.startMs}\t${it.endMs}\t${it.activeMs}\t${it.steps}\t${it.floors}\t${it.meters}\t${it.splits.joinToString(",")}\t${if (it.hasRoute) 1 else 0}"
    }

fun decodeWalks(text: String): List<Walk> = text.lineSequence().mapNotNull { line ->
    val f = line.split('\t')
    if (f.size < 5) return@mapNotNull null
    Walk(
        f[0].toLongOrNull() ?: return@mapNotNull null,
        f[1].toLongOrNull() ?: return@mapNotNull null,
        f[2].toLongOrNull() ?: return@mapNotNull null,
        f[3].toIntOrNull() ?: return@mapNotNull null,
        f[4].toIntOrNull() ?: return@mapNotNull null,
        f.getOrNull(5)?.toIntOrNull() ?: 0,
        f.getOrNull(6)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty(),
        f.getOrNull(7) == "1",
    )
}.toList().sortedByDescending { it.startMs }.take(KEEP_WALKS)

/** "mon 24 nov · 7:15 am" for a list row. */
fun walkWhenLabel(startMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(startMs).atZone(zone)
    val day = t.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).lowercase()
    val month = t.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).lowercase()
    val h = if (t.hour % 12 == 0) 12 else t.hour % 12
    return String.format(Locale.US, "%s %d %s · %d:%02d %s", day, t.dayOfMonth, month, h, t.minute, if (t.hour < 12) "am" else "pm")
}

/** Words for the voice: "9 minutes 12 seconds", "45 seconds", "1 hour 5 minutes". */
fun spokenWords(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val sec = total % 60
    fun u(n: Long, one: String) = "$n $one${if (n == 1L) "" else "s"}"
    return when {
        h > 0 -> u(h, "hour") + if (m > 0) " " + u(m, "minute") else ""
        m > 0 -> u(m, "minute") + if (sec > 0) " " + u(sec, "second") else ""
        else -> u(sec, "second")
    }
}

/** What is said at the [n]th kilometre (mile) mark: this one's time, and the total from the second on. */
fun kmAnnouncement(n: Int, splitMs: Long, totalMs: Long, miles: Boolean): String {
    val unit = if (miles) "mile" else "kilometre"
    return if (n == 1) "1 $unit in ${spokenWords(splitMs)}"
    else "$n ${unit}s. ${spokenWords(splitMs)} for the last one, ${spokenWords(totalMs)} in all"
}

/** The time of each full kilometre (mile) from the walking times at the marks. */
fun splitTimes(splits: List<Long>): List<Long> = splits.mapIndexed { i, t -> t - (splits.getOrNull(i - 1) ?: 0L) }
