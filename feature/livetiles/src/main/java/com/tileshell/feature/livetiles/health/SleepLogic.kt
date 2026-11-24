package com.tileshell.feature.livetiles.health

import com.tileshell.feature.livetiles.BatterySample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The sleep page's estimate. TileShell cannot know when you slept; it reads when the phone was left alone: the longest
 * stretch between 8 pm and noon with the screen off, where a short look at the phone (under [BREAK_MS]) is counted as
 * a pick-up but does not end the stretch, and longer use does. The use comes from TileShell's own battery log
 * (screen on / off) and, when usage access is on, app use seen in usage events. All pure, unit-tested, and always
 * shown as an estimate.
 */

/** A span of phone use, epoch millis. */
data class UseSpan(val start: Long, val end: Long)

/** A look at the phone shorter than this is a pick-up; longer use ends the stretch. */
const val BREAK_MS = 10L * 60_000
/** Uses closer together than this are one use. */
const val MERGE_GAP_MS = 5L * 60_000
/** A stretch shorter than this is a nap or a quiet evening, not a night. */
const val MIN_SLEEP_MS = 2L * 3_600_000
private const val EDGE_BLIP_MS = 30L * 60_000

data class SleepEstimate(
    val start: Long,
    val end: Long,
    /** The short looks at the phone inside the stretch. */
    val pickups: List<UseSpan>,
) {
    val durationMs: Long get() = end - start
}

/** Screen-on spans from the battery log; a state is assumed to last at most [capMs] until the next reading. */
fun screenOnSpans(samples: List<BatterySample>, nowMs: Long, capMs: Long = 3L * 3_600_000): List<UseSpan> {
    val sorted = samples.sortedBy { it.time }
    val out = mutableListOf<UseSpan>()
    sorted.forEachIndexed { i, s ->
        if (!s.screenOn) return@forEachIndexed
        val next = sorted.getOrNull(i + 1)?.time ?: nowMs
        out += UseSpan(s.time, minOf(next, s.time + capMs).coerceAtLeast(s.time))
    }
    return out
}

/** App-use moments (an app coming to the front) as one-minute spans. */
fun usageMoments(timesMs: List<Long>): List<UseSpan> = timesMs.map { UseSpan(it, it + 60_000L) }

/** Joins spans that overlap or are within [gap] of each other, ascending. */
fun mergeSpans(spans: List<UseSpan>, gap: Long = MERGE_GAP_MS): List<UseSpan> {
    val out = mutableListOf<UseSpan>()
    spans.filter { it.end >= it.start }.sortedBy { it.start }.forEach { s ->
        val last = out.lastOrNull()
        if (last != null && s.start <= last.end + gap) out[out.lastIndex] = UseSpan(last.start, maxOf(last.end, s.end))
        else out += s
    }
    return out
}

/**
 * The night in [windowStart]..[windowEnd] (end already limited to now by the caller if needed), or null when no stretch
 * reaches [MIN_SLEEP_MS]. [use] is every sign of the phone in use, in any order.
 */
fun estimateSleep(use: List<UseSpan>, windowStart: Long, windowEnd: Long): SleepEstimate? {
    if (windowEnd <= windowStart) return null
    val merged = mergeSpans(use.filter { it.end > windowStart && it.start < windowEnd }.map { UseSpan(maxOf(it.start, windowStart), minOf(it.end, windowEnd)) })
    val breaks = merged.filter { it.end - it.start >= BREAK_MS }
    val blips = merged.filter { it.end - it.start < BREAK_MS }
    // The quiet stretches between long uses (and the window's ends).
    val gaps = mutableListOf<UseSpan>()
    var cursor = windowStart
    breaks.forEach { b ->
        if (b.start > cursor) gaps += UseSpan(cursor, b.start)
        cursor = maxOf(cursor, b.end)
    }
    if (cursor < windowEnd) gaps += UseSpan(cursor, windowEnd)
    val best = gaps.maxByOrNull { it.end - it.start } ?: return null
    var start = best.start
    var end = best.end
    val inside = blips.filter { it.start >= start && it.end <= end }.sortedBy { it.start }
    // A look right at the edge is the evening winding down or the morning starting, not sleep.
    inside.firstOrNull()?.takeIf { it.start - start <= EDGE_BLIP_MS }?.let { start = it.end }
    inside.lastOrNull()?.takeIf { end - it.end <= EDGE_BLIP_MS }?.let { end = minOf(end, it.start) }
    if (end - start < MIN_SLEEP_MS) return null
    return SleepEstimate(start, end, inside.filter { it.start >= start && it.end <= end })
}

/** 8 pm the evening before [epochDay] to noon on it, in [zone]. */
fun nightWindow(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val day = LocalDate.ofEpochDay(epochDay)
    val start = day.minusDays(1).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
    val end = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    return start to end
}

/** The night ending on morning [epochDay], limited to [nowMs] (a night still going shows so far). */
fun nightEstimate(use: List<UseSpan>, epochDay: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): SleepEstimate? {
    val (s, e) = nightWindow(epochDay, zone)
    return estimateSleep(use, s, minOf(e, nowMs))
}

/** The week's nights, Monday first: null for a night with no estimate or one that has not happened. */
fun weekNights(use: List<UseSpan>, monday: Long, today: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<SleepEstimate?> =
    (0 until 7).map { i -> if (monday + i > today) null else nightEstimate(use, monday + i, nowMs, zone) }

/** Average length of the nights that have an estimate, or null. */
fun averageSleep(nights: List<SleepEstimate?>): Long? =
    nights.filterNotNull().takeIf { it.isNotEmpty() }?.let { n -> n.sumOf { it.durationMs } / n.size }

/** "7 h 12 min". */
fun sleepDurationLabel(ms: Long): String {
    val m = ms / 60_000
    return String.format(Locale.US, "%d h %02d min", m / 60, m % 60)
}

/** "11:48 pm". */
fun clockLabel(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val h = if (t.hour % 12 == 0) 12 else t.hour % 12
    return String.format(Locale.US, "%d:%02d %s", h, t.minute, if (t.hour < 12) "am" else "pm")
}

/** "phone picked up once", "phone picked up 3 times", "phone not touched". */
fun pickupLabel(count: Int): String = when (count) {
    0 -> "phone not touched"
    1 -> "phone picked up once"
    else -> "phone picked up $count times"
}

/** Where [ms] falls on the 10 pm (the evening before [epochDay]) to 8 am bar, 0..1 (clamped). */
fun timelineFraction(ms: Long, epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Float {
    val day = LocalDate.ofEpochDay(epochDay)
    val a = day.minusDays(1).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
    val b = day.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    return ((ms - a).toFloat() / (b - a)).coerceIn(0f, 1f)
}

/** The next time of day [minuteOfDay] at or after [nowMs] (strictly after). */
fun nextOccurrence(minuteOfDay: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
    var t = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone)
    if (!t.toInstant().isAfter(now.toInstant())) t = t.plusDays(1)
    return t.toInstant().toEpochMilli()
}
