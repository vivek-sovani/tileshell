package com.tileshell.feature.livetiles

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.clock.ClockSessions
import com.tileshell.core.data.clock.ClockStore
import com.tileshell.core.data.clock.formatDuration
import com.tileshell.core.data.clock.flatten
import com.tileshell.core.data.clock.sessionForSet
import com.tileshell.core.data.clock.totalMs
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons

/**
 * A timer set pinned to Start ([setId]): its name, the countdown of the running
 * step (or the set's length when idle) and a start / pause button with a stop
 * button while it runs. A 1x1 tile is the start / pause button itself. The
 * buttons work only while [interactive] (not in edit mode); the rest of the
 * tile opens the clock hub like any hub tile.
 */
@Composable
fun TimerSetTileFace(size: TileSize, active: Boolean, interactive: Boolean, setId: String?, modifier: Modifier = Modifier) {
    val color = LocalTileFaceColor.current
    val context = LocalContext.current
    val sets by ClockStore.setsFlow(context).collectAsState()
    val sessions by ClockSessions.flow(context).collectAsState()
    val set = sets.firstOrNull { it.id == setId }
    val session = sessionForSet(sessions, setId)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ticking = session != null && !session.paused && active
    LaunchedEffect(session?.id, session?.paused, active) {
        now = System.currentTimeMillis()
        while (ticking) {
            ClockSessions.tick(context)
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L - now % 1000L)
        }
    }

    val name = set?.name?.ifBlank { null } ?: if (set == null) "timer set" else "untitled set"
    val steps = remember(set) { set?.let { flatten(it).size } ?: 0 }
    val canStart = session != null || steps > 0
    val time = when {
        session != null -> formatDuration((session.remainingMs(now) + 999) / 1000)
        set != null -> formatDuration(totalMs(set) / 1000)
        else -> "--:--"
    }
    val sub = when {
        session != null && session.paused -> "paused · " + session.current.label
        session != null -> session.current.label
        set == null -> "set removed"
        else -> "$steps step${if (steps == 1) "" else "s"}"
    }
    val fraction = session?.let { 1f - it.totalRemainingMs(now).toFloat() / it.totalMs().coerceAtLeast(1L) } ?: 0f
    val primary = {
        when {
            session == null -> set?.let { ClockSessions.startSet(context, it) }
            session.paused -> ClockSessions.resume(context, session.id)
            else -> ClockSessions.pause(context, session.id)
        }
        Unit
    }
    val stop = { session?.let { ClockSessions.stop(context, it.id) }; Unit }
    val primaryIcon = if (session != null && !session.paused) "pause" else "play"
    val primaryLabel = if (session == null) "start $name" else if (session.paused) "resume $name" else "pause $name"

    when {
        size == TileSize.SMALL -> Column(
            modifier = modifier.fillMaxSize().clickable(enabled = interactive && canStart, onClick = primary).padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(TileIcons[primaryIcon], contentDescription = primaryLabel, tint = color, modifier = Modifier.size(26.dp))
            if (session != null) Text(time, color = color, fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center)
        }
        size.rows <= 1 -> Row(
            modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = color, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, color = color, fontSize = if (size.cols >= 2) 24.sp else 18.sp, fontWeight = FontWeight.Light, maxLines = 1)
            }
            TimerButton(primaryIcon, primaryLabel, interactive && canStart, 34.dp, color, primary)
            if (session != null && size.cols >= 2) {
                Box(Modifier.size(6.dp))
                TimerButton("stop", "stop $name", interactive, 34.dp, color, stop)
            }
        }
        else -> Column(modifier = modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(name, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, color = color, fontSize = if (size.cols >= 2) 34.sp else 22.sp, fontWeight = FontWeight.Light, maxLines = 1)
                Text(sub, color = color.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = if (size.rows >= 3) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
            if (session != null && size.cols >= 2) {
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(color.copy(alpha = 0.3f))) {
                    Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimerButton(primaryIcon, primaryLabel, interactive && canStart, 38.dp, color, primary)
                if (session != null) TimerButton("stop", "stop $name", interactive, 38.dp, color, stop)
            }
        }
    }
}

/** A round outlined button with a glyph: the tile's start / pause / stop. */
@Composable
private fun TimerButton(icon: String, description: String, enabled: Boolean, diameter: Dp, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(diameter).clip(CircleShape).border(1.5.dp, color, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(TileIcons[icon], contentDescription = description, tint = color, modifier = Modifier.size(diameter * 0.55f))
    }
}
