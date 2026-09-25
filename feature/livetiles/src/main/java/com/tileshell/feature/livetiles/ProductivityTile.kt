package com.tileshell.feature.livetiles

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TaskRepository
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import kotlinx.coroutines.delay

// Same slow dwell as the People Hub page tiles.
private const val PRODUCTIVITY_FLIP_MS = 15_000L

/**
 * The productivity hub's own Start tile: the front shows the next meeting
 * (with a "join" button when it has a meeting link) and how many tasks are
 * open; the back shows the most used productivity apps. Tapping an app icon
 * or "join" acts directly; a tap anywhere else opens the hub (routed by
 * Start on the tile's "productivity" icon key). Flips on its own dwell
 * while [active], like the People Hub page tiles.
 */
@Composable
fun ProductivityTileFace(size: TileSize, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val calendarGranted = rememberPermissionGranted(Manifest.permission.READ_CALENDAR)
    val meetings = rememberUpcomingMeetings(calendarGranted)
    val openCount by remember(context) { TaskRepository.create(context).openCount() }.collectAsState(initial = 0)
    val apps = rememberProductivityApps().orEmpty()

    var flipped by remember { mutableStateOf(false) }
    val canFlip = apps.isNotEmpty() && size.rows >= 2
    LaunchedEffect(active, canFlip) {
        if (!active || !canFlip) {
            flipped = false
            return@LaunchedEffect
        }
        while (true) {
            delay(PRODUCTIVITY_FLIP_MS)
            flipped = !flipped
        }
    }
    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { ProductivityFront(meetings.firstOrNull(), openCount, size) },
        back = { ProductivityAppsBack(apps, size) },
    )
}

@Composable
private fun ProductivityFront(meeting: UpcomingMeeting?, openCount: Int, size: TileSize) {
    val context = LocalContext.current
    val color = LocalTileFaceColor.current
    val now = System.currentTimeMillis()
    Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (meeting != null) {
                Text(
                    meetingTimeLabel(meeting.startMillis, meeting.endMillis, now).substringAfter("· "),
                    color = color.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
                Text(
                    meeting.title.lowercase(),
                    color = color,
                    fontSize = if (size.cols >= 4) 18.sp else 14.sp,
                    maxLines = if (size.rows >= 2) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meetingTimeLabel(meeting.startMillis, meeting.endMillis, now).substringBefore(" ·") +
                        (meeting.link?.let { " · ${it.provider}" } ?: ""),
                    color = color.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meeting.link != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "join",
                        color = Color(0xFF0A0A0D),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { openMeetingLink(context, meeting.link.url) },
                            )
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
            } else {
                Text("no more meetings today", color = color.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 2)
            }
            if (size.rows >= 2) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (openCount > 0) "$openCount task${if (openCount == 1) "" else "s"} open" else "no open tasks",
                    color = color,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
        Text("productivity", color = color, fontSize = 11.sp, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ProductivityAppsBack(apps: List<ProductivityApp>, size: TileSize) {
    val context = LocalContext.current
    val color = LocalTileFaceColor.current
    val columns = size.cols.coerceAtLeast(1)
    val rows = size.rows.coerceAtLeast(1)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 18.dp)) {
            apps.take(columns * rows).chunked(columns).forEach { rowApps ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    rowApps.forEach { app ->
                        val icon = rememberMonochromeAppIcon(app.packageName, sizePx = 96)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { openApp(context, app.packageName) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = app.label,
                                    colorFilter = ColorFilter.tint(color),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    repeat(columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Text("productivity", color = color, fontSize = 11.sp, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp))
    }
}
