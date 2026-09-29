package com.tileshell.core.data

import java.util.Calendar
import java.util.TimeZone

/**
 * When in the day a tithi must prevail for the day to observe it: most at
 * sunrise; Ganesh Chaturthi at midday, Dussehra in the afternoon, Pradosh and Diwali in the evening,
 * Mahashivaratri and Janmashtami at midnight, Sankashti at moonrise.
 */
enum class ObserveAt { SUNRISE, NOON, AFTERNOON, EVENING, MIDNIGHT, MOONRISE }

/**
 * A tithi to highlight on the Panchang tile. [paksha] null = both pakshas;
 * [month] null = every month. [tithi] is 1-15 within the paksha (15 =
 * purnima / amavasya).
 */
data class TithiHighlight(
    val id: String,
    val name: String,
    val english: String,
    val paksha: Paksha?,
    val tithi: Int,
    val month: String? = null,
    val at: ObserveAt = ObserveAt.SUNRISE,
    val defaultOn: Boolean = false,
)

/** A Hindu festival on a fixed lunar date (amanta month). */
data class Festival(
    val name: String,
    val english: String,
    val month: String,
    val paksha: Paksha,
    val tithi: Int,
    val at: ObserveAt = ObserveAt.SUNRISE,
)

/** One highlight, festival or grahan falling on a day. [detail] carries a grahan's times. */
data class Observance(
    val id: String,
    val name: String,
    val english: String,
    val festival: Boolean,
    val grahan: Boolean = false,
    /** For a grahan: seen from here. The tile only shows visible ones. */
    val visibleHere: Boolean = true,
    val eclipse: Eclipse? = null,
)

/** Which highlights are on, whether festivals show, and the user's own tithis ("s9" / "k9"). */
data class ObservanceSettings(
    val highlights: Set<String> = PanchangObservances.HIGHLIGHTS.filter { it.defaultOn }.map { it.id }.toSet(),
    val festivals: Boolean = true,
    val customTithis: Set<String> = emptySet(),
    val grahan: Boolean = true,
)

/**
 * Highlighted tithis and Hindu festivals for a day, from [HinduPanchang].
 * A day observes a tithi that prevails at the observance's time of day
 * ([ObserveAt]) and didn't already prevail at that time the day before; a
 * tithi that prevails at no day's reference time (a kshaya tithi) goes to
 * the day before it would have. Festivals are skipped in an adhika month.
 * Pure, unit-tested.
 */
object PanchangObservances {
    val HIGHLIGHTS: List<TithiHighlight> = listOf(
        TithiHighlight("sankashti", "संकष्टी चतुर्थी", "sankashti chaturthi", Paksha.KRISHNA, 4, at = ObserveAt.MOONRISE, defaultOn = true),
        TithiHighlight("vinayaki", "विनायकी चतुर्थी", "vinayaki chaturthi", Paksha.SHUKLA, 4, at = ObserveAt.NOON),
        TithiHighlight("ekadashi", "एकादशी", "ekadashi", null, 11, defaultOn = true),
        TithiHighlight("pradosh", "प्रदोष", "pradosh", null, 13, at = ObserveAt.EVENING),
        TithiHighlight("purnima", "पूर्णिमा", "purnima", Paksha.SHUKLA, 15),
        TithiHighlight("amavasya", "अमावस्या", "amavasya", Paksha.KRISHNA, 15),
        TithiHighlight("mahashivaratri", "महाशिवरात्री", "mahashivaratri", Paksha.KRISHNA, 14, month = "magha", at = ObserveAt.MIDNIGHT, defaultOn = true),
    )

