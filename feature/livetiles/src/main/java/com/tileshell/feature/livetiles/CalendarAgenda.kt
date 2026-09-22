package com.tileshell.feature.livetiles

import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar

/**
 * One event instance in a queried date range for the Calendar Hub's own
 * day/week/month agenda views — a different, richer shape from
 * [CalendarEvent] (a single pre-formatted "next up" time line): carries the
 * raw start/end millis and an all-day flag so a day card can group, sort,
 * and format events itself. [queryUpcomingEvents] is untouched and still
 * powers the live tile's own "next 1-2 events" face.
 */
data class AgendaEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

/**
 * Every event instance overlapping `[startMillis, endMillis)`, sorted by
 * start time. Requires READ_CALENDAR — callers must guard with
 * [rememberPermissionGranted] first, same as the live tile, and run this off
 * the main thread themselves (matching [queryUpcomingEvents]'s own
 * convention of staying a plain synchronous function, not suspend).
 */
fun queryAgendaEvents(context: Context, startMillis: Long, endMillis: Long): List<AgendaEvent> {
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(startMillis.toString())
        .appendPath(endMillis.toString())
        .build()
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
    )
    val events = mutableListOf<AgendaEvent>()
    runCatching {
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    events += AgendaEvent(
                        title = cursor.getString(0)?.ifBlank { "(untitled)" } ?: "(untitled)",
                        startMillis = cursor.getLong(1),
                        endMillis = cursor.getLong(2),
                        allDay = cursor.getInt(3) != 0,
                    )
                }
            }
    }
    return events
}

/** Start-of-day epoch millis for the day [daysFromNow] days after today
 * (device-local time zone; 0 = today). Used to build the queried range for
 * "this week"/"next"/"month" without each caller re-deriving day boundaries. */
fun startOfDayMillis(daysFromNow: Int, nowMillis: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, daysFromNow)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** "wed 17" — a day card's own header label. [dayOfWeek] is Calendar's
 * 1=Sunday convention. Pure, so it's unit-testable. */
fun agendaDayLabel(dayOfWeek: Int, dayOfMonth: Int): String =
    "${AGENDA_WEEKDAY_ABBR[dayOfWeek - 1]} $dayOfMonth"

/** "9:30a" / "4:00p" — the hub's own compact 12-hour event time label (the
 * live tile's [eventTimeLine] is a distinct, 24-hour style meant for a
 * single-line tile face, not reused here). Pure. */
fun agendaTimeLabel(hour24: Int, minute: Int): String {
    val period = if (hour24 < 12) "a" else "p"
    val hour12 = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }
    return "$hour12:${minute.toString().padStart(2, '0')}$period"
}

private val AGENDA_WEEKDAY_ABBR = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

/** Start-of-day epoch millis for every day in the current calendar month (day
 * 1 through the last day), device-local time zone — the "month" pivot's own
 * day range. Pure given [nowMillis]. */
fun currentMonthDayStarts(nowMillis: Long = System.currentTimeMillis()): List<Long> {
    val base = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val daysInMonth = base.getActualMaximum(Calendar.DAY_OF_MONTH)
    return (0 until daysInMonth).map { offset ->
        (base.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }.timeInMillis
    }
}

/**
 * Buckets [events] by which entry in [dayStartsAscending] (each the start of
 * a distinct calendar day, already sorted ascending) its own start time falls
 * into: the first bucket also catches anything earlier, and the last bucket
 * anything at/after the final day start. Pure; used by the hub's day-grid
 * pages to group a flat [queryAgendaEvents] result per day card.
 */
fun groupEventsByDay(events: List<AgendaEvent>, dayStartsAscending: List<Long>): List<List<AgendaEvent>> {
    val buckets = List(dayStartsAscending.size) { mutableListOf<AgendaEvent>() }
    for (event in events) {
        val index = dayStartsAscending.indexOfLast { it <= event.startMillis }.coerceAtLeast(0)
        buckets[index].add(event)
    }
    return buckets
}
