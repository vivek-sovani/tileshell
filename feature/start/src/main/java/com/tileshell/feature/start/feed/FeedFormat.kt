package com.tileshell.feature.start.feed

import java.net.URLEncoder
import java.util.Calendar

/**
 * Pure helpers for the left feed page (the 3rd pager page). Kept free of Android
 * UI / Compose so the date, pager-commit, and search-url logic are JVM unit-testable.
 */

/** The glance row's date: a full weekday plus a compact "16 Jun 2026" subtitle. */
data class GlanceDate(val weekday: String, val sub: String)

private val FEED_WEEKDAYS = listOf(
    "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
)
private val FEED_MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** Formats the glance-row date from [calendar] (prototype `glanceRow`). */
fun feedGlanceDate(calendar: Calendar): GlanceDate {
    val weekday = FEED_WEEKDAYS[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = FEED_MONTHS[calendar.get(Calendar.MONTH)]
    val year = calendar.get(Calendar.YEAR)
    return GlanceDate(weekday = weekday, sub = "$day $month $year")
}

/**
 * The glance row's clock in 12-hour `h:mm am/pm` form (unpadded hour, zero-padded
 * minutes, lowercase suffix to match the launcher's lowercase styling).
 */
fun feedClock12(calendar: Calendar): String {
    val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
    val suffix = if (hour24 < 12) "am" else "pm"
    return "$hour12:${minute.toString().padStart(2, '0')} $suffix"
}

/**
 * The pager page to settle to after a horizontal drag, given the [base] page the
 * gesture started on and the live [pos] at release. Pages are ordered
 * `feed = -1, start = 0, apps = +1`; a net travel past 0.28 of a page width
 * commits to the adjacent page (prototype `d>0.28 / d<-0.28`), otherwise it
 * springs back. The result is the nearest whole page, clamped to [-1, 1].
 */
fun pagerCommitTarget(base: Float, pos: Float): Float {
    val delta = pos - base
    val target = when {
        delta > 0.28f -> base + 1f
        delta < -0.28f -> base - 1f
        else -> base
    }
    return Math.round(target.coerceIn(-1f, 1f)).toFloat()
}

/**
 * Google web-search URL for [query], used as the browser fallback when no app
 * handles `ACTION_WEB_SEARCH`. The query is URL-encoded so spaces/symbols survive.
 */
fun googleSearchUrl(query: String): String =
    "https://www.google.com/search?q=" + URLEncoder.encode(query.trim(), "UTF-8")
