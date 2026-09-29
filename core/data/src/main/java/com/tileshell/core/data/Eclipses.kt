package com.tileshell.core.data

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A grahan: its type worldwide, and what it looks like from one place. */
data class Eclipse(
    val solar: Boolean,
    val kind: Kind,
    /** Greatest eclipse (geocentric), epoch millis. */
    val maxMillis: Long,
    /** Seen from the given place: first/last contact while it's in the sky, or null when it isn't visible there. */
    val visibleStart: Long?,
    val visibleEnd: Long?,
    /** What it looks like there: total/annular only when that phase reaches this place. */
    val localKind: Kind?,
) {
    enum class Kind { TOTAL, ANNULAR, HYBRID, PARTIAL }

    val visible: Boolean get() = visibleStart != null
}

/**
 * Solar and lunar eclipses (grahan) — Meeus, *Astronomical Algorithms*,
 * ch. 54 for when and what kind; for a solar eclipse the local view comes
 * from the topocentric Sun–Moon separation sampled every 2 minutes, for a
 * lunar one from the Moon being above the horizon during the umbral phase.
 * Penumbral lunar eclipses aren't counted (panchangs don't observe them).
 * Pure, unit-tested.
 */
object Eclipses {
    private const val LUNATION = 29.530588861

    /** Eclipses whose greatest phase falls in [fromMillis, toMillis), as seen from [latitude]/[longitude]. */
    fun between(fromMillis: Long, toMillis: Long, latitude: Double, longitude: Double): List<Eclipse> {
        val kStart = floor((julian(fromMillis) - 2451550.09766) / LUNATION) - 1
        val kEnd = floor((julian(toMillis) - 2451550.09766) / LUNATION) + 1
        val out = mutableListOf<Eclipse>()
        var k = kStart
        while (k <= kEnd) {
            listOf(k, k + 0.5).forEach { kk ->
                val e = eclipseAt(kk, latitude, longitude)
                if (e != null && e.maxMillis in fromMillis until toMillis) out += e
            }
            k += 1.0
        }
        return out.sortedBy { it.maxMillis }
    }

