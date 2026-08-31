package com.tileshell.core.data

import kotlin.math.floor
import kotlin.math.sin

/**
 * Which fortnight (paksha) of the lunar month a tithi falls in — waxing
 * (new moon toward full moon) or waning (full moon toward new moon).
 */
enum class Paksha { SHUKLA, KRISHNA }

/** One lunar day (tithi): 1-15 within its [paksha], plus the display [name]. */
data class TithiInfo(val paksha: Paksha, val tithiInPaksha: Int, val name: String)

/**
 * A full day's Panchang: the prevailing [tithi], [nakshatra], lunar [month],
 * [vara] (weekday), and the two most common Hindu calendar years —
 * [shakaSamvat] (the Indian National Calendar's own era, epoch 78 CE) and
 * [vikramSamvat] (epoch 57 BCE) — both anchored to the same Chaitra new-year
 * crossing, so they always move together.
 */
data class PanchangInfo(
    val tithi: TithiInfo,
    val nakshatra: String,
    val month: String,
    val vara: String,
    val shakaSamvat: Int,
    val vikramSamvat: Int,
)

internal val TITHI_NAMES_1_TO_14 = listOf(
    "pratipada", "dwitiya", "tritiya", "chaturthi", "panchami",
    "shashthi", "saptami", "ashtami", "navami", "dashami",
    "ekadashi", "dwadashi", "trayodashi", "chaturdashi",
)

internal val NAKSHATRA_NAMES = listOf(
    "ashwini", "bharani", "krittika", "rohini", "mrigashira", "ardra", "punarvasu", "pushya", "ashlesha",
    "magha", "purva phalguni", "uttara phalguni", "hasta", "chitra", "swati", "vishakha", "anuradha", "jyeshtha",
    "mula", "purva ashadha", "uttara ashadha", "shravana", "dhanishta", "shatabhisha",
    "purva bhadrapada", "uttara bhadrapada", "revati",
)

/** Index = the Sun's sidereal zodiac sign (0 = Mesha/Aries ... 11 = Meena/Pisces) at the defining new moon. */
internal val MONTH_NAMES = listOf(
    "vaishakha", "jyeshtha", "ashadha", "shravana", "bhadrapada", "ashwin",
    "kartika", "margashirsha", "pausha", "magha", "phalguna", "chaitra",
)

/** Index 0 = Sunday, matching `Calendar.DAY_OF_WEEK - 1`. */
internal val VARA_NAMES = listOf(
    "ravivara", "somavara", "mangalavara", "budhavara", "guruvara", "shukravara", "shanivara",
)

/**
 * Hindu lunisolar Panchang — tithi, nakshatra, lunar month, and vara (weekday).
 * Nothing like this exists in the JDK or in Android's `android.icu` calendar
 * set (ICU's own "Indian" locale calendar is the unrelated solar National/
 * Saka calendar — month names, no tithi/nakshatra concept), so this computes
 * it directly from a low-precision Sun/Moon position formula (Meeus,
 * *Astronomical Algorithms*, ch. 25 and the introduction to ch. 47) — pure
 * trig, no device API.
 *
 * A Panchang is traditionally a whole-day designation referenced to sunrise,
 * not a value that changes minute to minute — [panchangFor] approximates
 * sunrise as 6:00 local time (no geolocation dependency); using local
 * midnight instead was tried first and found to disagree with a real
 * reference day right at a nakshatra boundary (see the calibration test).
 *
 * The lunar month uses the standard amanta rule: named for the Sun's
 * sidereal zodiac sign at the new moon that *starts* the current lunar
 * month (found by [findPrecedingNewMoonJd]), not the Sun's position on the
 * reference day itself — using "today's" own solar position was tried first
 * and produced the wrong month whenever today falls late in a lunar month
 * (the Sun can drift into the *next* sign before the *next* new moon
 * arrives). Adhika (intercalary) months are not detected — a known
 * simplification; see the class doc for the calibration this was checked
 * against.
 *
 * Calibrated against a real reference day (2026-08-30, India) confirmed by
 * hand: Krishna Paksha Dwitiya, month Shravana, nakshatra Uttara
 * Bhadrapada — [panchangFor] reproduces all three exactly at 6:00 IST.
 */
object HinduPanchang {

    private fun norm360(deg: Double): Double {
        val m = deg % 360.0
        return if (m < 0) m + 360.0 else m
    }

