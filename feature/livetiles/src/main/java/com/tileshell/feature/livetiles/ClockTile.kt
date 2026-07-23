package com.tileshell.feature.livetiles

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import kotlinx.coroutines.delay
import java.util.Calendar

private val WEEKDAYS = listOf(
    "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday",
)
private val MONTHS = listOf(
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december",
)

/**
 * The text shown on the clock tile's two faces. 12-hour `h:mm am/pm` (matching the
 * feed/glance screen's clock, [com.tileshell.feature.start.feed.feedClock12] — a
 * deliberate deviation from the prototype's `clockNow()`, which is 24-hour; see
 * DECISIONS.md "Clock tile: 12-hour am/pm, matching the glance screen") with full
 * lowercase weekday and date; the back shows the date again with the next
 * reminder/alarm. Empty [alarm] means none is set (back omits the line).
 * [alarmDate] is the reminder/alarm's *own* date (e.g. "25 july 2026" for an
 * alarm two days out) — distinct from [fullDate] (today's date) since the two
 * differ whenever the next alarm isn't today.
 */
data class ClockFace(
    val hm: String,
    val weekday: String,
    val fullDate: String,
    val alarm: String = "",
    val reminderTitle: String = "",
    val alarmDate: String = "",
)

private fun formatFullDate(dayOfMonth: Int, month0: Int, year: Int): String =
    "$dayOfMonth ${MONTHS[month0]} $year"

/**
 * [AlarmManager.getNextAlarmClock] is a single system-wide "next" value, not
 * queryable per app — there's no public API to ask "what's Google Clock's next
 * alarm specifically." Non-clock apps (notably calendar apps) also register via
 * `setAlarmClock()` for their own reminders, purely to bypass Doze/battery
 * restrictions on exact timing; whichever is chronologically soonest wins the
 * single global slot, so a same-day calendar reminder routinely eclipses a real
 * alarm set for later. A prior attempt to filter this to known clock-app
 * packages (Google/Samsung) just made the tile go blank whenever a calendar
 * reminder was the sooner entry — worse than the ambiguity, since a real alarm
 * further out then never showed. So: always show whatever's next, generically
 * labelled "next reminder/alarm" (see [ClockBack]) rather than claiming it's an
 * alarm; when it matches an upcoming calendar event's start time (within
 * [REMINDER_MATCH_TOLERANCE_MS]) — see [reminderTitleFor] — show that event's
 * title in place of the generic label.
 */
fun nextAlarmString(context: Context): String {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return ""
    val info = am.nextAlarmClock ?: return ""
    val cal = Calendar.getInstance().apply { timeInMillis = info.triggerTime }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val ampm = if (hour < 12) "am" else "pm"
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$h12:${minute.toString().padStart(2, '0')} $ampm"
}

private const val REMINDER_MATCH_TOLERANCE_MS = 60_000L

/**
 * If [triggerMillis] (the system's next alarm-clock trigger time) matches a
 * scheduled calendar reminder's actual fire time — within
 * [REMINDER_MATCH_TOLERANCE_MS], to absorb rounding — returns that reminder's
 * event title, so the tile can show real reminder text instead of a generic
 * label. Deliberately queries [android.provider.CalendarContract.CalendarAlerts]
 * (each row's `ALARM_TIME` is exactly when that reminder is scheduled to fire —
 * the same value AlarmManager was handed), not `Instances.BEGIN` (an event's
 * *start* time): those two only coincide for a reminder set to "at time of
 * event," so an all-day event (whose `BEGIN` is midnight, not the reminder's
 * actual time-of-day) or any reminder offset earlier than the event start would
 * never match against `Instances` — verified on-device, where a 2:50pm
 * meeting reminder's `calendar_alerts.ALARM_TIME` matched the trigger time
 * exactly while its parent event's `BEGIN` was 9+ hours away. Requires
 * READ_CALENDAR; silently returns "" when denied (no new permission ask from
 * the clock tile — this only activates if the user already granted it, e.g.
 * via the calendar tile) or when nothing matches (probably a real Clock-app
 * alarm). When multiple events share one alarm time (a reminder fired for
 * several overlapping meetings at once), picks the soonest-starting one.
 */
