package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.data.clock.ClockSessions
import com.tileshell.core.data.clock.formatDuration
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import kotlinx.coroutines.delay

/**
 * The clock hub's tile: the running timer or timer set with its countdown
 * (ticking only while [active]), else the next alarm, else the stopwatch glyph.
 * Tapping opens the hub.
 */
@Composable
fun ClockHubTileFace(size: TileSize, active: Boolean, modifier: Modifier = Modifier) {
    val color = LocalTileFaceColor.current
    val context = LocalContext.current
    val sessions by ClockSessions.flow(context).collectAsState()
    val ticking = active && sessions.isNotEmpty() && size != TileSize.SMALL
    val now by produceState(System.currentTimeMillis(), ticking) {
        value = System.currentTimeMillis()
        while (ticking) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            value = System.currentTimeMillis()
            ClockSessions.tick(context)
        }
    }
    val alarm = remember(now / 60_000) { nextAlarmString(context) }
    val first = sessions.firstOrNull()
    Box(modifier = modifier.fillMaxSize()) {
        when {
            size == TileSize.SMALL || (first == null && alarm.isEmpty()) -> {
                Icon(TileIcons["clockhub"], contentDescription = null, tint = color, modifier = Modifier.align(Alignment.Center).size(if (size == TileSize.SMALL) 28.dp else 44.dp))
                if (size != TileSize.SMALL) Text("clock", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            first != null -> Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(first.title, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column {
                    Text(
                        formatDuration((first.remainingMs(now) + 999) / 1000),
                        color = color, fontSize = if (size.cols >= 2) 32.sp else 22.sp, fontWeight = FontWeight.Light, maxLines = 1,
                    )
                    Text(
                        if (first.paused) "paused" else first.current.label,
                        color = color, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(if (sessions.size > 1) "+${sessions.size - 1} more" else "clock", color = color, fontSize = 12.sp)
            }
            else -> Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("next alarm", color = color, fontSize = 12.sp)
                Text(alarm, color = color, fontSize = if (size.cols >= 2) 30.sp else 20.sp, fontWeight = FontWeight.Light)
                Text("clock", color = color, fontSize = 12.sp)
            }
        }
    }
}
