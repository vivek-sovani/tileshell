package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AgendaDayLabelTest {

    @Test
    fun `formats weekday and day of month`() {
        assertEquals("wed 17", agendaDayLabel(dayOfWeek = 4, dayOfMonth = 17))
        assertEquals("sun 1", agendaDayLabel(dayOfWeek = 1, dayOfMonth = 1))
        assertEquals("sat 31", agendaDayLabel(dayOfWeek = 7, dayOfMonth = 31))
    }
}

class AgendaTimeLabelTest {

    @Test
    fun `formats morning times with an a suffix`() {
        assertEquals("9:30a", agendaTimeLabel(9, 30))
        assertEquals("12:00a", agendaTimeLabel(0, 0))
        assertEquals("11:59a", agendaTimeLabel(11, 59))
    }

    @Test
    fun `formats afternoon times with a p suffix`() {
        assertEquals("12:00p", agendaTimeLabel(12, 0))
        assertEquals("4:00p", agendaTimeLabel(16, 0))
        assertEquals("11:59p", agendaTimeLabel(23, 59))
    }

    @Test
    fun `pads single-digit minutes`() {
        assertEquals("9:05a", agendaTimeLabel(9, 5))
    }
}

class GroupEventsByDayTest {

    private fun day(offset: Int): Long = TimeZone.getDefault().let {
        Calendar.getInstance().apply {
            timeInMillis = 0L
            add(Calendar.DAY_OF_YEAR, offset)
        }.timeInMillis
    }

    @Test
    fun `buckets events into the day they start in`() {
        val dayStarts = listOf(day(0), day(1), day(2))
        val events = listOf(
            AgendaEvent("first day", day(0) + 1000, day(0) + 2000, false, eventId = 1),
            AgendaEvent("third day", day(2) + 500, day(2) + 600, false, eventId = 2),
        )
        val buckets = groupEventsByDay(events, dayStarts)
        assertEquals(3, buckets.size)
        assertEquals(listOf("first day"), buckets[0].map { it.title })
        assertEquals(emptyList<String>(), buckets[1].map { it.title })
        assertEquals(listOf("third day"), buckets[2].map { it.title })
    }

    @Test
    fun `an event before the first day start is dropped, not misattributed to the first bucket`() {
        // Real bug (user-reported): an all-day event overlapping into the
        // queried range only via its UTC-storage bleed used to get dumped
        // into "today" instead of being recognized as belonging to an
        // earlier day this call isn't even showing.
        val dayStarts = listOf(day(5), day(6))
        val events = listOf(AgendaEvent("early", day(0), day(0) + 1000, false, eventId = 1))
        val buckets = groupEventsByDay(events, dayStarts)
        assertEquals(emptyList<String>(), buckets[0].map { it.title })
        assertEquals(emptyList<String>(), buckets[1].map { it.title })
    }

    @Test
    fun `an event at or after the last day start lands in the last bucket`() {
        val dayStarts = listOf(day(0), day(1))
        val events = listOf(AgendaEvent("late", day(10), day(10) + 1000, false, eventId = 1))
        val buckets = groupEventsByDay(events, dayStarts)
        assertEquals(listOf("late"), buckets[1].map { it.title })
    }

    @Test
    fun `no events yields all-empty buckets`() {
        val buckets = groupEventsByDay(emptyList(), listOf(day(0), day(1)))
        assertEquals(listOf(emptyList<AgendaEvent>(), emptyList()), buckets)
    }

    @Test
    fun `an all-day event's UTC-midnight storage doesn't bleed it into the next local day`() {
        // Reproduces the exact real-world bug (confirmed against the live
        // provider on-device): an all-day event "on the 21st" is stored as
        // begin = UTC midnight of the 21st, end = UTC midnight of the 22nd.
        // In a positive-UTC-offset zone (IST, +5:30) that end becomes
        // 05:30 *local* on the 22nd — bleeding into a "this week starting
        // today (the 22nd)" query — but it must still be grouped as the
        // 21st's event, not attributed to the 22nd.
        val utc = TimeZone.getTimeZone("UTC")
        val begin21Utc = Calendar.getInstance(utc).apply { set(2026, Calendar.SEPTEMBER, 21, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val end22Utc = Calendar.getInstance(utc).apply { set(2026, Calendar.SEPTEMBER, 22, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val local22Start = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 22, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val local21Start = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 21, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val local23Start = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 23, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

        val event = AgendaEvent("Indranil", begin21Utc, end22Utc, allDay = true, eventId = 1)
        // "this week" starting today (the 22nd) — the 21st isn't even in the
        // window, so the event must disappear entirely, not show under the 22nd.
        val bucketsFromToday = groupEventsByDay(listOf(event), listOf(local22Start, local23Start))
        assertEquals(emptyList<String>(), bucketsFromToday[0].map { it.title })

        // With the 21st actually in the window, it must land there, not the 22nd.
        val bucketsWith21st = groupEventsByDay(listOf(event), listOf(local21Start, local22Start))
        assertEquals(listOf("Indranil"), bucketsWith21st[0].map { it.title })
        assertEquals(emptyList<String>(), bucketsWith21st[1].map { it.title })
    }
}

class CurrentMonthDayStartsTest {

    @Test
    fun `returns one entry per day in the month`() {
        // 2026-02-15 12:00:00 local time — February 2026 has 28 days.
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.FEBRUARY, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val starts = currentMonthDayStarts(cal.timeInMillis)
        assertEquals(28, starts.size)

        val first = Calendar.getInstance().apply { timeInMillis = starts.first() }
        assertEquals(1, first.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, first.get(Calendar.HOUR_OF_DAY))

        val last = Calendar.getInstance().apply { timeInMillis = starts.last() }
        assertEquals(28, last.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `handles a 31-day month`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 3, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(31, currentMonthDayStarts(cal.timeInMillis).size)
    }
}

class StartOfDayMillisTest {

    @Test
    fun `zeroes out the time of day`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, 15, 45, 30)
        }.timeInMillis
        val start = startOfDayMillis(0, now)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(10, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `offsets by the requested number of days`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, 15, 45, 30)
        }.timeInMillis
        val start = startOfDayMillis(7, now)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(17, cal.get(Calendar.DAY_OF_MONTH))
    }
}
