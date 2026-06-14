package com.tileshell.feature.livetiles

import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * One agenda entry shown on the calendar tile (FR-2): a [title] and the
 * prototype's time line, e.g. `10:00 · 30m`.
 */
data class CalendarEvent(val title: String, val timeLine: String)

/**
 * What the calendar tile shows: the [next] upcoming event (front) and the
 * [following] one (back). Either may be null — no upcoming events reads as a
 * static degrade in the tile.
 */
data class CalendarFace(val next: CalendarEvent?, val following: CalendarEvent?)

/**
 * Today's date as shown on the calendar tile's base face: a lowercase [weekday],
 * the [day] of the month, and the lowercase [month]. Always available (no
 * permission needed), so the calendar tile shows the date even with no events.
 */
data class CalendarToday(
    val weekday: String,
    val day: Int,
    val month: String,
)

private val CALENDAR_WEEKDAYS = listOf(
    "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday",
)
private val CALENDAR_MONTHS = listOf(
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december",
)

/**
 * Builds a [CalendarToday] from calendar fields. Pure (no `Calendar.getInstance()`)
 * so the lowercase weekday/month formatting is unit-testable. [dayOfWeek] is
 * Calendar's 1=Sunday convention; [month0] is 0-based (0=January).
 */
fun calendarToday(
    dayOfWeek: Int,
    dayOfMonth: Int,
    month0: Int,
): CalendarToday =
    CalendarToday(
        weekday = CALENDAR_WEEKDAYS[dayOfWeek - 1],
        day = dayOfMonth,
        month = CALENDAR_MONTHS[month0],
    )

/**
 * The prototype's event time line: 24-hour `h:mm` start plus a compact duration
 * (`30m`, `1h`, `1h 30m`). Pure so it is unit-testable. A non-positive or
 * absurdly long [durationMin] drops the duration (all-day / open-ended events).
 */
fun eventTimeLine(hour24: Int, minute: Int, durationMin: Int): String {
    val start = "$hour24:${minute.toString().padStart(2, '0')}"
    val dur = formatDuration(durationMin)
    return if (dur == null) start else "$start · $dur"
}

private fun formatDuration(durationMin: Int): String? {
    if (durationMin <= 0 || durationMin >= 24 * 60) return null
    val h = durationMin / 60
    val m = durationMin % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/** Builds a [CalendarEvent] from a title and raw begin/end epoch millis. */
fun calendarEvent(title: String, beginMillis: Long, endMillis: Long): CalendarEvent {
    val c = Calendar.getInstance().apply { timeInMillis = beginMillis }
    val durationMin = ((endMillis - beginMillis) / 60_000L).toInt()
    return CalendarEvent(
        title = title.ifBlank { "(untitled)" },
        timeLine = eventTimeLine(
            hour24 = c.get(Calendar.HOUR_OF_DAY),
            minute = c.get(Calendar.MINUTE),
            durationMin = durationMin,
        ),
    )
}

/**
 * Reads the next two upcoming events from the system calendar provider
 * (FR-2 calendar tile). Caller must hold READ_CALENDAR — this throws
 * SecurityException otherwise, so guard the call. Returns at most two events
 * starting from [nowMillis] within the next [windowHours]; empty when nothing is
 * coming up (the tile then degrades to static).
 */
fun queryUpcomingEvents(
    context: Context,
    nowMillis: Long = System.currentTimeMillis(),
    windowHours: Long = 36,
): CalendarFace {
    val end = nowMillis + TimeUnit.HOURS.toMillis(windowHours)
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(nowMillis.toString())
        .appendPath(end.toString())
        .build()
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
    )
    val events = ArrayList<CalendarEvent>(2)
    context.contentResolver.query(
        uri,
        projection,
        "${CalendarContract.Instances.BEGIN} >= ?",
        arrayOf(nowMillis.toString()),
        "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { cursor ->
        while (cursor.moveToNext() && events.size < 2) {
            val title = cursor.getString(0).orEmpty()
            val begin = cursor.getLong(1)
            val endAt = cursor.getLong(2)
            events.add(calendarEvent(title, begin, endAt))
        }
    }
    return CalendarFace(
        next = events.getOrNull(0),
        following = events.getOrNull(1),
    )
}
