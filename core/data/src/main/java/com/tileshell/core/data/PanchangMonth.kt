package com.tileshell.core.data

import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * One day of a month calendar in the Hindu panchang: the Gregorian [day], the tithi number at that morning's
 * sunrise-ish hour (shown the way printed panchang calendars do, 1-15 and 1-15 again, with the full and new moon
 * apart), and what kind of day it is.
 */
data class PanchangDayCell(val day: Int, val tithi: Int, val label: String, val kind: PanchangDayKind)

enum class PanchangDayKind { NORMAL, EKADASHI, PURNIMA, AMAVASYA }

/** A month of [PanchangDayCell]s and where it starts in a Monday-first week. */
data class PanchangMonth(val year: Int, val month: Int, val firstWeekdayOffset: Int, val cells: List<PanchangDayCell>)

/** The tithi in force at 6 am local — close enough to sunrise to match a printed panchang. */
private const val TITHI_HOUR = 6

fun panchangMonth(year: Int, month: Int, zone: ZoneId = ZoneId.systemDefault()): PanchangMonth {
    val first = LocalDate.of(year, month, 1)
    val tz = TimeZone.getTimeZone(zone)
    val cells = (1..first.lengthOfMonth()).map { day ->
        val millis = LocalDate.of(year, month, day).atTime(TITHI_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        val t = HinduPanchang.panchangFor(millis, tz).tithi
        val kind = when {
            t.displayNumber == 15 -> PanchangDayKind.PURNIMA
            t.displayNumber == 30 -> PanchangDayKind.AMAVASYA
            t.tithiInPaksha == 11 -> PanchangDayKind.EKADASHI
            else -> PanchangDayKind.NORMAL
        }
        PanchangDayCell(day, t.displayNumber, PanchangDevanagari.tithiNumber(t), kind)
    }
    return PanchangMonth(year, month, first.dayOfWeek.value - 1, cells)
}