private fun reminderTitleFor(context: Context, triggerMillis: Long): String =
    runCatching {
        var title = ""
        context.contentResolver.query(
            android.provider.CalendarContract.CalendarAlerts.CONTENT_URI,
            arrayOf(
                android.provider.CalendarContract.CalendarAlerts.TITLE,
                android.provider.CalendarContract.CalendarAlerts.BEGIN,
            ),
            "${android.provider.CalendarContract.CalendarAlerts.ALARM_TIME} >= ? AND " +
                "${android.provider.CalendarContract.CalendarAlerts.ALARM_TIME} <= ?",
            arrayOf(
                (triggerMillis - REMINDER_MATCH_TOLERANCE_MS).toString(),
                (triggerMillis + REMINDER_MATCH_TOLERANCE_MS).toString(),
            ),
            "${android.provider.CalendarContract.CalendarAlerts.BEGIN} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) title = cursor.getString(0).orEmpty()
        }
        title
    }.getOrDefault("")

/**
 * Builds a [ClockFace] from calendar fields. Pure (no `Calendar.getInstance()`)
 * so the formatting — 12-hour am/pm, unpadded hour, zero-padded minutes, lowercase
 * names — is unit-testable. [dayOfWeek] is Calendar's 1=Sunday convention; [month0]
 * is 0-based (0=January), matching both Calendar and the prototype's JS `Date`.
 * [alarm] is the formatted next-alarm string; empty means no alarm is set.
 */
fun clockFace(
    hour24: Int,
    minute: Int,
    dayOfWeek: Int,
    dayOfMonth: Int,
    month0: Int,
    year: Int,
    alarm: String = "",
    reminderTitle: String = "",
    alarmDate: String = "",
): ClockFace {
    val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
    val suffix = if (hour24 < 12) "am" else "pm"
    return ClockFace(
        hm = "$hour12:${minute.toString().padStart(2, '0')} $suffix",
        weekday = WEEKDAYS[dayOfWeek - 1],
        fullDate = formatFullDate(dayOfMonth, month0, year),
        alarm = alarm,
        reminderTitle = reminderTitle,
        alarmDate = alarmDate,
    )
}

private fun currentClockFace(context: Context): ClockFace {
    val c = Calendar.getInstance()
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val info = am?.nextAlarmClock
    val alarmDate = info?.let {
        val alarmCal = Calendar.getInstance().apply { timeInMillis = it.triggerTime }
        formatFullDate(
            alarmCal.get(Calendar.DAY_OF_MONTH),
            alarmCal.get(Calendar.MONTH),
            alarmCal.get(Calendar.YEAR),
        )
    }.orEmpty()
    return clockFace(
        hour24 = c.get(Calendar.HOUR_OF_DAY),
        minute = c.get(Calendar.MINUTE),
        dayOfWeek = c.get(Calendar.DAY_OF_WEEK),
        dayOfMonth = c.get(Calendar.DAY_OF_MONTH),
        month0 = c.get(Calendar.MONTH),
        year = c.get(Calendar.YEAR),
        alarm = nextAlarmString(context),
        reminderTitle = info?.let { reminderTitleFor(context, it.triggerTime) }.orEmpty(),
        alarmDate = alarmDate,
    )
}

/**
 * The live clock tile (FR-2.1): a [FlipTile] whose front shows the time and the
 * back the date + next alarm. The displayed time ticks on each minute boundary
 * while [active] (the same gate that drives the flip scheduler), so a paused /
 * off-screen launcher does no per-minute work; it refreshes the moment it
 * resumes. Live face only at medium+ — small tiles never reach here.
 */