    val FESTIVALS: List<Festival> = listOf(
        Festival("गुढीपाडवा", "gudi padwa", "chaitra", Paksha.SHUKLA, 1),
        Festival("राम नवमी", "ram navami", "chaitra", Paksha.SHUKLA, 9, ObserveAt.NOON),
        Festival("हनुमान जयंती", "hanuman jayanti", "chaitra", Paksha.SHUKLA, 15),
        Festival("अक्षय तृतीया", "akshaya tritiya", "vaishakha", Paksha.SHUKLA, 3),
        Festival("वटपौर्णिमा", "vat purnima", "jyeshtha", Paksha.SHUKLA, 15),
        Festival("आषाढी एकादशी", "ashadhi ekadashi", "ashadha", Paksha.SHUKLA, 11),
        Festival("गुरुपौर्णिमा", "guru purnima", "ashadha", Paksha.SHUKLA, 15),
        Festival("नागपंचमी", "nag panchami", "shravana", Paksha.SHUKLA, 5),
        Festival("रक्षाबंधन", "raksha bandhan", "shravana", Paksha.SHUKLA, 15),
        Festival("श्रीकृष्ण जन्माष्टमी", "janmashtami", "shravana", Paksha.KRISHNA, 8, ObserveAt.MIDNIGHT),
        Festival("गणेश चतुर्थी", "ganesh chaturthi", "bhadrapada", Paksha.SHUKLA, 4, ObserveAt.NOON),
        Festival("अनंत चतुर्दशी", "anant chaturdashi", "bhadrapada", Paksha.SHUKLA, 14),
        Festival("घटस्थापना", "ghatasthapana · navratri", "ashwin", Paksha.SHUKLA, 1),
        Festival("दसरा", "dussehra", "ashwin", Paksha.SHUKLA, 10, ObserveAt.AFTERNOON),
        Festival("कोजागिरी पौर्णिमा", "kojagiri purnima", "ashwin", Paksha.SHUKLA, 15, ObserveAt.MIDNIGHT),
        Festival("धनत्रयोदशी", "dhanteras", "ashwin", Paksha.KRISHNA, 13, ObserveAt.EVENING),
        Festival("नरक चतुर्दशी", "narak chaturdashi", "ashwin", Paksha.KRISHNA, 14),
        Festival("लक्ष्मीपूजन", "lakshmi pujan · diwali", "ashwin", Paksha.KRISHNA, 15, ObserveAt.EVENING),
        Festival("बलिप्रतिपदा", "diwali padwa", "kartika", Paksha.SHUKLA, 1),
        Festival("भाऊबीज", "bhaubeej", "kartika", Paksha.SHUKLA, 2),
        Festival("कार्तिकी एकादशी", "kartiki ekadashi", "kartika", Paksha.SHUKLA, 11),
        Festival("तुळशी विवाह", "tulsi vivah", "kartika", Paksha.SHUKLA, 12),
        Festival("त्रिपुरारी पौर्णिमा", "tripurari purnima", "kartika", Paksha.SHUKLA, 15),
        Festival("दत्त जयंती", "datta jayanti", "margashirsha", Paksha.SHUKLA, 15, ObserveAt.EVENING),
        Festival("वसंत पंचमी", "vasant panchami", "magha", Paksha.SHUKLA, 5),
        Festival("होळी", "holi", "phalguna", Paksha.SHUKLA, 15, ObserveAt.EVENING),
        Festival("धूलिवंदन", "dhulivandan", "phalguna", Paksha.KRISHNA, 1),
        Festival("रंगपंचमी", "rang panchami", "phalguna", Paksha.KRISHNA, 5),
    )

    /** Each month's two ekadashis by name (amanta month; shukla, krishna). */
    private val EKADASHI_NAMES = mapOf(
        "chaitra" to ("कामदा" to "वरूथिनी"), "vaishakha" to ("मोहिनी" to "अपरा"),
        "jyeshtha" to ("निर्जला" to "योगिनी"), "ashadha" to ("देवशयनी" to "कामिका"),
        "shravana" to ("पुत्रदा" to "अजा"), "bhadrapada" to ("परिवर्तिनी" to "इंदिरा"),
        "ashwin" to ("पाशांकुशा" to "रमा"), "kartika" to ("प्रबोधिनी" to "उत्पत्ती"),
        "margashirsha" to ("मोक्षदा" to "सफला"), "pausha" to ("पुत्रदा" to "षट्तिला"),
        "magha" to ("जया" to "विजया"), "phalguna" to ("आमलकी" to "पापमोचनी"),
    )
    private val EKADASHI_ENGLISH = mapOf(
        "कामदा" to "kamada", "वरूथिनी" to "varuthini", "मोहिनी" to "mohini", "अपरा" to "apara",
        "निर्जला" to "nirjala", "योगिनी" to "yogini", "देवशयनी" to "devshayani", "कामिका" to "kamika",
        "पुत्रदा" to "putrada", "अजा" to "aja", "परिवर्तिनी" to "parivartini", "इंदिरा" to "indira",
        "पाशांकुशा" to "papankusha", "रमा" to "rama", "प्रबोधिनी" to "prabodhini", "उत्पत्ती" to "utpatti",
        "मोक्षदा" to "mokshada", "सफला" to "saphala", "षट्तिला" to "shattila", "जया" to "jaya",
        "विजया" to "vijaya", "आमलकी" to "amalaki", "पापमोचनी" to "papmochani", "पद्मिनी" to "padmini", "परमा" to "parama",
    )

