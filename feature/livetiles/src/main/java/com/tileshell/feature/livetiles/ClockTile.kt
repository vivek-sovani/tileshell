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
 * lowercase weekday and date; the back shows the date again with the next alarm.
 * Empty string means no alarm is set (back omits the alarm line).
 */
data class ClockFace(
    val hm: String,
    val weekday: String,
    val fullDate: String,
    val alarm: String = "",
)

/**
 * Packages of known alarm-clock apps (Google Clock, Samsung Clock) whose
 * [AlarmManager.AlarmClockInfo] entries are trusted as real alarms. Some other
 * apps (notably calendar/reminder apps) also call `setAlarmClock()` for their
 * own reminders — purely to bypass Doze/battery restrictions on exact timing —
 * which would otherwise surface on the clock tile mislabeled as "alarm / bedtime"
 * (see the user report: a 2:50pm calendar meeting reminder showed up as the
 * tile's next alarm). Deliberately a small explicit whitelist, not a heuristic:
 * only devices whose stock clock app is one of these two show the alarm/bedtime
 * face at all; other OEM clock apps simply never populate it, per the user's
 * own call — extend only for another confirmed, specific clock app package.
 */
private val KNOWN_ALARM_CLOCK_PACKAGES = setOf(
    "com.google.android.deskclock",
    "com.sec.android.app.clockpackage",
)

/**
 * Returns the next system alarm-clock event as "h:mm am/pm", or empty string if
 * none is set or it wasn't registered by a [KNOWN_ALARM_CLOCK_PACKAGES] app.
 * Reads [AlarmManager.getNextAlarmClock] — no permission required.
 */
fun nextAlarmString(context: Context): String {
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return ""
    val info = am.nextAlarmClock ?: return ""
    val creator = runCatching { info.showIntent?.creatorPackage }.getOrNull()
    if (creator !in KNOWN_ALARM_CLOCK_PACKAGES) return ""
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
): ClockFace {
    val hour12 = (hour24 % 12).let { if (it == 0) 12 else it }
    val suffix = if (hour24 < 12) "am" else "pm"
    return ClockFace(
        hm = "$hour12:${minute.toString().padStart(2, '0')} $suffix",
        weekday = WEEKDAYS[dayOfWeek - 1],
        fullDate = "$dayOfMonth ${MONTHS[month0]} $year",
        alarm = alarm,
    )
}

private fun currentClockFace(context: Context): ClockFace {
    val c = Calendar.getInstance()
    return clockFace(
        hour24 = c.get(Calendar.HOUR_OF_DAY),
        minute = c.get(Calendar.MINUTE),
        dayOfWeek = c.get(Calendar.DAY_OF_WEEK),
        dayOfMonth = c.get(Calendar.DAY_OF_MONTH),
        month0 = c.get(Calendar.MONTH),
        year = c.get(Calendar.YEAR),
        alarm = nextAlarmString(context),
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
                // Alarm gets the hero slot — user set it, they want to see it.
                // Labelled "alarm / bedtime" because Android's getNextAlarmClock reports
                // the next alarm-clock *event*, which includes the clock app's Bedtime
                // schedule, and gives no way to tell the two apart.
                Text(
                    text = "alarm / bedtime",
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
                    text = face.fullDate,
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
