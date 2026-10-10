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
) {
    /** The highlight's name in [language] (Marathi's is [name]). */
    fun nameIn(language: PanchangLanguage): String = PanchangObservanceNames.highlight(id, language) ?: name
}

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
    /** The language names are written in, and (with it) which region's festivals show. */
    val language: PanchangLanguage = PanchangLanguage.DEFAULT,
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

    /** Every festival of every region (see [PanchangFestivals]). */
    val FESTIVALS: List<Festival> get() = PanchangFestivals.ALL

    /** "कामिका एकादशी" in [language]; (adhik: पद्मिनी / परमा). Returns the localized and the English name. */
    internal fun ekadashiName(month: String, adhik: Boolean, paksha: Paksha, language: PanchangLanguage = PanchangLanguage.MARATHI): Pair<String, String> =
        PanchangObservanceNames.ekadashi(language, month, adhik, paksha) to PanchangObservanceNames.ekadashi(PanchangLanguage.ENGLISH, month, adhik, paksha)

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

    /** "शुक्ल नवमी" plus its English form, for a custom tithi, in [language]. */
    fun customName(paksha: Paksha?, tithi: Int, language: PanchangLanguage = PanchangLanguage.MARATHI): Pair<String, String> =
        PanchangObservanceNames.customTithi(language, paksha, tithi) to PanchangObservanceNames.customTithi(PanchangLanguage.ENGLISH, paksha, tithi)

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

        val language = settings.language
        val out = mutableListOf<Observance>()
        if ("ekadashi" in settings.highlights) ekadashiOn(day, zone, language)?.let { out += it }
        HIGHLIGHTS.filter { it.id in settings.highlights && it.id != "ekadashi" }.forEach { h ->
            val month = monthOf(h.at, h.paksha, h.tithi) ?: return@forEach
            if (h.month == null || (month.first == h.month && !month.second)) {
                out += when (h.id) {
                    // Sankashti on a Tuesday (मंगळवार) is Angaraki.
                    "sankashti" -> if (Calendar.getInstance(zone).apply { timeInMillis = day }.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY) {
                        Observance(h.id, PanchangObservanceNames.angaraki(language), "angaraki sankashti chaturthi", festival = false)
                    } else {
                        Observance(h.id, h.nameIn(language), h.english, festival = false)
                    }
                    else -> Observance(h.id, h.nameIn(language), h.english, festival = false)
                }
            }
        }
        settings.customTithis.sorted().forEach { key ->
            val (paksha, tithi) = parseCustomKey(key) ?: return@forEach
            if (matchTime(ownedAt(ObserveAt.SUNRISE), paksha, tithi) != null) {
                val (local, eng) = customName(paksha, tithi, language)
                if (out.none { it.name == local }) out += Observance("custom-$key", local, eng, festival = false)
            }
        }
        if (settings.festivals) {
            PanchangFestivals.forLanguage(language).forEach { f ->
                if (festivalFalls(f, day, zone, ::monthOf)) {
                    out += Observance("festival-${f.english}", f.nameIn(language), f.english, festival = true)
                }
            }
        }
        if (settings.grahan && location != null) {
            // A visible grahan belongs to the local day it's seen on; one not
            // seen here, to the day of its greatest phase.
            Eclipses.between(day - DAY, day + 2 * DAY, location.first, location.second).forEach { e ->
                val anchor = e.visibleStart ?: e.maxMillis
                if (anchor >= day && anchor < addDays(day, 1, zone)) out += grahanObservance(e, language)
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

    /** Whether festival [f] is on the calendar day [day] — see [FestivalRule]. */
    private fun festivalFalls(
        f: Festival,
        day: Long,
        zone: TimeZone,
        monthOf: (ObserveAt, Paksha?, Int) -> Pair<String, Boolean>?,
    ): Boolean = when (val rule = f.rule) {
        is FestivalRule.Lunar -> monthOf(f.at, rule.paksha, rule.tithi)?.let { (name, adhik) -> name == rule.month && !adhik } ?: false
        is FestivalRule.Ingress -> crosses(HinduPanchang.sunSiderealAt(day), HinduPanchang.sunSiderealAt(addDays(day, 1, zone)), rule.longitude)
        is FestivalRule.StarInSolarMonth -> starDay(day, zone, rule)
    }

    /** Whether the Sun's longitude went from [start] up through [target] (degrees, wrapping at 360) by [end]. */
    internal fun crosses(start: Double, end: Double, target: Double): Boolean {
        val span = (end - start + 360.0) % 360.0
        val offset = (target - start + 360.0) % 360.0
        return offset > 0.0 && offset <= span
    }

    /**
     * The first day in a solar month on which the Moon is in the festival's nakshatra at sunrise (or skips over it
     * between two sunrises); the nakshatra comes round every 27.3 days, so a 30-day month can have it twice and only
     * the first counts.
     */
    private fun starDay(day: Long, zone: TimeZone, rule: FestivalRule.StarInSolarMonth): Boolean {
        val sunrise = day + 6 * HOUR
        val sun = HinduPanchang.sunSiderealAt(sunrise)
        if (((sun - rule.solarStart + 360.0) % 360.0) >= 30.0) return false
        val yesterday = HinduPanchang.nakshatraIndexAt(addDays(day, -1, zone) + 6 * HOUR)
        val today = HinduPanchang.nakshatraIndexAt(sunrise)
        val advance = (today - yesterday + 27) % 27
        val owned = advance > 0 && (rule.nakshatra - yesterday + 27) % 27 in 1..advance
        if (!owned) return false
        // The first in the month: a sidereal month earlier the Sun was still in the previous sign.
        val before = HinduPanchang.sunSiderealAt(sunrise - (27.32 * DAY).toLong())
        return ((before - rule.solarStart + 360.0) % 360.0) >= 30.0
    }

    /**
     * The ekadashi on [day], if any, for both traditions. Smarta keep the
     * ekadashi prevailing at sunrise (the first day when it spans two
     * sunrises). Vaishnava move a day later when dashami still prevails at
     * arunodaya (96 minutes before sunrise) or when the ekadashi also runs
     * through the next sunrise. Same day: one entry; different days:
     * "(स्मार्त)" on the first and "(वैष्णव)" on the second.
     */
    internal fun ekadashiOn(day: Long, zone: TimeZone, language: PanchangLanguage = PanchangLanguage.MARATHI): Observance? {
        val yesterday = addDays(day, -1, zone)
        val tomorrow = addDays(day, 1, zone)
        val smartaToday = smartaEkadashi(day, zone)
        val smartaYesterday = smartaEkadashi(yesterday, zone)
        val vaishnavaToday = (smartaToday != null && !vaishnavaMoves(day, tomorrow)) ||
            (smartaYesterday != null && vaishnavaMoves(yesterday, day))
        val source = smartaToday ?: smartaYesterday?.takeIf { vaishnavaToday } ?: return null
        val (month, paksha) = source
        val (local, eng) = ekadashiName(month.first, month.second, paksha, language)
        return when {
            smartaToday != null && vaishnavaToday -> Observance("ekadashi", local, eng, festival = false)
            smartaToday != null -> Observance("ekadashi", "$local ${PanchangObservanceNames.tradition(language, vaishnava = false)}", "$eng (smarta)", festival = false)
            else -> Observance("ekadashi", "$local ${PanchangObservanceNames.tradition(language, vaishnava = true)}", "$eng (vaishnava)", festival = false)
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
    internal fun grahanObservance(e: Eclipse, language: PanchangLanguage = PanchangLanguage.MARATHI): Observance {
        val kind = e.localKind ?: e.kind
        val english = "${kind.name.lowercase()} ${if (e.solar) "solar" else "lunar"} eclipse"
        return Observance(
            id = "grahan-${e.maxMillis}",
            name = PanchangObservanceNames.grahan(language, kind, e.solar),
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
            val grahan = eclipses.filter { (it.visibleStart ?: it.maxMillis).let { a -> a >= day && a < next } }.map { grahanObservance(it, settings.language) }
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