    private fun julianDay(epochMillis: Long): Double = epochMillis / 86400000.0 + 2440587.5

    private fun centuriesSinceJ2000(jd: Double): Double = (jd - 2451545.0) / 36525.0

    /** Low-precision apparent solar ecliptic longitude, degrees 0..360 (Meeus ch. 25). */
    internal fun sunLongitude(t: Double): Double {
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = norm360(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val mRad = Math.toRadians(m)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
            (0.019993 - 0.000101 * t) * sin(2 * mRad) +
            0.000289 * sin(3 * mRad)
        return norm360(l0 + c)
    }

    /** The ~21 largest lunar periodic terms (coeff, D-mult, M-mult, M'-mult, F-mult), Meeus ch. 47 intro. */
    private val MOON_TERMS = listOf(
        doubleArrayOf(6.288774, 0.0, 0.0, 1.0, 0.0),
        doubleArrayOf(1.274027, 2.0, 0.0, -1.0, 0.0),
        doubleArrayOf(0.658314, 2.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.213618, 0.0, 0.0, 2.0, 0.0),
        doubleArrayOf(-0.185116, 0.0, 1.0, 0.0, 0.0),
        doubleArrayOf(-0.114332, 0.0, 0.0, 0.0, 2.0),
        doubleArrayOf(0.058793, 2.0, 0.0, -2.0, 0.0),
        doubleArrayOf(0.057066, 2.0, -1.0, -1.0, 0.0),
        doubleArrayOf(0.053322, 2.0, 0.0, 1.0, 0.0),
        doubleArrayOf(0.045758, 2.0, -1.0, 0.0, 0.0),
        doubleArrayOf(-0.040923, 0.0, 1.0, -1.0, 0.0),
        doubleArrayOf(-0.034720, 1.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-0.030383, 0.0, 1.0, 1.0, 0.0),
        doubleArrayOf(0.015327, 2.0, 0.0, 0.0, -2.0),
        doubleArrayOf(-0.012528, 0.0, 0.0, 1.0, 2.0),
        doubleArrayOf(0.010980, 0.0, 0.0, 1.0, -2.0),
        doubleArrayOf(0.010675, 4.0, 0.0, -1.0, 0.0),
        doubleArrayOf(0.010034, 0.0, 0.0, 3.0, 0.0),
        doubleArrayOf(0.008548, 4.0, 0.0, -2.0, 0.0),
        doubleArrayOf(-0.007888, 2.0, 1.0, -1.0, 0.0),
        doubleArrayOf(-0.006766, 2.0, 1.0, 0.0, 0.0),
    )

    /** Low-precision lunar ecliptic longitude, degrees 0..360 (Meeus ch. 47 intro, truncated series). */
    internal fun moonLongitude(t: Double): Double {
        val lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t * t
        val d = Math.toRadians(norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t))
        val m = Math.toRadians(norm360(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t))
        val mp = Math.toRadians(norm360(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t))
        val f = Math.toRadians(norm360(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t))
        val sum = MOON_TERMS.sumOf { (coeff, cd, cm, cmp, cf) ->
            coeff * sin(cd * d + cm * m + cmp * mp + cf * f)
        }
        return norm360(lp + sum)
    }

    /** Lahiri ayanamsa, linear approximation (~24.2° in the mid-2020s) — plenty precise next to a nakshatra's 13.33° width. */
    internal fun ayanamsa(t: Double): Double = 23.85 + 1.396 * t

    private fun sunSiderealLongitude(t: Double): Double = norm360(sunLongitude(t) - ayanamsa(t))
    private fun moonSiderealLongitude(t: Double): Double = norm360(moonLongitude(t) - ayanamsa(t))

    /** Pure — the named tithi for a given Moon-minus-Sun [elongation] in degrees (any range; wrapped mod 360). */
    internal fun tithiFromElongation(elongation: Double): TithiInfo {
        val tithiIndex = floor(norm360(elongation) / 12.0).toInt().coerceIn(0, 29) // 0..29
        val paksha = if (tithiIndex < 15) Paksha.SHUKLA else Paksha.KRISHNA
        val withinPaksha = (tithiIndex % 15) + 1 // 1..15
        val name = if (withinPaksha == 15) {
            if (paksha == Paksha.SHUKLA) "purnima" else "amavasya"
        } else {
            TITHI_NAMES_1_TO_14[withinPaksha - 1]
        }
        return TithiInfo(paksha, withinPaksha, name)
    }

