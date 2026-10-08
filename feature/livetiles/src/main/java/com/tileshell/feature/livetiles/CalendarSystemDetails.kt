package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.CalFields
import com.tileshell.core.data.CalendarSource
import com.tileshell.core.data.SystemDay
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.UpcomingSpecial
import com.tileshell.core.data.calendarSystemFor
import com.tileshell.core.data.daysAwayText
import com.tileshell.core.data.describeSystemDay
import com.tileshell.core.data.upcomingSpecialDays
import com.tileshell.core.design.LocalTileFaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** [CalendarSource] over ICU (`android.icu`), which knows every system on the tile except the Hindu panchang. */
internal class IcuCalendarSource(systemId: String) : CalendarSource {
    private val keyword = calendarSystemFor(systemId)?.icuCalendarKeyword.orEmpty()
    private val locale = android.icu.util.ULocale("en@calendar=$keyword")
    private val calendar = android.icu.util.Calendar.getInstance(locale)
    private val monthFormat = android.icu.text.SimpleDateFormat("MMMM", locale)

    private fun at(epochDay: Long): android.icu.util.Calendar {
        // Local noon: far from any day boundary, whatever the zone's offset.
        val millis = LocalDate.ofEpochDay(epochDay).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        calendar.timeInMillis = millis
        return calendar
    }

    override fun fieldsOn(epochDay: Long): CalFields {
        val c = at(epochDay)
        return CalFields(
            day = c.get(android.icu.util.Calendar.DAY_OF_MONTH),
            month0 = c.get(android.icu.util.Calendar.MONTH),
            year = c.get(android.icu.util.Calendar.YEAR),
            leapMonth = c.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1 && keyword == "chinese",
        )
    }

    override fun monthLength(epochDay: Long) = at(epochDay).getActualMaximum(android.icu.util.Calendar.DAY_OF_MONTH)
    override fun monthsInYear(epochDay: Long) = at(epochDay).getActualMaximum(android.icu.util.Calendar.MONTH) + 1
    override fun monthName(epochDay: Long): String =
        runCatching { monthFormat.format(at(epochDay)) }.getOrDefault("")
}

/** Everything the tile shows about today in [systemId], worked out off the main thread and redone each day. */
internal class SystemDetails(val today: SystemDay, val upcoming: List<UpcomingSpecial>)

@Composable
internal fun rememberSystemDetails(systemId: String, nowMillis: Long): SystemDetails? {
    val epochDay = remember(nowMillis) {
        java.time.Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }
    val details by produceState<SystemDetails?>(initialValue = null, systemId, epochDay) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val source = IcuCalendarSource(systemId)
                SystemDetails(describeSystemDay(systemId, source, epochDay), upcomingSpecialDays(systemId, source, epochDay, count = 2))
            }.getOrNull()
        }
    }
    return details
}

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

private fun weekdayWord(nowMillis: Long): String =
    java.time.Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).dayOfWeek
        .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ENGLISH)

private fun monthYearLine(d: SystemDay): String = listOf(d.monthName, d.yearText).filter { it.isNotBlank() }.joinToString(" ")

private fun nextSpecialLine(upcoming: List<UpcomingSpecial>): String? =
    upcoming.firstOrNull()?.let { "${it.name} ${daysAwayText(it.daysAway)}" }

/**
 * The front of the calendar-systems tile for every system but the panchang: today's day number big, the month and
 * year with their era, the weekday, and the facts of the month, with a small month grid on the biggest tile.
 */
