package com.tileshell.core.data

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/** Local sunrise/sunset for one calendar day, as epoch millis. */
data class SunTimesInfo(val sunriseMillis: Long, val sunsetMillis: Long)

/**
 * Local sunrise/sunset for a given date and location — the "sunrise
 * equation" (low-precision solar-position trig, equivalent to the NOAA solar
 * calculator's simplified form), same idiom as [HinduPanchang]'s own
 * Meeus-based Sun/Moon formulas: pure, no device API, no network, accurate
 * to roughly a minute — plenty for a glanceable tile.
 */
object SunTimes {

    private fun norm360(deg: Double): Double {
        val m = deg % 360.0
        return if (m < 0) m + 360.0 else m
    }

    /** Julian Day Number (Fliegel–Van Flandern) for a Gregorian calendar date, at Greenwich noon. */
    private fun julianDayNumber(year: Int, month1to12: Int, day: Int): Double {
        val a = (14 - month1to12) / 12
        val y = year + 4800 - a
        val m = month1to12 + 12 * a - 3
        return (day + (153 * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045).toDouble()
    }

    /**
     * [latitude]/[longitude] in degrees (east positive, matching
     * [android.location.Location]). Returns null when the sun genuinely
     * doesn't rise or set on this calendar day — polar day/night; never
     * happens at the latitudes this app's userbase sits at, but a real
     * possibility mathematically, so guarded rather than producing a
     * nonsense time.
     */
    fun sunriseSunsetFor(
        epochMillis: Long,
        latitude: Double,
        longitude: Double,
        zone: java.util.TimeZone = java.util.TimeZone.getDefault(),
    ): SunTimesInfo? {
        val cal = java.util.Calendar.getInstance(zone).apply { timeInMillis = epochMillis }
        val jDate = julianDayNumber(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )

        // "lw" is longitude *west* in this formulation (east longitude negated).
        val lw = -longitude
        val j2000 = 2451545.0
        val nStar = jDate - j2000 - 0.0009 - lw / 360.0
        val n = round(nStar)
        val jStar = j2000 + 0.0009 + lw / 360.0 + n

        // Mean anomaly is defined relative to J2000.0 (357.5291° *at* J2000),
        // so the rate must be applied to days *since* J2000 — not the raw
        // Julian date, whose own huge magnitude would swamp the mod-360
        // reduction and silently point at the wrong time of year entirely
        // (caught by this file's own calibration test: a first cut applied
        // the rate directly to `jStar`, and it placed the December solstice
        // at ecliptic longitude ~191° instead of ~270°).
        val mDeg = norm360(357.5291 + 0.98560028 * (jStar - j2000))
        val mRad = Math.toRadians(mDeg)
        val c = 1.9148 * sin(mRad) + 0.0200 * sin(2 * mRad) + 0.0003 * sin(3 * mRad)
        val lambdaDeg = norm360(mDeg + 102.9372 + c + 180.0)
        val lambdaRad = Math.toRadians(lambdaDeg)

        val jTransit = jStar + 0.0053 * sin(mRad) - 0.0069 * sin(2 * lambdaRad)

        val sinDelta = sin(lambdaRad) * sin(Math.toRadians(23.4397))
        val delta = asin(sinDelta)
        val latRad = Math.toRadians(latitude)
        val cosH0 = (sin(Math.toRadians(-0.833)) - sin(latRad) * sinDelta) / (cos(latRad) * cos(delta))
        if (cosH0 < -1.0 || cosH0 > 1.0) return null
        val h0Deg = Math.toDegrees(acos(cosH0))

        val jSet = jTransit + h0Deg / 360.0
        val jRise = jTransit - (jSet - jTransit)

        fun toEpochMillis(jd: Double) = ((jd - 2440587.5) * 86_400_000.0).toLong()
        return SunTimesInfo(sunriseMillis = toEpochMillis(jRise), sunsetMillis = toEpochMillis(jSet))
    }
}