    /** Pure — the named nakshatra for a given sidereal Moon [longitude] in degrees (any range; wrapped mod 360). */
    internal fun nakshatraFromSiderealLongitude(longitude: Double): String {
        val index = floor(norm360(longitude) / (360.0 / 27.0)).toInt().coerceIn(0, 26)
        return NAKSHATRA_NAMES[index]
    }

    /** Pure — the named amanta lunar month for a new moon whose Sun sidereal longitude is [sunSiderealAtNewMoon]. */
    internal fun monthFromNewMoonSunSiderealLongitude(sunSiderealAtNewMoon: Double): String {
        val zodiacIndex = floor(norm360(sunSiderealAtNewMoon) / 30.0).toInt().coerceIn(0, 11)
        return MONTH_NAMES[zodiacIndex]
    }

    private fun elongationSignedAtJd(jd: Double): Double {
        val t = centuriesSinceJ2000(jd)
        val e = norm360(moonLongitude(t) - sunLongitude(t))
        return if (e > 180.0) e - 360.0 else e
    }

    /**
     * Julian Day of the most recent new moon at or before [jdRef] — scans
     * backward in 1-day steps (a lunar month is ~29.5 days, so 40 days
     * always brackets one) for where the Moon-minus-Sun elongation, taken
     * signed into (-180, 180], crosses from negative back up through 0 as
     * time increases, then bisects that one-day bracket to the crossing.
     */
    internal fun findPrecedingNewMoonJd(jdRef: Double): Double {
        var jd = jdRef
        var prev = elongationSignedAtJd(jd)
        repeat(40) {
            val jd2 = jd - 1.0
            val cur = elongationSignedAtJd(jd2)
            if (prev >= 0.0 && cur < 0.0) {
                var lo = jd2
                var hi = jd
                repeat(50) {
                    val mid = (lo + hi) / 2.0
                    if (elongationSignedAtJd(mid) < 0.0) lo = mid else hi = mid
                }
                return hi
            }
            prev = cur
            jd = jd2
        }
        return jdRef
    }

    /** The tithi (lunar day) prevailing at [epochMillis]. */
    fun tithiFor(epochMillis: Long): TithiInfo {
        val t = centuriesSinceJ2000(julianDay(epochMillis))
        return tithiFromElongation(moonLongitude(t) - sunLongitude(t))
    }

    /**
     * Walks backward one amanta month at a time from [fromNewMoonJd] (a
     * month-start new moon) until it finds the one that starts Chaitra —
     * the Hindu new year's own month — returning that new moon's Julian
     * Day. A year always contains exactly one Chaitra, so 13 steps always
     * finds it regardless of where in the cycle [fromNewMoonJd] falls.
     */
    private fun findChaitraStartJd(fromNewMoonJd: Double): Double {
        var newMoonJd = fromNewMoonJd
        repeat(13) {
            val monthName = monthFromNewMoonSunSiderealLongitude(sunSiderealLongitude(centuriesSinceJ2000(newMoonJd)))
            if (monthName == "chaitra") return newMoonJd
            newMoonJd = findPrecedingNewMoonJd(newMoonJd - 1.0)
        }
        return newMoonJd
    }

