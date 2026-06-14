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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val FaceText = Color.White
private const val REFRESH_MS = 5 * 60_000L

/**
 * The live calendar tile (FR-2). Asks for READ_CALENDAR once (opt-in) and, while
 * [active], polls the provider for the next two upcoming events, re-querying
 * every few minutes so a started/finished meeting rolls off. The front shows the
 * next event, the back the following one. When the permission is denied or there
 * is nothing coming up it renders [fallback] (the static glyph).
 */
@Composable
fun CalendarTileFace(
    flipped: Boolean,
    active: Boolean,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val granted = rememberOptInPermission(Manifest.permission.READ_CALENDAR)

    var face by remember { mutableStateOf<CalendarFace?>(null) }
    LaunchedEffect(granted, active) {
        if (!granted || !active) return@LaunchedEffect
        while (true) {
            face = runCatching {
                withContext(Dispatchers.IO) { queryUpcomingEvents(context) }
            }.getOrNull()
            delay(REFRESH_MS)
        }
    }

    val next = face?.next ?: return fallback()

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { CalendarFaceColumn(heading = "next", event = next) },
        back = {
            val following = face?.following
            if (following != null) {
                CalendarFaceColumn(heading = "later", event = following)
            } else {
                CalendarFaceColumn(heading = "next", event = next)
            }
        },
    )
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