@Composable
fun ClockTileFace(
    size: TileSize,
    flipped: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var face by remember { mutableStateOf(currentClockFace(context)) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            face = currentClockFace(context)
            // Align the next tick to the start of the next minute.
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    // Keep the alarm line current without waiting for the next minute tick: refresh
    // the moment the system's next alarm changes (the user added/edited/deleted an
    // alarm) and whenever the launcher resumes (returning from the clock app). This
    // works even while the minute tick is paused (active == false), so the alarm is
    // never stale.
    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                face = currentClockFace(context)
            }
        }
        runCatching {
            context.registerReceiver(
                receiver,
                IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            )
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) face = currentClockFace(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { ClockFront(face, size) },
        back = { ClockBack(face) },
    )
}

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * WIDE and MEDIUM clock tiles both occupy a 2-row footprint (`GridGeometry`), whose
 * absolute pixel height shrinks as the grid's column count rises — 5/6 columns pack
 * more, smaller units into the same screen width, so a tile's row height shrinks too
 * even though its unit *count* doesn't. The face text below is sized to fit
 * comfortably at the default 4 columns (≈170dp+ tall); [clockFaceScale] shrinks it
 * proportionally below that so the date line never gets clipped at 5/6 columns,
 * while leaving the common 4-column case pixel-identical (scale clamps to 1).
 */
private val ClockRowReferenceHeight = 165.dp

internal fun clockFaceScale(measuredHeight: Dp): Float =
    (measuredHeight / ClockRowReferenceHeight).coerceIn(0.6f, 1f)

/**
 * The compact clock face for a small (1×1) tile: just the time, centred, at a size
 * that fits the tiny tile. Ticks on the minute boundary while [active]; never flips
 * (small tiles stay out of the flip scheduler).
 */
@Composable
fun ClockSmallFace(active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var face by remember { mutableStateOf(currentClockFace(context)) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            face = currentClockFace(context)
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = face.hm,
            color = FaceText,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-1).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ClockFront(face: ClockFace, size: TileSize) {
    // Prototype `.lc .xl` (styles.css): 64px wide / 42px medium, weight 200,
    // line-height .9, letter-spacing -2px. CSS lets the tall glyphs overflow the
    // .9 line box harmlessly; Compose would crop them (the time vanished at the
    // earlier inflated size), so trim = None keeps the full glyph painted.
    val big = size == TileSize.WIDE
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = clockFaceScale(maxHeight)
        val timeSize = (if (big) 64f else 42f).sp * scale
        Column(
            modifier = Modifier.fillMaxSize().padding(11.dp * scale),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = face.hm,
                color = FaceText,
                fontSize = timeSize,
                lineHeight = timeSize * 0.9f,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-2).sp,
                maxLines = 1,
                style = LocalTextStyle.current.copy(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None,
                    ),
                ),
            )
            Spacer(Modifier.height(4.dp * scale))
            Text(text = face.weekday, color = FaceText, fontSize = 15.sp * scale, maxLines = 1)
            Text(
                text = face.fullDate,
                color = FaceText.copy(alpha = 0.82f),
                fontSize = 12.sp * scale,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ClockBack(face: ClockFace) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scale = clockFaceScale(maxHeight)
        val bigSize = 30.sp * scale
        val smallSize = 12.sp * scale
        Column(
            modifier = Modifier.fillMaxSize().padding(11.dp * scale),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End,
        ) {
            if (face.alarm.isNotEmpty()) {
                // Alarm/reminder gets the hero slot — user set it, they want to see it.
                // Labelled generically because Android's getNextAlarmClock reports a
                // single system-wide next value that any app (not just a clock app —
                // e.g. a calendar reminder) may have registered; shows the matched
                // calendar event's own title instead when one lines up (reminderTitleFor).
                Text(
                    text = face.reminderTitle.ifEmpty { "next reminder / alarm" },
                    color = FaceText.copy(alpha = 0.65f),
                    fontSize = 11.sp * scale,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp * scale))
                Text(
                    text = face.alarm,
                    color = FaceText,
                    fontSize = bigSize,
                    lineHeight = bigSize,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp * scale))
                Text(
                    // The reminder/alarm's own date, not necessarily today's — e.g. an
                    // alarm set for the 25th while today is the 23rd shows "25 ...".
                    text = face.alarmDate.ifEmpty { face.fullDate },
                    color = FaceText.copy(alpha = 0.65f),
                    fontSize = smallSize,
                    maxLines = 1,
                )
            } else {
                // No alarm set — date fills the back face as before.
                Text(
                    text = face.fullDate,
                    color = FaceText,
                    fontSize = bigSize,
                    lineHeight = bigSize,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp,
                    maxLines = 1,
                )
            }
        }
    }
}