    /**
     * The full Panchang for the calendar day (in [zone]) containing
     * [epochMillis] — tithi/nakshatra/month are evaluated at that day's
     * 6:00 local time (a sunrise proxy; see the class doc), vara from the
     * calendar date itself. The Hindu year rolls over at Chaitra (found via
     * [findChaitraStartJd]), same as the real calendars it names.
     */
    fun panchangFor(epochMillis: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): PanchangInfo {
        val cal = java.util.Calendar.getInstance(zone)
        cal.timeInMillis = epochMillis
        val vara = VARA_NAMES[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        cal.set(java.util.Calendar.HOUR_OF_DAY, 6)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val referenceJd = julianDay(cal.timeInMillis)
        val t = centuriesSinceJ2000(referenceJd)

        val tithi = tithiFromElongation(moonLongitude(t) - sunLongitude(t))
        val nakshatra = nakshatraFromSiderealLongitude(moonSiderealLongitude(t))
        val newMoonJd = findPrecedingNewMoonJd(referenceJd)
        val month = monthFromNewMoonSunSiderealLongitude(sunSiderealLongitude(centuriesSinceJ2000(newMoonJd)))

        val chaitraStartJd = findChaitraStartJd(newMoonJd)
        val chaitraStartMillis = ((chaitraStartJd - 2440587.5) * 86400000.0).toLong()
        val chaitraCal = java.util.Calendar.getInstance(zone).apply { timeInMillis = chaitraStartMillis }
        val chaitraGregorianYear = chaitraCal.get(java.util.Calendar.YEAR)
        val shakaSamvat = chaitraGregorianYear - 78
        val vikramSamvat = chaitraGregorianYear + 57

        return PanchangInfo(tithi, nakshatra, month, vara, shakaSamvat, vikramSamvat)
    }
}

/**
 * Devanagari-script equivalents of [PanchangInfo]'s English/transliterated
 * field values, for the Panchang tile's Devanagari face. Each lookup falls
 * back to the English input on a miss (defensive only — every value
 * [HinduPanchang] can actually produce is covered, which
 * `PanchangDevanagariTest`'s completeness checks enforce against the same
 * name lists [HinduPanchang] builds [PanchangInfo] from).
 */
object PanchangDevanagari {
    private val VARA = mapOf(
        "ravivara" to "रविवार", "somavara" to "सोमवार", "mangalavara" to "मंगलवार",
        "budhavara" to "बुधवार", "guruvara" to "गुरुवार", "shukravara" to "शुक्रवार", "shanivara" to "शनिवार",
    )

    private val TITHI = mapOf(
        "pratipada" to "प्रतिपदा", "dwitiya" to "द्वितीया", "tritiya" to "तृतीया", "chaturthi" to "चतुर्थी",
        "panchami" to "पंचमी", "shashthi" to "षष्ठी", "saptami" to "सप्तमी", "ashtami" to "अष्टमी",
        "navami" to "नवमी", "dashami" to "दशमी", "ekadashi" to "एकादशी", "dwadashi" to "द्वादशी",
        "trayodashi" to "त्रयोदशी", "chaturdashi" to "चतुर्दशी", "purnima" to "पूर्णिमा", "amavasya" to "अमावस्या",
    )

    private val MONTH = mapOf(
        "vaishakha" to "वैशाख", "jyeshtha" to "ज्येष्ठ", "ashadha" to "आषाढ़", "shravana" to "श्रावण",
        "bhadrapada" to "भाद्रपद", "ashwin" to "आश्विन", "kartika" to "कार्तिक", "margashirsha" to "मार्गशीर्ष",
        "pausha" to "पौष", "magha" to "माघ", "phalguna" to "फाल्गुन", "chaitra" to "चैत्र",
    )

    private val NAKSHATRA = mapOf(
        "ashwini" to "अश्विनी", "bharani" to "भरणी", "krittika" to "कृत्तिका", "rohini" to "रोहिणी",
        "mrigashira" to "मृगशिरा", "ardra" to "आर्द्रा", "punarvasu" to "पुनर्वसु", "pushya" to "पुष्य",
        "ashlesha" to "आश्लेषा", "magha" to "मघा", "purva phalguni" to "पूर्वाफाल्गुनी",
        "uttara phalguni" to "उत्तराफाल्गुनी", "hasta" to "हस्त", "chitra" to "चित्रा", "swati" to "स्वाती",
        "vishakha" to "विशाखा", "anuradha" to "अनुराधा", "jyeshtha" to "ज्येष्ठा", "mula" to "मूल",
        "purva ashadha" to "पूर्वाषाढ़ा", "uttara ashadha" to "उत्तराषाढ़ा", "shravana" to "श्रवण",
        "dhanishta" to "धनिष्ठा", "shatabhisha" to "शतभिषा", "purva bhadrapada" to "पूर्वाभाद्रपदा",
        "uttara bhadrapada" to "उत्तराभाद्रपदा", "revati" to "रेवती",
    )

    fun vara(value: String): String = VARA[value] ?: value
    fun tithiName(value: String): String = TITHI[value] ?: value
    fun paksha(paksha: Paksha): String = if (paksha == Paksha.SHUKLA) "शुक्ल पक्ष" else "कृष्ण पक्ष"
    fun month(value: String): String = MONTH[value] ?: value
    fun nakshatra(value: String): String = NAKSHATRA[value] ?: value
}
