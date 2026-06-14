package com.tileshell.feature.livetiles

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

private val FaceText = Color.White
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
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            today = currentCalendarToday()
            delay(REFRESH_MS)
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
                CalendarFaceColumn(heading = "next", event = next)
            } else {
                CalendarDateColumn(today, size)
            }
        },
    )
}

@Composable
private fun CalendarDateColumn(today: CalendarToday, size: TileSize) {
    val big = size == TileSize.WIDE || size == TileSize.LARGE
    Column(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = today.weekday, color = FaceText, fontSize = 14.sp, maxLines = 1)
        Text(
            text = today.day.toString(),
            color = FaceText,
            fontSize = if (big) 60.sp else 44.sp,
            lineHeight = if (big) 60.sp else 44.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-2).sp,
            maxLines = 1,
        )
        Text(
            text = today.month,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = 13.sp,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Text(text = "calendar", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun CalendarFaceColumn(heading: String, event: CalendarEvent) {
    Column(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = heading, color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(
            text = event.title,
            color = FaceText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(text = event.timeLine, color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1)
    }
}
