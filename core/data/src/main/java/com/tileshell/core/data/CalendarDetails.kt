package com.tileshell.core.data

import java.time.LocalDate

/**
 * What a calendar system says about one day, before any wording: the raw fields. [month0] counts from 0 the way
 * ICU does (so Ramadan is 8 and a Hebrew leap year's Adar II is 6); [year] is the system's own year number
 * (for the Chinese calendar, the year within its 60-year cycle).
 */
data class CalFields(val day: Int, val month0: Int, val year: Int, val leapMonth: Boolean = false)

/** Reads a calendar system day by day. The Android implementation is ICU; tests use a fake. */
interface CalendarSource {
    fun fieldsOn(epochDay: Long): CalFields
    /** Days in the month [epochDay] falls in. */
    fun monthLength(epochDay: Long): Int
    /** Months in the year [epochDay] falls in (13 in a Hebrew leap year). */
    fun monthsInYear(epochDay: Long): Int
    fun monthName(epochDay: Long): String
}

/** A fixed observance: [month0] / [day] in the system's own calendar. */
data class SpecialDay(val name: String, val month0: Int, val day: Int)

/** Everything the tile shows for today in one system. */
data class SystemDay(
    val day: Int,
    val monthName: String,
    val monthNumber: Int,
    val monthsInYear: Int,
    val year: Int,
    val yearText: String,
    val eraLabel: String,
    val monthLength: Int,
    val daysLeftInMonth: Int,
    /** Offset of day 1 in a Monday-first week (0 = Monday), for the month grid. */
    val firstWeekdayOffset: Int,
    /** "year of the wood snake" and the like; null for systems without one. */
    val yearNote: String?,
)

data class UpcomingSpecial(val name: String, val daysAway: Int)

/** Era names, as short labels. */
fun eraLabelFor(systemId: String): String = when (systemId) {
    "islamic" -> "AH"
    "hebrew" -> "AM"
    "persian" -> "AP"
    "buddhist" -> "BE"
    "coptic" -> "AM"
    "ethiopic" -> "EE"
    else -> ""
}

private val ANIMALS = listOf("rat", "ox", "tiger", "rabbit", "dragon", "snake", "horse", "goat", "monkey", "rooster", "dog", "pig")
private val ELEMENTS = listOf("wood", "fire", "earth", "metal", "water")

/** "wood snake" for year [cycleYear] (1..60) of the Chinese 60-year cycle; year 1 is wood rat. */
fun chineseYearName(cycleYear: Int): String {
    val i = ((cycleYear - 1) % 60 + 60) % 60
    return "${ELEMENTS[(i % 10) / 2]} ${ANIMALS[i % 12]}"
}

private val CHINESE_MONTHS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth")

fun chineseMonthName(month0: Int, leap: Boolean): String =
    (if (leap) "leap " else "") + "${CHINESE_MONTHS.getOrElse(month0) { "month ${month0 + 1}" }} month"