    /** "कामिका एकादशी": the ekadashi's own name for its month (adhik: पद्मिनी / परमा). */
    internal fun ekadashiName(month: String, adhik: Boolean, paksha: Paksha): Pair<String, String> {
        val name = if (adhik) {
            if (paksha == Paksha.SHUKLA) "पद्मिनी" else "परमा"
        } else {
            EKADASHI_NAMES[month]?.let { if (paksha == Paksha.SHUKLA) it.first else it.second }
        } ?: return "एकादशी" to "ekadashi"
        return "$name एकादशी" to "${EKADASHI_ENGLISH[name] ?: name} ekadashi"
    }

    private val SANKRANTI = Observance("makar-sankranti", "मकर संक्रांती", "makar sankranti", festival = true)

    /** A custom tithi key: "s9" (shukla navami), "k4", or "b11" (both pakshas). */
    fun customKey(paksha: Paksha?, tithi: Int): String = when (paksha) {
        Paksha.SHUKLA -> "s$tithi"
        Paksha.KRISHNA -> "k$tithi"
        null -> "b$tithi"
    } 

    fun parseCustomKey(key: String): Pair<Paksha?, Int>? {
        val tithi = key.drop(1).toIntOrNull()?.takeIf { it in 1..15 } ?: return null
        return when (key.firstOrNull()) {
            's' -> Paksha.SHUKLA to tithi
            'k' -> Paksha.KRISHNA to tithi
            'b' -> null to tithi
            else -> null
        }
    }

    /** "नवमी" plus its paksha, for a custom tithi. */
    fun customName(paksha: Paksha?, tithi: Int): Pair<String, String> {
        val info = tithiInfo(if (paksha == Paksha.KRISHNA) 15 + tithi - 1 else tithi - 1)
        val dev = PanchangDevanagari.tithiName(if (paksha == null && tithi == 15) "purnima" else info.name)
        val eng = if (paksha == null && tithi == 15) "purnima / amavasya" else info.name
        val devName = if (paksha == null && tithi == 15) "पूर्णिमा / अमावस्या" else dev
        return when (paksha) {
            Paksha.SHUKLA -> "शुक्ल $devName" to "shukla $eng"
            Paksha.KRISHNA -> "कृष्ण $devName" to "krishna $eng"
            null -> devName to eng
        }
    }

    private fun tithiInfo(index: Int): TithiInfo = HinduPanchang.tithiFromElongation(index * 12.0 + 6.0)

    /**
     * Everything [settings] highlights on the calendar day (in [zone])
     * containing [dayMillis]. [moonriseAfter] gives the first moonrise after a
     * time (for Sankashti); without it, 9 pm stands in.
     */
    fun on(
        dayMillis: Long,
        settings: ObservanceSettings,
        zone: TimeZone = TimeZone.getDefault(),
        moonriseAfter: ((Long) -> Long?)? = null,
        location: Pair<Double, Double>? = null,
    ): List<Observance> {
        val day = startOfDay(dayMillis, zone)
        val owned = HashMap<ObserveAt, Map<Int, Long>>()
        fun ownedAt(at: ObserveAt) = owned.getOrPut(at) { ownedTithis(day, at, zone, moonriseAfter) }
        val months = HashMap<Long, Pair<String, Boolean>>()
        // The month the matched tithi falls in (a tithi just after the new
        // moon belongs to the next month even if today's sunrise was before it).
        fun monthOf(at: ObserveAt, paksha: Paksha?, tithi: Int): Pair<String, Boolean>? {
            val time = matchTime(ownedAt(at), paksha, tithi) ?: return null
            return months.getOrPut(time) { HinduPanchang.lunarMonthAt(time) }
        }

        val out = mutableListOf<Observance>()
        if ("ekadashi" in settings.highlights) ekadashiOn(day, zone)?.let { out += it }
        HIGHLIGHTS.filter { it.id in settings.highlights && it.id != "ekadashi" }.forEach { h ->
            val month = monthOf(h.at, h.paksha, h.tithi) ?: return@forEach
            if (h.month == null || (month.first == h.month && !month.second)) {
                out += when (h.id) {
                    // Sankashti on a Tuesday (मंगळवार) is Angaraki.
                    "sankashti" -> if (Calendar.getInstance(zone).apply { timeInMillis = day }.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY) {
                        Observance(h.id, "अंगारकी संकष्टी चतुर्थी", "angaraki sankashti chaturthi", festival = false)
                    } else {
                        Observance(h.id, h.name, h.english, festival = false)
                    }
                    else -> Observance(h.id, h.name, h.english, festival = false)
                }
            }
        }
        settings.customTithis.sorted().forEach { key ->
            val (paksha, tithi) = parseCustomKey(key) ?: return@forEach
            if (matchTime(ownedAt(ObserveAt.SUNRISE), paksha, tithi) != null) {
                val (dev, eng) = customName(paksha, tithi)
                if (out.none { it.name == dev }) out += Observance("custom-$key", dev, eng, festival = false)
            }
        }
        if (settings.festivals) {
            FESTIVALS.forEach { f ->
                val (name, adhik) = monthOf(f.at, f.paksha, f.tithi) ?: return@forEach
                if (name == f.month && !adhik) out += Observance("festival-${f.english}", f.name, f.english, festival = true)
            }
            val sunStart = HinduPanchang.sunSiderealAt(day)
            val sunEnd = HinduPanchang.sunSiderealAt(day + DAY)
            if (sunStart < 270.0 && sunEnd >= 270.0) out += SANKRANTI
        }
        if (settings.grahan && location != null) {
            // A visible grahan belongs to the local day it's seen on; one not
            // seen here, to the day of its greatest phase.
            Eclipses.between(day - DAY, day + 2 * DAY, location.first, location.second).forEach { e ->
                val anchor = e.visibleStart ?: e.maxMillis
                if (anchor >= day && anchor < addDays(day, 1, zone)) out += grahanObservance(e)
            }
        }
        // A festival that is itself the highlighted tithi ("kartiki ekadashi")
        // stands in for the plain highlight ("ekadashi"). Festivals first.
        val festivalNames = out.filter { it.festival }.map { it.english }
        return out
            .filterNot { o ->
                val base = HIGHLIGHTS.firstOrNull { it.id == o.id }?.english ?: o.english
                !o.festival && !o.grahan && festivalNames.any { base in it }
            }
            .sortedWith(compareByDescending<Observance> { it.grahan && it.visibleHere }.thenByDescending { it.festival })
    }

