package com.tileshell.core.data.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * How a task's reminder repeats. Stored in `tasks.remindRepeat` as a short
 * code: "" (once), "daily", "weekly", "monthly", "yearly" or "days:N". A
 * repeating task is never copied — ticking it done moves it to its next date
 * ([nextReminderAt]) and it stays open.
 */
sealed class TaskRepeat {
    data object Once : TaskRepeat()
    data object Daily : TaskRepeat()
    data object Weekly : TaskRepeat()
    data object Monthly : TaskRepeat()
    data object Yearly : TaskRepeat()
    data class EveryDays(val days: Int) : TaskRepeat()

    val code: String
        get() = when (this) {
            Once -> ""
            Daily -> "daily"
            Weekly -> "weekly"
            Monthly -> "monthly"
            Yearly -> "yearly"
            is EveryDays -> "days:$days"
        }

    /** Lowercase label for the task row ("weekly", "every 3 days"); empty for once. */
    val label: String
        get() = when (this) {
            Once -> ""
            Daily -> "daily"
            Weekly -> "weekly"
            Monthly -> "monthly"
            Yearly -> "yearly"
            is EveryDays -> "every $days days"
        }

    companion object {
        const val MIN_DAYS = 2
        const val MAX_DAYS = 365

        /** Unknown or malformed codes read as [Once], so a bad value never loops. */
        fun decode(code: String?): TaskRepeat = when {
            code.isNullOrBlank() -> Once
            code == "daily" -> Daily
            code == "weekly" -> Weekly
            code == "monthly" -> Monthly
            code == "yearly" -> Yearly
            code.startsWith("days:") ->
                code.removePrefix("days:").toIntOrNull()
                    ?.takeIf { it in 1..MAX_DAYS }
                    ?.let { if (it == 1) Daily else EveryDays(it) }
                    ?: Once
            else -> Once
        }
    }
}

/** One step of [repeat] from [from] (wall-clock, so 9:00 am stays 9:00 am across DST). */
private fun step(from: ZonedDateTime, repeat: TaskRepeat, anchorDay: Int): ZonedDateTime = when (repeat) {
    TaskRepeat.Once -> from
    TaskRepeat.Daily -> from.plusDays(1)
    TaskRepeat.Weekly -> from.plusWeeks(1)
    // Months and years aim for [anchorDay], clamped to the month's length, so a
    // multi-period catch-up doesn't drift to the 28th. Only the next due time is
    // stored, so a series set on the 31st does settle on the 28th/30th after a
    // short month — accepted rather than adding an anchor column.
    TaskRepeat.Monthly -> from.plusMonths(1).let { it.withDayOfMonth(minOf(anchorDay, it.toLocalDate().lengthOfMonth())) }
    TaskRepeat.Yearly -> from.plusYears(1).let { it.withDayOfMonth(minOf(anchorDay, it.toLocalDate().lengthOfMonth())) }
    is TaskRepeat.EveryDays -> from.plusDays(repeat.days.toLong())
}

/**
 * The next due time for a repeating task being ticked done: the first
 * occurrence after both [remindAt] and [now], so a task overdue by several
 * periods jumps to its next future date instead of one period ahead. Null for
 * [TaskRepeat.Once]. [anchorDay] is the day of month the series started on
 * (defaults to [remindAt]'s own), used for monthly/yearly.
 */
fun nextReminderAt(
    remindAt: Long,
    repeat: TaskRepeat,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    anchorDay: Int = Instant.ofEpochMilli(remindAt).atZone(zone).dayOfMonth,
): Long? {
    if (repeat == TaskRepeat.Once) return null
    var t = step(Instant.ofEpochMilli(remindAt).atZone(zone), repeat, anchorDay)
    var guard = 0
    while (t.toInstant().toEpochMilli() <= now && guard++ < 100_000) {
        t = step(t, repeat, anchorDay)
    }
    return t.toInstant().toEpochMilli()
}

/** "9:00 am" — the app's 12-hour lowercase style. */
fun reminderClock(at: ZonedDateTime): String {
    val h = at.hour % 12
    return "${if (h == 0) 12 else h}:${at.minute.toString().padStart(2, '0')} ${if (at.hour < 12) "am" else "pm"}"
}

private val WEEKDAYS_SHORT = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
private val MONTHS_SHORT = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

/** "today" / "tomorrow" / "thu 1 oct" (plus the year when it isn't this year). */
fun reminderDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "today"
    today.plusDays(1) -> "tomorrow"
    today.minusDays(1) -> "yesterday"
    else -> buildString {
        append(WEEKDAYS_SHORT[date.dayOfWeek.value - 1]).append(' ')
        append(date.dayOfMonth).append(' ').append(MONTHS_SHORT[date.monthValue - 1])
        if (date.year != today.year) append(' ').append(date.year)
    }
}

/**
 * The line under a task: "tomorrow 9:00 am · weekly", or "overdue · mon 29 sep
 * 11:00 am" once past. Pure so it's unit-testable.
 */
fun reminderLine(remindAt: Long, repeat: TaskRepeat, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(remindAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val whenText = "${reminderDay(at.toLocalDate(), today)} ${reminderClock(at)}"
    val base = if (remindAt <= now) "overdue · $whenText" else whenText
    return if (repeat.label.isEmpty()) base else "$base · ${repeat.label}"
}

/**
 * A task's reminder is "due" (shown first on the tiles, with the count badge)
 * once its time has passed, it isn't done, and it isn't snoozed into the
 * future.
 */
fun isReminderDue(remindAt: Long?, snoozeAt: Long?, done: Boolean, now: Long): Boolean =
    !done && remindAt != null && remindAt <= now && (snoozeAt == null || snoozeAt <= now)

/**
 * The productivity hub's "scheduled today": open tasks whose reminder falls on
 * today's date, plus earlier ones still open (overdue), soonest first. Takes
 * plain (remindAt, item) pairs so it's pure and testable.
 */
fun <T> scheduledToday(tasks: List<T>, remindAtOf: (T) -> Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<T> {
    val endOfToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return tasks.filter { t -> remindAtOf(t)?.let { it < endOfToday } == true }.sortedBy { remindAtOf(it) }
}

/** "6:00 pm" for today, else "tomorrow 9:00 am" / "thu 1 oct 9:00 am" — compact for tiles. */
fun reminderShortWhen(remindAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(remindAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = reminderDay(at.toLocalDate(), today)
    return if (day == "today") reminderClock(at) else "$day ${reminderClock(at)}"
}

/**
 * The next reminder still to come (time after [now], counting a snooze as the
 * new time), for the tiles' "next" line. Pure over plain accessors.
 */
fun <T> nextScheduled(
    tasks: List<T>,
    remindAtOf: (T) -> Long?,
    snoozeAtOf: (T) -> Long?,
    now: Long,
): T? = tasks
    .mapNotNull { t -> (snoozeAtOf(t) ?: remindAtOf(t))?.takeIf { it > now }?.let { t to it } }
    .minByOrNull { it.second }
    ?.first
