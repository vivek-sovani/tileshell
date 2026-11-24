package com.tileshell.feature.livetiles.health

import java.time.Instant
import java.time.ZoneId

/**
 * Steps by hour. Each reading of the day's total puts the difference from the previous reading into the hour the
 * reading fell in, so a finished hour is as exact as the readings were frequent and the current hour grows with
 * the next reading. Pure and unit-tested; [HealthStore] holds and saves it.
 *
 * Hours are local "hours since 1970" ([hourIndex]); days are local epoch days.
 */
data class HourlySteps(
    /** Steps in each local hour, keyed by [hourIndex]. */
    val hours: Map<Long, Int> = emptyMap(),
    /** The last total seen for a day, keyed by epoch day, to take the next difference from. */
    val lastTotals: Map<Long, Int> = emptyMap(),
)

const val KEEP_HOURLY_DAYS = 14

/** The local hour number of [millis] (hours since 1970 in [zone]). */
fun hourIndex(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val offset = zone.rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds * 1000L
    return Math.floorDiv(millis + offset, 3_600_000L)
}

/**
 * Notes that the day [epochDay] stood at [total] steps during hour [hour]. The first reading of a day only sets the
 * starting point (nothing is guessed for steps taken before it); a lower total (the counter was reset) moves the
 * starting point down without taking steps away. Days older than [KEEP_HOURLY_DAYS] are dropped.
 */
fun recordHourly(state: HourlySteps, epochDay: Long, hour: Long, total: Int): HourlySteps {
    val last = state.lastTotals[epochDay]
    val delta = if (last == null) 0 else (total - last).coerceAtLeast(0)
    val hours = if (delta > 0) state.hours + (hour to ((state.hours[hour] ?: 0) + delta)) else state.hours
    val totals = state.lastTotals + (epochDay to total)
    val firstDay = epochDay - KEEP_HOURLY_DAYS + 1
    return HourlySteps(
        hours = hours.filterKeys { it >= firstDay * 24 },
        lastTotals = totals.filterKeys { it >= firstDay },
    )
}

/** The 24 hourly step counts of [epochDay], midnight first. */
fun dayHours(state: HourlySteps, epochDay: Long): List<Int> =
    (0 until 24).map { state.hours[epochDay * 24 + it] ?: 0 }

/** The hour of the day (0..23) with the most steps, or null when the day has none. */
fun busiestHour(hours: List<Int>): Int? {
    val best = hours.withIndex().maxByOrNull { it.value } ?: return null
    return if (best.value > 0) best.index else null
}

/** "8–9 am", "11 am–12 pm", "12–1 am". */
fun hourRangeLabel(hour: Int): String {
    fun h12(h: Int) = if (h % 12 == 0) 12 else h % 12
    fun ap(h: Int) = if (h % 24 < 12) "am" else "pm"
    val end = (hour + 1) % 24
    return if (ap(hour) == ap(end)) "${h12(hour)}–${h12(end)} ${ap(end)}"
    else "${h12(hour)} ${ap(hour)}–${h12(end)} ${ap(end)}"
}

/** Encodes as lines `h<TAB>hour<TAB>steps` and `t<TAB>day<TAB>total`. */
fun encodeHourly(state: HourlySteps): String =
    (state.hours.toSortedMap().map { "h\t${it.key}\t${it.value}" } + state.lastTotals.toSortedMap().map { "t\t${it.key}\t${it.value}" })
        .joinToString("\n")

/** Tolerant: lines that do not parse are skipped. */
fun decodeHourly(text: String): HourlySteps {
    val hours = mutableMapOf<Long, Int>()
    val totals = mutableMapOf<Long, Int>()
    text.lineSequence().forEach { line ->
        val f = line.split('\t')
        if (f.size < 3) return@forEach
        val k = f[1].toLongOrNull() ?: return@forEach
        val v = f[2].toIntOrNull() ?: return@forEach
        when (f[0]) {
            "h" -> hours[k] = v
            "t" -> totals[k] = v
        }
    }
    return HourlySteps(hours, totals)
}