    private fun eclipseAt(k: Double, latitude: Double, longitude: Double): Eclipse? {
        val solar = k == floor(k)
        val t = k / 1236.85
        val jde = 2451550.09766 + LUNATION * k + 0.00015437 * t * t - 0.000000150 * t * t * t
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        val m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t * t)
        val mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t * t)
        val f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t * t)
        val om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t * t)
        val f1 = f - rad(0.02665) * sin(om)
        val a1 = rad(299.77 + 0.107408 * k - 0.009173 * t * t)
        if (abs(sin(f1)) > 0.36) return null

        var jd = jde + (if (solar) -0.4075 * sin(mp) + 0.1721 * e * sin(m) else -0.4065 * sin(mp) + 0.1727 * e * sin(m)) +
            0.0161 * sin(2 * mp) - 0.0097 * sin(2 * f1) + 0.0073 * e * sin(mp - m) - 0.0050 * e * sin(mp + m) -
            0.0023 * sin(mp - 2 * f1) + 0.0021 * e * sin(2 * m) + 0.0012 * sin(mp + 2 * f1) + 0.0006 * e * sin(2 * mp + m) -
            0.0004 * sin(3 * mp) - 0.0003 * e * sin(m + 2 * f1) + 0.0003 * sin(a1) - 0.0002 * e * sin(m - 2 * f1) -
            0.0002 * e * sin(2 * mp - m) - 0.0002 * sin(om)
        jd -= 69.0 / 86400.0 // TT → UT
        val p = 0.2070 * e * sin(m) + 0.0024 * e * sin(2 * m) - 0.0392 * sin(mp) + 0.0116 * sin(2 * mp) -
            0.0073 * e * sin(mp + m) + 0.0067 * e * sin(mp - m) + 0.0118 * sin(2 * f1)
        val q = 5.2207 - 0.0048 * e * cos(m) + 0.0020 * e * cos(2 * m) - 0.3299 * cos(mp) -
            0.0060 * e * cos(mp + m) + 0.0041 * e * cos(mp - m)
        val w = abs(cos(f1))
        val gamma = (p * cos(f1) + q * sin(f1)) * (1 - 0.0048 * w)
        val u = 0.0059 + 0.0046 * e * cos(m) - 0.0182 * cos(mp) + 0.0004 * cos(2 * mp) - 0.0005 * cos(m + mp)
        val maxMillis = millis(jd)
        val g = abs(gamma)

        return if (solar) {
            if (g > 1.5433 + u) return null
            val kind = when {
                g >= 0.9972 -> Eclipse.Kind.PARTIAL
                u < 0 -> Eclipse.Kind.TOTAL
                u > 0.0047 -> Eclipse.Kind.ANNULAR
                u < 0.00464 * sqrt(1 - g * g) -> Eclipse.Kind.HYBRID
                else -> Eclipse.Kind.ANNULAR
            }
            localSolar(maxMillis, kind, latitude, longitude)
        } else {
            val umbral = (1.0128 - u - g) / 0.5450
            if (umbral <= 0) return null
            val kind = if (umbral >= 1.0) Eclipse.Kind.TOTAL else Eclipse.Kind.PARTIAL
            val n = 0.5458 + 0.0400 * cos(mp)
            val pp = 1.0128 - u
            val semi = 60.0 / n * sqrt(pp * pp - g * g) // minutes
            val start = maxMillis - (semi * 60_000).toLong()
            val end = maxMillis + (semi * 60_000).toLong()
            // Visible while the Moon is up during the umbral phase.
            var first: Long? = null
            var last: Long? = null
            var tt = start
            while (tt <= end) {
                if (MoonTimes.moonAltitude(tt, latitude, longitude) > -0.3) {
                    if (first == null) first = tt
                    last = tt
                }
                tt += 60_000L
            }
            // Seen as total here only if the Moon is up during totality.
            val tt2 = 0.4678 - u
            val totalSemi = if (kind == Eclipse.Kind.TOTAL) 60.0 / n * sqrt(tt2 * tt2 - g * g) else 0.0
            val totalStart = maxMillis - (totalSemi * 60_000).toLong()
            val totalEnd = maxMillis + (totalSemi * 60_000).toLong()
            val local = when {
                first == null || last == null -> null
                kind == Eclipse.Kind.TOTAL && last >= totalStart && first <= totalEnd -> Eclipse.Kind.TOTAL
                else -> Eclipse.Kind.PARTIAL
            }
            Eclipse(false, kind, maxMillis, first, last, local)
        }
    }

    /** The eclipse from one place: topocentric Moon against the Sun, every 2 minutes for ±4 hours. */
    private fun localSolar(maxMillis: Long, kind: Eclipse.Kind, latitude: Double, longitude: Double): Eclipse {
        var first: Long? = null
        var last: Long? = null
        var central = false
        var annular = false
        var tt = maxMillis - 4 * 3_600_000L
        val end = maxMillis + 4 * 3_600_000L
        while (tt <= end) {
            val s = sunMoonTopocentric(tt, latitude, longitude)
            if (s.sunAltitude > -0.8 && s.separation < s.sunRadius + s.moonRadius) {
                if (first == null) first = tt
                last = tt
                if (s.separation < abs(s.moonRadius - s.sunRadius)) {
                    if (s.moonRadius > s.sunRadius) central = true else annular = true
                }
            }
            tt += 120_000L
        }
        val local = when {
            first == null -> null
            central -> Eclipse.Kind.TOTAL
            annular -> Eclipse.Kind.ANNULAR
            else -> Eclipse.Kind.PARTIAL
        }
        return Eclipse(true, kind, maxMillis, first, last, local)
    }

    private class Sky(val separation: Double, val sunRadius: Double, val moonRadius: Double, val sunAltitude: Double)

    private val DIST_TERMS = listOf(
        doubleArrayOf(-20905355.0, 0.0, 0.0, 1.0, 0.0), doubleArrayOf(-3699111.0, 2.0, 0.0, -1.0, 0.0),
        doubleArrayOf(-2955968.0, 2.0, 0.0, 0.0, 0.0), doubleArrayOf(-569925.0, 0.0, 0.0, 2.0, 0.0),
        doubleArrayOf(48888.0, 0.0, 1.0, 0.0, 0.0), doubleArrayOf(-3149.0, 0.0, 0.0, 0.0, 2.0),
        doubleArrayOf(246158.0, 2.0, 0.0, -2.0, 0.0), doubleArrayOf(-152138.0, 2.0, -1.0, -1.0, 0.0),
        doubleArrayOf(-170733.0, 2.0, 0.0, 1.0, 0.0), doubleArrayOf(-204586.0, 2.0, -1.0, 0.0, 0.0),
        doubleArrayOf(-129620.0, 0.0, 1.0, -1.0, 0.0), doubleArrayOf(108743.0, 1.0, 0.0, 0.0, 0.0),
        doubleArrayOf(104755.0, 0.0, 1.0, 1.0, 0.0), doubleArrayOf(10321.0, 2.0, 0.0, 0.0, -2.0),
    )

    /** Moon–Earth distance in km, Meeus table 47.A (largest terms). */
    internal fun moonDistanceKm(t: Double): Double {
        val d = rad(297.8501921 + 445267.1114034 * t)
        val m = rad(357.5291092 + 35999.0502909 * t)
        val mp = rad(134.9633964 + 477198.8675055 * t)
        val f = rad(93.2720950 + 483202.0175233 * t)
        val sum = DIST_TERMS.sumOf { (c, cd, cm, cmp, cf) -> c * cos(cd * d + cm * m + cmp * mp + cf * f) }
        return 385000.56 + sum / 1000.0
    }

    private fun sunMoonTopocentric(ms: Long, latitude: Double, longitude: Double): Sky {
        val jd = julian(ms)
        val t = (jd - 2451545.0) / 36525.0
        val eps = rad(23.439291 - 0.0130042 * t)
        val lst = rad(norm360(280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t + longitude))
        val phi = rad(latitude)

        val ls = rad(HinduPanchang.sunLongitude(t))
        val sunRa = atan2(cos(eps) * sin(ls), cos(ls))
        val sunDec = asin(sin(eps) * sin(ls))

        val lm = rad(HinduPanchang.moonLongitude(t))
        val bm = rad(HinduPanchang.moonLatitude(t))
        var moonRa = atan2(sin(lm) * cos(eps) - tan(bm) * sin(eps), cos(lm))
        var moonDec = asin(sin(bm) * cos(eps) + cos(bm) * sin(eps) * sin(lm))
        val dist = moonDistanceKm(t)
        val sinPar = 6378.14 / dist
        // Parallax (Meeus ch. 40, spherical Earth).
        val h = lst - moonRa
        val dRa = atan2(-cos(phi) * sinPar * sin(h), cos(moonDec) - cos(phi) * sinPar * cos(h))
        moonDec = atan2((sin(moonDec) - sin(phi) * sinPar) * cos(dRa), cos(moonDec) - cos(phi) * sinPar * cos(h))
        moonRa += dRa

        val sep = acos(
            (sin(sunDec) * sin(moonDec) + cos(sunDec) * cos(moonDec) * cos(sunRa - moonRa)).coerceIn(-1.0, 1.0),
        )
        val sunAlt = asin(sin(phi) * sin(sunDec) + cos(phi) * cos(sunDec) * cos(lst - sunRa))
        // Topocentric Moon is a little bigger than geocentric; the Sun is 0.2666° ±1.7%.
        val moonRadius = asin(0.272481 * sinPar)
        val sunDistAu = 1.000140 - 0.016708 * cos(rad(357.52911 + 35999.05029 * t))
        val sunRadius = rad(0.266563 / sunDistAu)
        return Sky(Math.toDegrees(sep), Math.toDegrees(sunRadius), Math.toDegrees(moonRadius), Math.toDegrees(sunAlt))
    }

    private fun julian(ms: Long) = ms / 86_400_000.0 + 2440587.5
    private fun millis(jd: Double) = ((jd - 2440587.5) * 86_400_000.0).toLong()
    private fun rad(deg: Double) = Math.toRadians(deg)
    private fun norm360(deg: Double): Double {
        val v = deg % 360.0
        return if (v < 0) v + 360.0 else v
    }
}
