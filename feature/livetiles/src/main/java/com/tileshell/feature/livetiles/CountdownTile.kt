package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The text shown on the countdown tile's two faces. [hasDate] is false until
 * the user has actually picked a target date in [com.tileshell.feature.
 * personalize.CountdownEditorSheet] — a freshly-pinned tile starts this way.
 */
data class CountdownFace(
    val hasDate: Boolean,
    val label: String,
    val daysRemaining: Long,
    val dateText: String,
)

/**
 * "today" / "tomorrow" / "yesterday" / "in N days" / "N days ago" — pure, no
 * [LocalDate] arithmetic inside, so the wording is unit-testable directly off
 * a plain day count.
 */
fun countdownStatusText(daysRemaining: Long): String = when {
    daysRemaining == 0L -> "today"
    daysRemaining == 1L -> "tomorrow"
    daysRemaining == -1L -> "yesterday"
    daysRemaining > 1L -> "in $daysRemaining days"
    else -> "${-daysRemaining} days ago"
}

/**
 * Pure — [today]/[target] are plain [LocalDate] value objects (no `Calendar`/
 * `Context` call inside), so day-arithmetic is exactly this library's own
 * unit-testable, matching every other live face's "pure core + Composable
 * shell" split.
 */
fun countdownFace(label: String, target: LocalDate, today: LocalDate): CountdownFace = CountdownFace(
    hasDate = true,
    label = label.ifBlank { "countdown" },
    daysRemaining = ChronoUnit.DAYS.between(today, target),
    dateText = "${target.dayOfMonth} ${MONTHS[target.monthValue - 1]} ${target.year}",
)

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live countdown tile: one target date per pinned tile (see
 * [com.tileshell.core.data.CountdownTile]). Only needs to refresh once a day,
 * not per-minute like the clock — recomputed on [Lifecycle.Event.ON_RESUME]
 * (same "cheap enough to just re-check on resume" convention
 * [rememberBatteryFace] uses) rather than running a delay loop for an exact
 * midnight rollover.
 */
@Composable
fun CountdownTileFace(
    size: TileSize,
    flipped: Boolean,
    targetIsoDate: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var today by remember { mutableStateOf(LocalDate.now()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val target = remember(targetIsoDate) { runCatching { LocalDate.parse(targetIsoDate) }.getOrNull() }
    val face = remember(target, today, label) {
        target?.let { countdownFace(label, it, today) }
            ?: CountdownFace(hasDate = false, label = "", daysRemaining = 0, dateText = "")
    }

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { CountdownFront(face, size) },
        back = { CountdownBack(face, size) },
    )
}

/** The compact 1×1 face (ICONS home style / SMALL tile): just the day count. */
@Composable
fun CountdownSmallFace(targetIsoDate: String, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var today by remember { mutableStateOf(LocalDate.now()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val target = remember(targetIsoDate) { runCatching { LocalDate.parse(targetIsoDate) }.getOrNull() }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (target == null) {
            Icon(imageVector = TileIcons["countdown"], contentDescription = null, tint = FaceText)
        } else {
            val days = remember(target, today) { ChronoUnit.DAYS.between(today, target) }
            Text(
                text = days.toString(),
                color = FaceText,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-1).sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CountdownFront(face: CountdownFace, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    val big = size == TileSize.LARGE
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (face.hasDate) {
            Text(
                text = face.label,
                color = FaceText,
                fontSize = if (short) 15.sp else if (narrow) 15.sp else if (big) 22.sp else 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (narrow) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
            Text(
                text = countdownStatusText(face.daysRemaining),
                color = FaceText.copy(alpha = 0.9f),
                fontSize = if (short) 18.sp else if (narrow) 18.sp else if (big) 38.sp else 26.sp,
                lineHeight = if (short) 18.sp else if (narrow) 18.sp else if (big) 38.sp else 26.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        } else {
            Text(
                text = "tap to set a date",
                color = FaceText.copy(alpha = 0.82f),
                fontSize = if (short) 12.sp else 14.sp,
                maxLines = if (narrow) 2 else 1,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
        if (big) {
            Spacer(Modifier.weight(1f))
            Text("countdown", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun CountdownBack(face: CountdownFace, size: TileSize) {
    val narrow = size.narrowLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow) 4.dp else 11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = "countdown",
            color = FaceText,
            fontSize = if (narrow) 20.sp else 26.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-1).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (face.hasDate) face.dateText else "tap to edit",
            color = FaceText.copy(alpha = 0.65f),
            fontSize = if (narrow) 11.sp else 13.sp,
            maxLines = 2,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}
