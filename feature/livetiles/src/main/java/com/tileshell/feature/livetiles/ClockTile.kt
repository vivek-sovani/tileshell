package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
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
 * The text shown on the clock tile's two faces (prototype `clockNow()`). 24-hour
 * `h:mm` with full lowercase weekday and date; the back shows the date again with
 * the next alarm. [alarm] is a placeholder until the alarm provider lands.
 */
data class ClockFace(
    val hm: String,
    val weekday: String,
    val fullDate: String,
    val alarm: String = "7:00",
)

/**
 * Builds a [ClockFace] from calendar fields. Pure (no `Calendar.getInstance()`)
 * so the formatting — 24-hour, zero-padded minutes, lowercase names — is
 * unit-testable. [dayOfWeek] is Calendar's 1=Sunday convention; [month0] is
 * 0-based (0=January), matching both Calendar and the prototype's JS `Date`.
 */
fun clockFace(
    hour24: Int,
    minute: Int,
    dayOfWeek: Int,
    dayOfMonth: Int,
    month0: Int,
    year: Int,
): ClockFace = ClockFace(
    hm = "$hour24:${minute.toString().padStart(2, '0')}",
    weekday = WEEKDAYS[dayOfWeek - 1],
    fullDate = "$dayOfMonth ${MONTHS[month0]} $year",
)

private fun currentClockFace(): ClockFace {
    val c = Calendar.getInstance()
    return clockFace(
        hour24 = c.get(Calendar.HOUR_OF_DAY),
        minute = c.get(Calendar.MINUTE),
        dayOfWeek = c.get(Calendar.DAY_OF_WEEK),
        dayOfMonth = c.get(Calendar.DAY_OF_MONTH),
        month0 = c.get(Calendar.MONTH),
        year = c.get(Calendar.YEAR),
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
    var face by remember { mutableStateOf(currentClockFace()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            face = currentClockFace()
            // Align the next tick to the start of the next minute.
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { ClockFront(face, size) },
        back = { ClockBack(face) },
    )
}

private val FaceText = Color.White

@Composable
private fun ClockFront(face: ClockFace, size: TileSize) {
    // Prototype `.lc .xl` (styles.css): 64px wide / 42px medium, weight 200,
    // line-height .9, letter-spacing -2px. CSS lets the tall glyphs overflow the
    // .9 line box harmlessly; Compose would crop them (the time vanished at the
    // earlier inflated size), so trim = None keeps the full glyph painted.
    val big = size == TileSize.WIDE
    val timeSize = if (big) 64.sp else 42.sp
    Column(
        modifier = Modifier.fillMaxSize().padding(11.dp),
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
        Spacer(Modifier.height(4.dp))
        Text(text = face.weekday, color = FaceText, fontSize = 15.sp, maxLines = 1)
        Text(
            text = face.fullDate,
            color = FaceText.copy(alpha = 0.82f),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ClockBack(face: ClockFace) {
    Column(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = face.fullDate,
            color = FaceText,
            fontSize = 30.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-1).sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "alarm ${face.alarm}",
            color = FaceText.copy(alpha = 0.82f),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}