    /**
     * The ekadashi on [day], if any, for both traditions. Smarta keep the
     * ekadashi prevailing at sunrise (the first day when it spans two
     * sunrises). Vaishnava move a day later when dashami still prevails at
     * arunodaya (96 minutes before sunrise) or when the ekadashi also runs
     * through the next sunrise. Same day: one entry; different days:
     * "(स्मार्त)" on the first and "(वैष्णव)" on the second.
     */
    internal fun ekadashiOn(day: Long, zone: TimeZone): Observance? {
        val yesterday = addDays(day, -1, zone)
        val tomorrow = addDays(day, 1, zone)
        val smartaToday = smartaEkadashi(day, zone)
        val smartaYesterday = smartaEkadashi(yesterday, zone)
        val vaishnavaToday = (smartaToday != null && !vaishnavaMoves(day, tomorrow)) ||
            (smartaYesterday != null && vaishnavaMoves(yesterday, day))
        val source = smartaToday ?: smartaYesterday?.takeIf { vaishnavaToday } ?: return null
        val (month, paksha) = source
        val (dev, eng) = ekadashiName(month.first, month.second, paksha)
        return when {
            smartaToday != null && vaishnavaToday -> Observance("ekadashi", dev, eng, festival = false)
            smartaToday != null -> Observance("ekadashi", "$dev (स्मार्त)", "$eng (smarta)", festival = false)
            else -> Observance("ekadashi", "$dev (वैष्णव)", "$eng (vaishnava)", festival = false)
        }
    }

    /** The month and paksha of the ekadashi Smartas keep on [day], or null. */
    private fun smartaEkadashi(day: Long, zone: TimeZone): Pair<Pair<String, Boolean>, Paksha>? {
        val owned = ownedTithis(day, ObserveAt.SUNRISE, zone, null)
        val (paksha, time) = when {
            owned[10] != null -> Paksha.SHUKLA to owned.getValue(10)
            owned[25] != null -> Paksha.KRISHNA to owned.getValue(25)
            else -> return null
        }
        return HinduPanchang.lunarMonthAt(time) to paksha
    }

    /** Whether Vaishnavas keep the ekadashi Smartas keep on [smartaDay] on [nextDay] instead. */
    private fun vaishnavaMoves(smartaDay: Long, nextDay: Long): Boolean {
        val arunodaya = HinduPanchang.tithiIndexAt(smartaDay + 6 * HOUR - 96 * 60_000L)
        val nextSunrise = HinduPanchang.tithiIndexAt(nextDay + 6 * HOUR)
        return arunodaya == 9 || arunodaya == 24 || nextSunrise == 10 || nextSunrise == 25
    }

