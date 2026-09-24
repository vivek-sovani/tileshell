package com.tileshell.core.data

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The next moonrise and next moonset after a given moment, as epoch millis —
 * each independently null when it doesn't happen within the search window
 * (the Moon rises ~50 min later each day, so roughly once a month one of the
 * two skips a calendar day, but never a whole 48 h at inhabited latitudes).
 */
data class MoonTimesInfo(val moonriseMillis: Long?, val moonsetMillis: Long?)

/**
 * Moonrise/moonset (user-requested, Panchang tile + widget) from the same
 * low-precision Meeus Moon series [HinduPanchang] already uses for the tithi —
 * pure trig, no device API, no network.
 *
 * Unlike the Sun, the Moon moves ~13° a day against the stars, so there is no
 * closed-form "sunrise equation" equivalent worth trusting. Instead this
 * samples the Moon's altitude every [STEP_MINUTES] from `now`, finds where it
 * crosses the standard moonrise altitude [H0_DEG] (upper limb on the horizon,
 * refraction and the Moon's mean horizontal parallax included — Meeus ch. 15),
 * and bisects each crossing to the second. Accurate to a couple of minutes,
 * dominated by the truncated longitude series — plenty for a glanceable tile.
 */
object MoonTimes {

    private const val STEP_MINUTES = 10
    private const val SEARCH_HOURS = 48

    /** 0.7275 × mean parallax (0.9507°) − 0.5667° refraction, Meeus ch. 15. */
    private const val H0_DEG = 0.125

    private fun norm360(deg: Double): Double {
        val m = deg % 360.0
        return if (m < 0) m + 360.0 else m
    }

    /** The Moon's geocentric altitude above the horizon, degrees, at [epochMillis] for [latitude]/[longitude] (east positive). */
    internal fun moonAltitude(epochMillis: Long, latitude: Double, longitude: Double): Double {
        val jd = epochMillis / 86_400_000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val lambda = Math.toRadians(HinduPanchang.moonLongitude(t))
        val beta = Math.toRadians(HinduPanchang.moonLatitude(t))
        val eps = Math.toRadians(23.439291 - 0.0130042 * t)
        val ra = atan2(sin(lambda) * cos(eps) - tan(beta) * sin(eps), cos(lambda))
        val dec = asin(sin(beta) * cos(eps) + cos(beta) * sin(eps) * sin(lambda))
        val gmst = norm360(280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t)
        val hourAngle = Math.toRadians(gmst + longitude) - ra
        val lat = Math.toRadians(latitude)
        return Math.toDegrees(asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(hourAngle)))
    }

    /** The first moonrise and first moonset strictly after [epochMillis], each within [SEARCH_HOURS]. */
    fun nextMoonriseMoonset(epochMillis: Long, latitude: Double, longitude: Double): MoonTimesInfo {
        fun f(ms: Long) = moonAltitude(ms, latitude, longitude) - H0_DEG
        val step = STEP_MINUTES * 60_000L
        var rise: Long? = null
        var set: Long? = null
        var prevT = epochMillis
        var prev = f(prevT)
        val end = epochMillis + SEARCH_HOURS * 3_600_000L
        while (prevT < end && (rise == null || set == null)) {
            val t = prevT + step
            val cur = f(t)
            val rising = prev < 0.0 && cur >= 0.0
            val setting = prev >= 0.0 && cur < 0.0
            if ((rising && rise == null) || (setting && set == null)) {
                var lo = prevT
                var hi = t
                while (hi - lo > 1_000L) {
                    val mid = (lo + hi) / 2
                    if ((f(mid) >= 0.0) == rising) hi = mid else lo = mid
                }
                if (rising) rise = hi else set = hi
            }
            prevT = t
            prev = cur
        }
        return MoonTimesInfo(moonriseMillis = rise, moonsetMillis = set)
    }
}
