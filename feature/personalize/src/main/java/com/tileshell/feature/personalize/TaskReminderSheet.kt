package com.tileshell.feature.personalize

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TaskItem
import com.tileshell.core.data.reminders.TaskRepeat
import com.tileshell.core.data.reminders.reminderClock
import com.tileshell.core.data.reminders.reminderDay
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.TileIcons
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private val QUICK_TIMES = listOf(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(18, 0))

/**
 * "remind me" — date, time and repeat for one task, shown over the task list.
 * Quick chips for the common picks plus the platform date/time pickers.
 * [onSet] gets the chosen instant; [onRemove] clears an existing reminder.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskReminderSheet(
    task: TaskItem,
    tokens: ColorTokens,
    accent: Color,
    onSet: (at: Long, repeat: TaskRepeat) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val existing = task.remindAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
    var date by remember { mutableStateOf(existing?.toLocalDate() ?: today.plusDays(1)) }
    var time by remember { mutableStateOf(existing?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(9, 0)) }
    var repeat by remember { mutableStateOf(task.repeat) }
    var everyDays by remember { mutableStateOf((task.repeat as? TaskRepeat.EveryDays)?.days ?: 3) }
    var error by remember { mutableStateOf<String?>(null) }

    val nextMonday = remember { today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) }
    val quickDates = listOf(today to "today", today.plusDays(1) to "tomorrow", nextMonday to "next mon")

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(tokens.sheet, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text("remind me", color = tokens.fg, fontSize = 20.sp, fontWeight = FontWeight.W300)
            Text(task.text, color = tokens.fgDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            SectionLabel("date", tokens)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                quickDates.forEach { (d, label) ->
                    Chip(label, selected = date == d, tokens, accent) { date = d; error = null }
                }
                val custom = quickDates.none { it.first == date }
                Chip(if (custom) reminderDay(date, today) else "pick", selected = custom, tokens, accent, iconKey = "calendar") {
                    DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d); error = null }, date.year, date.monthValue - 1, date.dayOfMonth)
                        .apply { datePicker.minDate = System.currentTimeMillis() - 1_000L }
                        .show()
                }
            }

            SectionLabel("time", tokens)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val todayNow = java.time.ZonedDateTime.now(zone)
                QUICK_TIMES.forEach { t ->
                    Chip(reminderClock(todayNow.with(t)), selected = time == t, tokens, accent) { time = t; error = null }
                }
                val custom = time !in QUICK_TIMES
                Chip(if (custom) reminderClock(todayNow.with(time)) else "pick", selected = custom, tokens, accent, iconKey = "clock") {
                    TimePickerDialog(context, { _, h, min -> time = LocalTime.of(h, min); error = null }, time.hour, time.minute, false).show()
                }
            }

            SectionLabel("repeat", tokens)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    TaskRepeat.Once to "once",
                    TaskRepeat.Daily to "daily",
                    TaskRepeat.Weekly to "weekly",
                    TaskRepeat.Monthly to "monthly",
                    TaskRepeat.Yearly to "yearly",
                ).forEach { (r, label) ->
                    Chip(label, selected = repeat == r, tokens, accent) { repeat = r }
                }
                Chip("every n days", selected = repeat is TaskRepeat.EveryDays, tokens, accent) {
                    repeat = TaskRepeat.EveryDays(everyDays)
                }
            }
            if (repeat is TaskRepeat.EveryDays) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("every", color = tokens.fgDim, fontSize = 13.sp)
                    Stepper("−", tokens) {
                        everyDays = (everyDays - 1).coerceAtLeast(TaskRepeat.MIN_DAYS)
                        repeat = TaskRepeat.EveryDays(everyDays)
                    }
                    Text("$everyDays", color = tokens.fg, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    Stepper("+", tokens) {
                        everyDays = (everyDays + 1).coerceAtMost(TaskRepeat.MAX_DAYS)
                        repeat = TaskRepeat.EveryDays(everyDays)
                    }
                    Text("days", color = tokens.fgDim, fontSize = 13.sp)
                }
            }

            error?.let {
                Text(it, color = Color(0xFFF07A7A), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (task.remindAt != null) {
                    Text(
                        "remove",
                        color = tokens.fgDim,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable(onClick = onRemove).padding(vertical = 8.dp, horizontal = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "set reminder",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(accent)
                        .clickable {
                            val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                            if (at <= System.currentTimeMillis()) {
                                error = "that time has already passed — pick a later one"
                            } else {
                                onSet(at, repeat)
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, tokens: ColorTokens) {
    Text(text, color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    tokens: ColorTokens,
    accent: Color,
    iconKey: String? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent else tokens.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (iconKey != null) {
            Icon(TileIcons[iconKey], contentDescription = null, tint = if (selected) Color.White else tokens.fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = if (selected) Color.White else tokens.fg, fontSize = 13.sp)
    }
}

@Composable
private fun Stepper(symbol: String, tokens: ColorTokens, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tokens.chip)
            .clickable(onClick = onClick),
    ) {
        Text(symbol, color = tokens.fg, fontSize = 16.sp)
    }
}