/** Observances worth counting down to, per system (not the Hindu one: the panchang hub has its own). */
val SPECIAL_DAYS: Map<String, List<SpecialDay>> = mapOf(
    "islamic" to listOf(
        SpecialDay("islamic new year", 0, 1),
        SpecialDay("ashura", 0, 10),
        SpecialDay("mawlid", 2, 12),
        SpecialDay("start of ramadan", 8, 1),
        SpecialDay("laylat al-qadr (27th night)", 8, 27),
        SpecialDay("eid al-fitr", 9, 1),
        SpecialDay("day of arafah", 11, 9),
        SpecialDay("eid al-adha", 11, 10),
    ),
    "hebrew" to listOf(
        SpecialDay("rosh hashanah", 0, 1),
        SpecialDay("yom kippur", 0, 10),
        SpecialDay("sukkot", 0, 15),
        SpecialDay("hanukkah", 2, 25),
        SpecialDay("tu bishvat", 4, 15),
        SpecialDay("purim", 6, 14),
        SpecialDay("passover", 7, 15),
        SpecialDay("shavuot", 9, 6),
        SpecialDay("tisha b'av", 11, 9),
    ),
    "chinese" to listOf(
        SpecialDay("chinese new year", 0, 1),
        SpecialDay("lantern festival", 0, 15),
        SpecialDay("dragon boat festival", 4, 5),
        SpecialDay("qixi", 6, 7),
        SpecialDay("mid-autumn festival", 7, 15),
        SpecialDay("double ninth", 8, 9),
    ),
    "buddhist" to listOf(
        SpecialDay("thai new year's day", 0, 1),
        SpecialDay("songkran", 3, 13),
        SpecialDay("constitution day", 11, 10),
    ),
    "persian" to listOf(
        SpecialDay("nowruz", 0, 1),
        SpecialDay("sizdah bedar", 0, 13),
        SpecialDay("mehregan", 6, 16),
        SpecialDay("yalda night", 8, 30),
    ),
    "coptic" to listOf(
        SpecialDay("coptic new year (nayrouz)", 0, 1),
        SpecialDay("coptic christmas", 3, 29),
        SpecialDay("epiphany", 4, 11),
    ),
    "ethiopic" to listOf(
        SpecialDay("enkutatash (new year)", 0, 1),
        SpecialDay("meskel", 0, 17),
        SpecialDay("genna (christmas)", 3, 29),
        SpecialDay("timkat (epiphany)", 4, 11),
    ),
)

/** Where today falls in a month grid that starts on Monday: how many blank cells before day 1. */
fun firstWeekdayOffset(epochDay: Long, dayOfMonth: Int): Int =
    LocalDate.ofEpochDay(epochDay - (dayOfMonth - 1)).dayOfWeek.value - 1

/** Today in [systemId], from [source]. */
fun describeSystemDay(systemId: String, source: CalendarSource, epochDay: Long): SystemDay {
    val f = source.fieldsOn(epochDay)
    val length = source.monthLength(epochDay)
    val name = if (systemId == "chinese") chineseMonthName(f.month0, f.leapMonth) else source.monthName(epochDay)
    val era = eraLabelFor(systemId)
    val yearText = if (systemId == "chinese") "" else "${f.year}${if (era.isNotEmpty()) " $era" else ""}"
    return SystemDay(
        day = f.day,
        monthName = name.lowercase(),
        monthNumber = f.month0 + 1,
        monthsInYear = source.monthsInYear(epochDay),
        year = f.year,
        yearText = yearText,
        eraLabel = era,
        monthLength = length,
        daysLeftInMonth = (length - f.day).coerceAtLeast(0),
        firstWeekdayOffset = firstWeekdayOffset(epochDay, f.day),
        yearNote = if (systemId == "chinese") "year of the ${chineseYearName(f.year)}" else null,
    )
}

/**
 * The next [count] observances of [systemId] from [fromEpochDay] (today counts, as 0 days away), looking up to
 * [horizonDays] ahead, soonest first. Chinese observances never fall in a leap month.
 */
fun upcomingSpecialDays(
    systemId: String,
    source: CalendarSource,
    fromEpochDay: Long,
    count: Int = 2,
    horizonDays: Int = 400,
): List<UpcomingSpecial> {
    val table = SPECIAL_DAYS[systemId] ?: return emptyList()
    val found = mutableListOf<UpcomingSpecial>()
    var offset = 0
    while (offset <= horizonDays && found.size < count) {
        val f = source.fieldsOn(fromEpochDay + offset)
        table.firstOrNull { it.month0 == f.month0 && it.day == f.day && !f.leapMonth }
            ?.let { found += UpcomingSpecial(it.name, offset) }
        offset++
    }
    return found
}

/** "today", "tomorrow", "in 12 days". */
fun daysAwayText(days: Int): String = when (days) {
    0 -> "today"
    1 -> "tomorrow"
    else -> "in $days days"
}