    /** "खग्रास चंद्रग्रहण" etc., named for how it looks from here when visible. */
    internal fun grahanObservance(e: Eclipse): Observance {
        val kind = e.localKind ?: e.kind
        val prefix = when (kind) {
            Eclipse.Kind.TOTAL -> "खग्रास"
            Eclipse.Kind.ANNULAR -> "कंकणाकृती"
            Eclipse.Kind.HYBRID -> "संकरित"
            Eclipse.Kind.PARTIAL -> "खंडग्रास"
        }
        val body = if (e.solar) "सूर्यग्रहण" else "चंद्रग्रहण"
        val english = "${kind.name.lowercase()} ${if (e.solar) "solar" else "lunar"} eclipse"
        return Observance(
            id = "grahan-${e.maxMillis}",
            name = "$prefix $body",
            english = english,
            festival = false,
            grahan = true,
            visibleHere = e.visible,
            eclipse = e,
        )
    }

    /** Days (start-of-day millis) in the next [days] with anything to show, and what. */
    fun upcoming(
        fromMillis: Long,
        days: Int,
        settings: ObservanceSettings,
        zone: TimeZone = TimeZone.getDefault(),
        moonriseAfter: ((Long) -> Long?)? = null,
        location: Pair<Double, Double>? = null,
    ): List<Pair<Long, List<Observance>>> {
        val start = startOfDay(fromMillis, zone)
        // Grahan found once for the whole range, not per day.
        val eclipses = if (settings.grahan && location != null) {
            Eclipses.between(start - DAY, addDays(start, days, zone) + DAY, location.first, location.second)
        } else {
            emptyList()
        }
        val noGrahan = settings.copy(grahan = false)
        return (0 until days).mapNotNull { i ->
            val day = addDays(start, i, zone)
            val next = addDays(day, 1, zone)
            val grahan = eclipses.filter { (it.visibleStart ?: it.maxMillis).let { a -> a >= day && a < next } }.map(::grahanObservance)
            (grahan + on(day, noGrahan, zone, moonriseAfter)).takeIf { it.isNotEmpty() }?.let { day to it }
        }
    }

    /** When the matched tithi can be looked up (for its month), or null when [owned] doesn't include it. */
    private fun matchTime(owned: Map<Int, Long>, paksha: Paksha?, tithi: Int): Long? {
        val shukla = tithi - 1
        val krishna = 15 + tithi - 1
        return when (paksha) {
            Paksha.SHUKLA -> owned[shukla]
            Paksha.KRISHNA -> owned[krishna]
            null -> owned[shukla] ?: owned[krishna]
        }
    }

    /**
     * The tithis (0..29) day [day] owns at time-of-day [at]: the one
     * prevailing then, unless it already prevailed at that time yesterday,
     * plus any that are over before tomorrow's reference time.
     */
    internal fun ownedTithis(day: Long, at: ObserveAt, zone: TimeZone, moonriseAfter: ((Long) -> Long?)?): Map<Int, Long> {
        val todayTime = referenceTime(day, at, moonriseAfter)
        val tomorrowTime = referenceTime(addDays(day, 1, zone), at, moonriseAfter)
        val yesterday = HinduPanchang.tithiIndexAt(referenceTime(addDays(day, -1, zone), at, moonriseAfter))
        val today = HinduPanchang.tithiIndexAt(todayTime)
        val tomorrow = HinduPanchang.tithiIndexAt(tomorrowTime)
        // Each owned tithi maps to a time in the same lunar month as it: a
        // skipped tithi past the new moon (index wrapped) is in tomorrow's month.
        val owned = mutableMapOf<Int, Long>()
        if (today != yesterday) owned[today] = todayTime
        if (tomorrow != today) {
            var x = (today + 1) % 30
            while (x != tomorrow && owned.size < 3) {
                owned[x] = if (x < today) tomorrowTime else todayTime
                x = (x + 1) % 30
            }
        }
        return owned
    }

    private fun referenceTime(day: Long, at: ObserveAt, moonriseAfter: ((Long) -> Long?)?): Long = when (at) {
        ObserveAt.SUNRISE -> day + 6 * HOUR
        ObserveAt.NOON -> day + 12 * HOUR
        ObserveAt.AFTERNOON -> day + 14 * HOUR + 30 * 60_000L
        ObserveAt.EVENING -> day + 19 * HOUR
        ObserveAt.MIDNIGHT -> day + 24 * HOUR - 60_000L
        ObserveAt.MOONRISE -> moonriseAfter?.invoke(day + 17 * HOUR)?.takeIf { it < day + 30 * HOUR } ?: (day + 21 * HOUR)
    }

    fun startOfDay(millis: Long, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun addDays(day: Long, n: Int, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        timeInMillis = day
        add(Calendar.DAY_OF_MONTH, n)
    }.timeInMillis

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
}
