package com.tileshell.feature.livetiles

import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

/**
 * One event instance in a queried date range for the Calendar Hub's own
 * day/week/month agenda views — a different, richer shape from
 * [CalendarEvent] (a single pre-formatted "next up" time line): carries the
 * raw start/end millis, an all-day flag, and the underlying [eventId] (so a
 * row can be opened in the system calendar app) so a day card can group,
 * sort, format, and open events itself. [queryUpcomingEvents] is untouched
 * and still powers the live tile's own "next 1-2 events" face.
 */
data class AgendaEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val eventId: Long,
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
        CalendarContract.Instances.EVENT_ID,
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
                        eventId = cursor.getLong(4),
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
 * The day an event should be grouped under. For a timed event this is just
 * [AgendaEvent.startMillis] (a real instant). For an **all-day** event, the
 * provider always stores begin/end as UTC-midnight boundaries of the actual
 * calendar date (Android's own storage convention for `ALL_DAY` instances) —
 * not a real device-local instant. Reading that raw value back with a
 * local-timezone `Calendar` can shift it into the *next* local day by the
 * device's own UTC offset: e.g. in IST (+5:30), a 21st all-day event's
 * stored end (UTC midnight of the 22nd) is 05:30 *local* on the 22nd, so it
 * visibly "bleeds" into the next day — normalizing back to local midnight of
 * the same UTC-encoded calendar date keeps it on the day it's actually on
 * (user-reported, confirmed against the real provider data on-device: an
 * event on the 21st was showing grouped under the 22nd for exactly this
 * reason).
 */
private fun effectiveDayStartMillis(event: AgendaEvent): Long {
    if (!event.allDay) return event.startMillis
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = event.startMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

/**
 * Buckets [events] by which entry in [dayStartsAscending] (each the start of
 * a distinct calendar day, already sorted ascending) its own [effectiveDayStartMillis]
 * falls into. An event whose effective day is before every entry (e.g. it
 * only overlaps into the queried range from the day before, like an all-day
 * event's UTC-bleed described above) is dropped rather than misattributed to
 * the first bucket — it genuinely isn't on any day this call is showing.
 * Pure; used by the hub's day-grid pages to group a flat [queryAgendaEvents]
 * result per day card.
 */
fun groupEventsByDay(events: List<AgendaEvent>, dayStartsAscending: List<Long>): List<List<AgendaEvent>> {
    val buckets = List(dayStartsAscending.size) { mutableListOf<AgendaEvent>() }
    for (event in events) {
        val effectiveStart = effectiveDayStartMillis(event)
        val index = dayStartsAscending.indexOfLast { it <= effectiveStart }
        if (index < 0) continue
        buckets[index].add(event)
    }
    return buckets
}
