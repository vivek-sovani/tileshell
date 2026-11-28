package com.tileshell.feature.livetiles.health

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The health hub's numbers: a day's steps kept in a short history, the week and streak read from it, and the
 * distance, calories and active-minute estimates worked out from steps. All pure and unit-tested.
 */

/** One day's step total ([epochDay] is days since 1970-01-01, local). */
data class DaySteps(val epochDay: Long, val steps: Int)

const val DEFAULT_GOAL = 8_000
const val DEFAULT_HEIGHT_CM = 170
const val DEFAULT_WEIGHT_KG = 70
const val KEEP_DAYS = 120

/**
 * Notes that [steps] have been counted on [epochDay]. A day only ever grows (the counter counts up), so a lower reading
 * (a reboot reset it) never lowers what was already counted. Oldest days past [KEEP_DAYS] are dropped. Ascending by day.
 */
fun recordDay(history: List<DaySteps>, epochDay: Long, steps: Int): List<DaySteps> {
    val existing = history.firstOrNull { it.epochDay == epochDay }?.steps ?: 0
    val merged = history.filterNot { it.epochDay == epochDay } + DaySteps(epochDay, maxOf(existing, steps.coerceAtLeast(0)))
    return merged.sortedBy { it.epochDay }.takeLast(KEEP_DAYS)
}

fun stepsOn(history: List<DaySteps>, epochDay: Long): Int = history.firstOrNull { it.epochDay == epochDay }?.steps ?: 0

/** Stride in metres from height (about 41.5% of it). */
fun strideMeters(heightCm: Int): Double = heightCm.coerceIn(100, 230) * 0.415 / 100.0

/** Distance walked, in km, one decimal's worth. */
fun distanceKm(steps: Int, heightCm: Int): Double = steps * strideMeters(heightCm) / 1000.0

/** A rough energy figure: about 0.04 kcal a step. An estimate, not a measurement. */
fun caloriesKcal(steps: Int): Int = (steps * 0.04).roundToInt()

/** Walking MET (Compendium of Physical Activities) for a speed in km/h, plus 0.1 for each percent of average gradient. */
fun walkMet(speedKmh: Double, gradePct: Double = 0.0): Double {
    val base = when {
        speedKmh < 3.2 -> 2.3
        speedKmh < 4.8 -> 2.9
        speedKmh < 5.6 -> 3.3
        speedKmh < 6.4 -> 3.8
        speedKmh < 7.2 -> 5.0
        else -> 6.3
    }
    return base + 0.1 * gradePct.coerceIn(0.0, 15.0)
}

/** Calories burnt by a walk beyond resting: (MET - 1) x weight x hours. An estimate. */
fun walkKcal(activeMs: Long, km: Double, ascentM: Int, weightKg: Int): Int {
    if (activeMs <= 0L) return 0
    val hours = activeMs / 3_600_000.0
    val grade = if (km > 0.05) ascentM / (km * 1000.0) * 100.0 else 0.0
    return ((walkMet(km / hours, grade) - 1.0) * weightKg * hours).roundToInt().coerceAtLeast(0)
}

/** Calories a step is worth outside a walk, scaled from 0.04 for 70 kg. */
fun stepKcal(steps: Int, weightKg: Int): Double = steps * 0.04 * weightKg / DEFAULT_WEIGHT_KG

/** A day's (or period's) calories: each walk by the MET formula, the rest of the steps at the per-step rate. */
fun dayKcal(totalSteps: Int, walks: List<Walk>, heightCm: Int, weightKg: Int): Int {
    val walkSteps = walks.sumOf { it.steps }
    val walkKcal = walks.sumOf { walkKcal(it.activeMs, walkKm(it, heightCm), walkAscentM(it), weightKg) }
    return walkKcal + stepKcal((totalSteps - walkSteps).coerceAtLeast(0), weightKg).roundToInt()
}

/** Active minutes, roughly: a brisk walk is about 100 steps a minute. An estimate. */
fun activeMinutes(steps: Int): Int = steps / 100

/** Progress to the goal, 0..1 (an over-goal day stays at 1). */
fun goalProgress(steps: Int, goal: Int): Float = if (goal <= 0) 0f else (steps.toFloat() / goal).coerceIn(0f, 1f)

/** Days in a row at or above [goal], counting back from today when it is already reached, else from yesterday. */
fun streak(history: List<DaySteps>, todayEpochDay: Long, goal: Int): Int {
    val by = history.associate { it.epochDay to it.steps }
    var day = if ((by[todayEpochDay] ?: 0) >= goal) todayEpochDay else todayEpochDay - 1
    var count = 0
    while ((by[day] ?: 0) >= goal && goal > 0) {
        count++
        day--
    }
    return count
}

/** The Monday of the week [epochDay] is in. */
fun weekStart(epochDay: Long): Long = epochDay - (LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1)

/** A week, Monday first: each day's steps (null for a day after [todayEpochDay]), and the summary. */
data class WeekStats(
    val start: Long,
    val days: List<Int?>,
    val total: Int,
    /** Average over the days that have happened (today included). */
    val average: Int,
    val bestIndex: Int?,
    val daysAtGoal: Int,
)

fun weekStats(history: List<DaySteps>, start: Long, todayEpochDay: Long, goal: Int): WeekStats =
    rangeStats(history, start, 7, todayEpochDay, goal)

/** The first day of the month [epochDay] is in, and how many days it has. */
fun monthStart(epochDay: Long): Long = LocalDate.ofEpochDay(epochDay).withDayOfMonth(1).toEpochDay()

fun monthLength(startEpochDay: Long): Int = LocalDate.ofEpochDay(startEpochDay).lengthOfMonth()

/** The same summary over any run of [length] days from [start] (a week, a month). */
fun rangeStats(history: List<DaySteps>, start: Long, length: Int, todayEpochDay: Long, goal: Int): WeekStats {
    val days = (0 until length).map { i ->
        val day = start + i
        if (day > todayEpochDay) null else stepsOn(history, day)
    }
    val played = days.filterNotNull()
    val best = days.withIndex().filter { it.value != null && it.value!! > 0 }.maxByOrNull { it.value!! }?.index
    return WeekStats(
        start = start,
        days = days,
        total = played.sum(),
        average = if (played.isEmpty()) 0 else played.sum() / played.size,
        bestIndex = best,
        daysAtGoal = played.count { it >= goal && goal > 0 },
    )
}

/** What a set of walks adds up to. */
data class WalkTotals(val count: Int, val activeMs: Long, val km: Double, val ascentM: Int, val steps: Int)

fun walkTotals(walks: List<Walk>, heightCm: Int): WalkTotals =
    WalkTotals(walks.size, walks.sumOf { it.activeMs }, walks.sumOf { walkKm(it, heightCm) }, walks.sumOf { walkAscentM(it) }, walks.sumOf { it.steps })

/** Walks that started on a local day from [fromDay] to [toDay], newest first. */
fun walksBetween(walks: List<Walk>, fromDay: Long, toDay: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Walk> =
    walks.filter {
        val d = java.time.Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate().toEpochDay()
        d in fromDay..toDay
    }.sortedByDescending { it.startMs }
