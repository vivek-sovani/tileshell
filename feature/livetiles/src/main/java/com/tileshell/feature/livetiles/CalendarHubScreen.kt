package com.tileshell.feature.livetiles

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private val HUB_PIVOTS = listOf("this week", "next", "month")

/**
 * Full-screen calendar hub — real WP Panorama/Pivot shape, matching the
 * approved mockup exactly (a captioned month/year line, a big two-tone
 * blue→purple gradient "calendar" title, a pivot row, then a two-column grid
 * of day cards each with a coloured bar per event or "no events"). The
 * destination for tapping the calendar tile, replacing the old
 * "open the system calendar app" fallback.
 *
 * "this week" and "next" both query a plain 7-day window via
 * [queryAgendaEvents]/[startOfDayMillis] ("next" starting 7 days after
 * "this week" does); "month" queries every day in the current calendar month
 * ([currentMonthDayStarts]) — there was no dedicated mockup for a month view,
 * so it reuses the exact same day-card grid rather than inventing a separate
 * visual language. All three share one [AgendaDayGrid].
 */
@Composable
fun CalendarHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "calendarHubProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val granted = rememberPermissionGranted(Manifest.permission.READ_CALENDAR)

    BackHandler(enabled = visible) { onDismiss() }

    val pagerState = rememberPagerState(pageCount = { HUB_PIVOTS.size })
    val pagerScope = rememberCoroutineScope()

    val monthYearCaption = remember {
        val cal = Calendar.getInstance()
        val month = CALENDAR_MONTHS_FULL[cal.get(Calendar.MONTH)]
        "$month ${cal.get(Calendar.YEAR)}"
    }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                // Swallows every tap on this screen — see WeatherHubScreen's
                // own doc comment for the real bug this fixes elsewhere in
                // the hub family (a tap falling through to a Start tile
                // underneath).
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(20.dp))
                Text(text = monthYearCaption, color = tokens.fgDim, fontSize = 14.sp)
                // Was a fixed blue→purple gradient matching the mockup
                // exactly — user-reported it should track the user's own
                // chosen accent instead, same as the weather hub's own title.
                Text(
                    text = "calendar",
                    color = accent,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    HUB_PIVOTS.forEachIndexed { index, label ->
                        val selected = pagerState.currentPage == index
                        Text(
                            text = label,
                            color = if (selected) tokens.fg else tokens.fgDim,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { pagerScope.launch { pagerState.animateScrollToPage(index) } },
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (!granted) {
                CalendarPermissionGate(tokens, accent)
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp)
                            .padding(bottom = 16.dp),
                    ) {
                        when (page) {
                            0 -> AgendaWeekPage(context, startOffsetDays = 0, tokens = tokens)
                            1 -> AgendaWeekPage(context, startOffsetDays = 7, tokens = tokens)
                            else -> AgendaMonthPage(context, tokens)
                        }
                    }
                }
            }

            HubAppBar(
                tokens = tokens,
                actions = listOf(
                    HubAppBarAction("back", "back", onDismiss),
                    HubAppBarAction("plus", "add event") { launchAddCalendarEvent(context) },
                    HubAppBarAction("calendar", "open calendar app") { openCalendarApp(context) },
                    HubAppBarAction("clock", "today") {
                        pagerScope.launch { pagerState.animateScrollToPage(0) }
                    },
                ),
            )
        }
    }
}

@Composable
private fun AgendaWeekPage(context: Context, startOffsetDays: Int, tokens: ColorTokens) {
    val dayStarts = remember(startOffsetDays) { (0 until 7).map { startOfDayMillis(startOffsetDays + it) } }
    val events by produceState<List<AgendaEvent>?>(initialValue = null, dayStarts) {
        value = withContext(Dispatchers.IO) {
            queryAgendaEvents(context, dayStarts.first(), startOfDayMillis(startOffsetDays + 7))
        }
    }
    AgendaDayGrid(dayStarts, events, tokens)
}

