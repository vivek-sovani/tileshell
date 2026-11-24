package com.tileshell.feature.livetiles.health

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Pure pieces of the walk page: climbing from the barometer, pace and time wording, the saved-walk codec. */

/** One finished walk. Distance is worked out from [steps] and the height setting when shown. */
data class Walk(
    val startMs: Long,
    val endMs: Long,
    /** Time actually walking, without pauses. */
    val activeMs: Long,
    val steps: Int,
    val floors: Int,
)

const val METERS_PER_FLOOR = 3.0
/** The pressure reading has to move this far (metres of height) before it counts as a climb, so noise adds nothing. */
const val ASCENT_HYSTERESIS_M = 1.2
const val KEEP_WALKS = 50

/**
 * Adds up how far a walk climbed from a stream of heights in metres (from the barometer). Going down only moves the
 * low point; a climb counts once it is at least [hysteresis] above that low point, and then again in steps of that
 * size, so wobble of a metre or so never adds up.
 */
class AscentTracker(private val hysteresis: Double = ASCENT_HYSTERESIS_M) {
    private var low: Double? = null
    var ascentMeters: Double = 0.0
        private set

    fun add(heightM: Double) {
        val l = low
        if (l == null || heightM < l) {
            low = heightM
        } else if (heightM - l >= hysteresis) {
            ascentMeters += heightM - l
            low = heightM
        }
    }

    /** Forgets the reference point (after a pause), keeping what was counted. */
    fun restart() {
        low = null
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
    walks.joinToString("\n") { "${it.startMs}\t${it.endMs}\t${it.activeMs}\t${it.steps}\t${it.floors}" }

fun decodeWalks(text: String): List<Walk> = text.lineSequence().mapNotNull { line ->
    val f = line.split('\t')
    if (f.size < 5) return@mapNotNull null
    Walk(
        f[0].toLongOrNull() ?: return@mapNotNull null,
        f[1].toLongOrNull() ?: return@mapNotNull null,
        f[2].toLongOrNull() ?: return@mapNotNull null,
        f[3].toIntOrNull() ?: return@mapNotNull null,
        f[4].toIntOrNull() ?: return@mapNotNull null,
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
