package com.tileshell.feature.livetiles

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current
private const val REFRESH_MS = 5 * 60_000L

private fun currentCalendarToday(): CalendarToday {
    val c = Calendar.getInstance()
    return calendarToday(
        dayOfWeek = c.get(Calendar.DAY_OF_WEEK),
        dayOfMonth = c.get(Calendar.DAY_OF_MONTH),
        month0 = c.get(Calendar.MONTH),
    )
}

/**
 * The live calendar tile (FR-2). The base face always shows today's date (no
 * permission needed), so the tile is useful even with no calendar access. When
 * READ_CALENDAR is granted and there is an upcoming event, the tile flips to show
 * it — polled every few minutes while [active] so a started/finished meeting rolls
 * off. [fallback] is kept for parity, but the date face means the calendar tile
 * never degrades to a bare glyph.
 */
@Composable
fun CalendarTileFace(
    size: TileSize,
    flipped: Boolean,
    active: Boolean,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val granted = rememberPermissionGranted(Manifest.permission.READ_CALENDAR)

    var today by remember { mutableStateOf(currentCalendarToday()) }
    var face by remember { mutableStateOf<CalendarFace?>(null) }
    // Refresh the date on the minute boundary while active so it rolls over
    // shortly after midnight. Re-assigning an equal CalendarToday is a no-op for
    // recomposition (structural equality), so the per-minute tick is cheap.
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            today = currentCalendarToday()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    LaunchedEffect(granted, active) {
        if (!granted || !active) return@LaunchedEffect
        while (true) {
            face = runCatching {
                withContext(Dispatchers.IO) { queryUpcomingEvents(context) }
            }.getOrNull()
            delay(REFRESH_MS)
        }
    }

    val next = face?.next

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        // Front: today's date. Back: the next event when one exists, else the date.
        front = { CalendarDateColumn(today, size) },
        back = {
            if (next != null) {
                CalendarFaceColumn(heading = "next", event = next, size = size)
            } else {
                CalendarDateColumn(today, size)
            }
        },
    )
}

/**
 * The compact calendar face for a small (1×1) tile: just today's day number (e.g.
 * "15"), centred. Refreshes on the minute boundary while [active] so it rolls over
 * after midnight; never flips (small tiles stay out of the flip scheduler). Shows
 * the date with no permission needed.
 */
@Composable
fun CalendarSmallFace(active: Boolean, modifier: Modifier = Modifier) {
    var today by remember { mutableStateOf(currentCalendarToday()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            today = currentCalendarToday()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = today.day.toString(),
            color = FaceText,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-1).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun CalendarDateColumn(today: CalendarToday, size: TileSize) {
    // WIDE and MEDIUM share the same 2-row height (only LARGE's 3 rows have the
    // extra vertical room for the enlarged day number) — sizing "big" off WIDE
    // clipped the month/"calendar" lines at the bottom of a WIDE stack member.
    val big = size == TileSize.LARGE
    // TALL/COLUMN are only 1 column wide (same as SMALL) — the weekday/month
    // lines above clip at that width, so narrow tiles get a centred, width-safe
    // layout instead, spread across whatever row height the tile has.
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = if (narrow) today.weekday.take(3) else today.weekday,
            color = FaceText,
            fontSize = if (narrow) 12.sp else 14.sp,
            maxLines = 1,
            overflow = if (narrow) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        Text(
            text = today.day.toString(),
            color = FaceText,
            fontSize = if (narrow) 34.sp else if (big) 60.sp else 44.sp,
            lineHeight = if (narrow) 34.sp else if (big) 60.sp else 44.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-2).sp,
            maxLines = 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        // Month name, e.g. "june".
        Text(
            text = if (narrow) today.month.take(3) else today.month,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (narrow) 11.sp else 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        // The "calendar" caption only has room on a tall LARGE tile, or a narrow
        // COLUMN tile (4 rows — as roomy as LARGE, just 1 column wide). MEDIUM
        // and WIDE share LARGE's shorter sibling height with no space left for
        // a fourth line without clipping it, so it's dropped there rather than
        // squeezed in; the face is self-evidently a calendar without it.
        if (big || (narrow && size.rows >= 4)) {
            if (!narrow) Spacer(Modifier.weight(1f))
            Text(
                text = "calendar",
                color = FaceText.copy(alpha = 0.82f),
                fontSize = if (narrow) 11.sp else 12.sp,
                maxLines = 1,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
    }
}

@Composable
private fun CalendarFaceColumn(heading: String, event: CalendarEvent, size: TileSize = TileSize.MEDIUM) {
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = heading,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (narrow) 11.sp else 12.sp,
            maxLines = 1,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (!narrow) Spacer(Modifier.height(3.dp))
        Text(
            text = event.title,
            color = FaceText,
            fontSize = if (narrow) 14.sp else 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = if (narrow) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (!narrow) Spacer(Modifier.height(3.dp))
        Text(
            text = event.timeLine,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = if (narrow) 11.sp else 12.sp,
            maxLines = if (narrow) 2 else 1,
            overflow = if (narrow) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}