@Composable
private fun AgendaMonthPage(context: Context, tokens: ColorTokens) {
    val dayStarts = remember { currentMonthDayStarts() }
    val events by produceState<List<AgendaEvent>?>(initialValue = null, dayStarts) {
        value = withContext(Dispatchers.IO) {
            val monthEnd = Calendar.getInstance().apply {
                timeInMillis = dayStarts.last()
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
            queryAgendaEvents(context, dayStarts.first(), monthEnd)
        }
    }
    AgendaDayGrid(dayStarts, events, tokens)
}

/** The two-column day-card grid shared by every pivot page — [dayStarts] is
 * one start-of-day millis per card, in order; `null` [events] means still
 * loading. */
@Composable
private fun AgendaDayGrid(dayStarts: List<Long>, events: List<AgendaEvent>?, tokens: ColorTokens) {
    if (events == null) {
        Text("loading…", color = tokens.fgDim, fontSize = 14.sp)
        return
    }
    val buckets = remember(dayStarts, events) { groupEventsByDay(events, dayStarts) }
    Column {
        dayStarts.chunked(2).forEachIndexed { rowIndex, rowDayStarts ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowDayStarts.forEachIndexed { columnIndex, dayStart ->
                    val dayIndex = rowIndex * 2 + columnIndex
                    Box(modifier = Modifier.weight(1f)) {
                        AgendaDayCard(dayStart, buckets[dayIndex], tokens)
                    }
                }
                if (rowDayStarts.size == 1) Box(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AgendaDayCard(dayStartMillis: Long, dayEvents: List<AgendaEvent>, tokens: ColorTokens) {
    val cal = remember(dayStartMillis) { Calendar.getInstance().apply { timeInMillis = dayStartMillis } }
    Column(modifier = Modifier.padding(end = 10.dp)) {
        Text(
            text = agendaDayLabel(cal.get(Calendar.DAY_OF_WEEK), cal.get(Calendar.DAY_OF_MONTH)),
            color = tokens.fgDim,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        if (dayEvents.isEmpty()) {
            Text("no events", color = tokens.fgDim, fontSize = 13.sp)
        } else {
            dayEvents.forEachIndexed { index, event ->
                AgendaEventRow(event, TileAccents.all[index % TileAccents.all.size], tokens)
                if (index < dayEvents.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AgendaEventRow(event: AgendaEvent, barColor: Color, tokens: ColorTokens) {
    val context = LocalContext.current
    val eventCal = remember(event.startMillis) { Calendar.getInstance().apply { timeInMillis = event.startMillis } }
    Row(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { openCalendarEvent(context, event) },
        ),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(38.dp)
                .background(barColor, shape = RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = if (event.allDay) "all day" else agendaTimeLabel(eventCal.get(Calendar.HOUR_OF_DAY), eventCal.get(Calendar.MINUTE)),
                color = tokens.fgDim,
                fontSize = 12.sp,
            )
            Text(
                text = event.title,
                color = tokens.fg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CalendarPermissionGate(tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (!isGranted && !canShowSystemPermissionDialog(context, Manifest.permission.READ_CALENDAR, asked = true)) {
            blocked = true
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("see your schedule", color = tokens.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "shows your upcoming events by day, week and month. stays on your device — nothing is sent anywhere.",
            color = tokens.fgDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (blocked) "open settings to allow" else "allow calendar access",
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (blocked) openAppPermissionSettings(context) else requestPermission.launch(Manifest.permission.READ_CALENDAR)
                },
            ),
        )
    }
}

/** Opens the system calendar app's own "new event" screen — TileShell only
 * ever reads (READ_CALENDAR); creating an event is left to whichever real
 * calendar app is installed, the same way every launcher widget/shortcut
 * that offers "add event" does. */
private fun launchAddCalendarEvent(context: Context) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isFailure) {
        Toast.makeText(context, "no calendar app to add an event", Toast.LENGTH_SHORT).show()
    }
}

/** Opens the real calendar app at today, falling back to its "add event"
 * screen if nothing handles that first (mirrors the feed page's own
 * `openCalendar` fallback chain). */
private fun openCalendarApp(context: Context) {
    val viewIntent = Intent(Intent.ACTION_VIEW).setData(Uri.parse("content://com.android.calendar/time"))
    if (runCatching { context.startActivity(viewIntent) }.isSuccess) return
    launchAddCalendarEvent(context)
}

/** Opens a specific event (user-requested: "when event is tapped open
 * event") in the system calendar app — the standard `content://…/events/<id>`
 * `ACTION_VIEW` pattern, with the instance's own begin/end as extras so a
 * recurring event opens showing *this* occurrence, not just the series. */
private fun openCalendarEvent(context: Context, event: AgendaEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
    if (runCatching { context.startActivity(intent) }.isFailure) {
        Toast.makeText(context, "no calendar app to open this event", Toast.LENGTH_SHORT).show()
    }
}

private val CALENDAR_MONTHS_FULL = listOf(
    "january", "february", "march", "april", "may", "june",
    "july", "august", "september", "october", "november", "december",
)
