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
            AgendaEvent("first day", day(0) + 1000, day(0) + 2000, false),
            AgendaEvent("third day", day(2) + 500, day(2) + 600, false),
        )
        val buckets = groupEventsByDay(events, dayStarts)
        assertEquals(3, buckets.size)
        assertEquals(listOf("first day"), buckets[0].map { it.title })
        assertEquals(emptyList<String>(), buckets[1].map { it.title })
        assertEquals(listOf("third day"), buckets[2].map { it.title })
    }

    @Test
    fun `an event before the first day start still lands in the first bucket`() {
        val dayStarts = listOf(day(5), day(6))
        val events = listOf(AgendaEvent("early", day(0), day(0) + 1000, false))
        val buckets = groupEventsByDay(events, dayStarts)
        assertEquals(listOf("early"), buckets[0].map { it.title })
    }

    @Test
    fun `an event at or after the last day start lands in the last bucket`() {
        val dayStarts = listOf(day(0), day(1))
        val events = listOf(AgendaEvent("late", day(10), day(10) + 1000, false))
        val buckets = groupEventsByDay(events, dayStarts)
        assertEquals(listOf("late"), buckets[1].map { it.title })
    }

    @Test
    fun `no events yields all-empty buckets`() {
        val buckets = groupEventsByDay(emptyList(), listOf(day(0), day(1)))
        assertEquals(listOf(emptyList<AgendaEvent>(), emptyList()), buckets)
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