@Composable
internal fun SystemDayFront(label: String, details: SystemDetails, nowMillis: Long, size: TileSize) {
    val d = details.today
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size.cols >= 3 && size.rows >= 3
    val wide = size.cols >= 4 && !big
    val align = if (narrow) Alignment.CenterHorizontally else Alignment.Start
    val pad = if (narrow || short) 4.dp else 11.dp
    if (short || (narrow && size.rows < 2)) {
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.day}", color = FaceText, fontSize = if (short) 22.sp else 30.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(d.monthName.take(if (narrow) 6 else 10), color = FaceText.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    if (big) {
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(label, color = FaceText.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${d.day}", color = FaceText, fontWeight = FontWeight.Light, maxLines = 1, fontSize = 52.sp)
                Spacer(Modifier.padding(start = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(monthYearLine(d), color = FaceText, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    d.yearNote?.let { Text(it, color = FaceText.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1) }
                    Text(
                        "${weekdayWord(nowMillis)} · day ${d.day} of ${d.monthLength}",
                        color = FaceText.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            MonthGrid(d, Modifier.weight(1f).fillMaxWidth())
            nextSpecialLine(details.upcoming)?.let {
                Text(it, color = FaceText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
        return
    }
    Row(Modifier.fillMaxSize().padding(pad), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
            horizontalAlignment = align,
        ) {
            Text(label, color = FaceText.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${d.day}", color = FaceText, fontWeight = FontWeight.Light, maxLines = 1,
                fontSize = if (big) 64.sp else if (narrow) 36.sp else 48.sp,
            )
            Text(
                monthYearLine(d), color = FaceText, fontWeight = FontWeight.Medium,
                fontSize = if (big) 18.sp else if (narrow) 12.sp else 15.sp,
                maxLines = if (narrow) 3 else 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
            d.yearNote?.let { Text(it, color = FaceText.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (!narrow || size.rows >= 3) {
                Text(
                    "${weekdayWord(nowMillis)} · day ${d.day} of ${d.monthLength}",
                    color = FaceText.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (big || wide) {
                nextSpecialLine(details.upcoming)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = FaceText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (big) {
            Spacer(Modifier.padding(start = 8.dp))
            MonthGrid(d, Modifier.weight(1f))
        }
    }
}

/**
 * The back: the Roman date, the month in numbers (month n of m, days left), and the next observances with
 * a countdown. An Islamic date can run a day either way of the sighted moon, and says so.
 */
@Composable
internal fun SystemDayBack(systemId: String, details: SystemDetails, romanDate: String, size: TileSize) {
    val d = details.today
    val narrow = size.narrowLive
    val short = size.shortLive
    val roomy = size.rows >= 2 && !narrow
    val pad = if (narrow || short) 4.dp else 11.dp
    Column(
        Modifier.fillMaxSize().padding(pad),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text("roman calendar", color = FaceText.copy(alpha = 0.7f), fontSize = if (short) 10.sp else 12.sp, maxLines = 1)
        Text(
            romanDate, color = FaceText, fontWeight = FontWeight.Medium,
            fontSize = if (short) 13.sp else if (narrow) 13.sp else 16.sp, maxLines = if (narrow) 4 else 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (roomy) {
            Spacer(Modifier.height(6.dp))
            Text(
                "month ${d.monthNumber} of ${d.monthsInYear} · ${if (d.daysLeftInMonth == 0) "last day" else "${d.daysLeftInMonth} days left"}",
                color = FaceText.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            details.upcoming.take(if (size.rows >= 3) 2 else 1).forEach {
                Text("${it.name} ${daysAwayText(it.daysAway)}", color = FaceText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (systemId == "islamic" && size.rows >= 3) {
                Text("a day either way of the sighted moon", color = FaceText.copy(alpha = 0.65f), fontSize = 10.sp, maxLines = 2)
            }
        }
    }
}

/** A month in a Monday-first grid with weekday initials; today is a filled disc. Rows share the height given. */
@Composable
private fun MonthGrid(d: SystemDay, modifier: Modifier = Modifier) {
    val cells = d.firstWeekdayOffset + d.monthLength
    val rows = (cells + 6) / 7
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            listOf("m", "t", "w", "t", "f", "s", "s").forEach {
                Text(it, color = FaceText.copy(alpha = 0.6f), fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - d.firstWeekdayOffset + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day in 1..d.monthLength) {
                            val today = day == d.day
                            Box(
                                modifier = if (today) Modifier.aspectRatio(1f).fillMaxHeight().clip(CircleShape).background(FaceText) else Modifier,
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$day", fontSize = 11.sp, maxLines = 1, softWrap = false,
                                    color = if (today) Color.Black.copy(alpha = 0.85f) else FaceText.copy(alpha = 0.9f),
                                    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
