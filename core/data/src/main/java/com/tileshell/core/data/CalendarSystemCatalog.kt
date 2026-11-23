package com.tileshell.core.data

/**
 * One non-Gregorian calendar system the "calendar systems" tile can show
 * alongside the Roman (Gregorian) date. [icuCalendarKeyword] is the Unicode
 * locale `ca` extension value `android.icu.text.DateFormat` understands
 * (e.g. `Locale.Builder().setUnicodeLocaleKeyword("ca", icuCalendarKeyword)`)
 * — null only for [HINDU_PANCHANG_ID], whose tithi/paksha math has no ICU
 * equivalent at all (see [HinduPanchang]) and is computed directly instead.
 */
data class CalendarSystem(val id: String, val displayName: String, val icuCalendarKeyword: String?)

const val HINDU_PANCHANG_ID = "hindu"

val CALENDAR_SYSTEMS: List<CalendarSystem> = listOf(
    CalendarSystem(HINDU_PANCHANG_ID, "Hindu (Panchang)", icuCalendarKeyword = null),
    CalendarSystem("islamic", "Islamic (Hijri)", "islamic"),
    CalendarSystem("hebrew", "Hebrew", "hebrew"),
    CalendarSystem("chinese", "Chinese", "chinese"),
    CalendarSystem("buddhist", "Buddhist", "buddhist"),
    CalendarSystem("persian", "Persian", "persian"),
    CalendarSystem("coptic", "Coptic", "coptic"),
    CalendarSystem("ethiopic", "Ethiopic", "ethiopic"),
)

fun calendarSystemFor(id: String): CalendarSystem? = CALENDAR_SYSTEMS.find { it.id == id }

/**
 * `"sunday, 30 august 2026"` — the Roman (Gregorian) date in full text, the
 * "calendar systems" tile's back face. Plain `java.text`/`java.util` (not
 * `android.icu`) so this stays pure and unit-testable; [Locale.ENGLISH] pins
 * the month/weekday names regardless of device locale, then lowercased to
 * match this app's lowercase tile-text convention.
 */
fun formatRomanDate(epochMillis: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
    val format = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.ENGLISH)
    format.timeZone = zone
    return format.format(java.util.Date(epochMillis)).lowercase(java.util.Locale.ENGLISH)
}


/**
 * The calendar system to preselect on the calendar hub's "calendar systems" page for a region (an ISO country
 * code), or null where none stands out: the page then just offers the chips. Only a first guess; the choice is
 * the user's (someone from India living abroad picks Hindu by hand).
 */
fun defaultCalendarSystemFor(countryCode: String?): String? = when (countryCode?.uppercase()) {
    "IN", "NP" -> HINDU_PANCHANG_ID
    "SA", "AE", "QA", "KW", "BH", "OM", "YE", "JO", "IQ", "SY", "LB", "LY", "TN", "DZ", "MA", "SD", "PK", "BD", "AF", "MY", "BN", "ID" -> "islamic"
    "IL" -> "hebrew"
    "CN", "TW", "HK", "MO" -> "chinese"
    "IR" -> "persian"
    "TH", "MM", "KH", "LA", "LK" -> "buddhist"
    "ET", "ER" -> "ethiopic"
    "EG" -> "coptic"
    else -> null
}
