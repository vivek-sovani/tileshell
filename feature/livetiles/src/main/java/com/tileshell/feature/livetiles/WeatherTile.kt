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
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
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

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current

/**
 * The live weather tile (FR-2). Schedules the background refresh, asks for coarse
 * location once (opt-in), and renders the cached [WeatherSnapshot]. When there is
 * no cached snapshot — location denied and no manual city, or the first fetch
 * hasn't landed — it shows [fallback] (the static glyph), so the tile degrades
 * gracefully. [flipped] turns between current conditions and today's range.
 */
@Composable
fun WeatherTileFace(
    size: TileSize,
    flipped: Boolean,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WeatherRefreshWorker.ensureScheduled(context) }
    val locationGranted = rememberPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(locationGranted) {
        if (locationGranted) WeatherRefreshWorker.refreshNow(context)
    }

    val cache = remember(context) { WeatherCache.create(context) }
    val snapshot = cache.data.collectAsState(initial = WeatherCacheData()).value.snapshot
        ?: return fallback()

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { WeatherFront(snapshot, size) },
        back = { WeatherBack(snapshot, size) },
    )
}

/**
 * The compact weather face for a small (1×1) tile: just the current temperature,
 * centred (FR-2). Same data + opt-in as [WeatherTileFace] (shared [WeatherCache],
 * coarse-location ask, background refresh) so a standalone small weather tile
 * still fetches. Never flips; degrades to [fallback] when no snapshot is cached.
 */
@Composable
fun WeatherSmallFace(
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WeatherRefreshWorker.ensureScheduled(context) }
    val locationGranted = rememberPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(locationGranted) {
        if (locationGranted) WeatherRefreshWorker.refreshNow(context)
    }

    val cache = remember(context) { WeatherCache.create(context) }
    val snapshot = cache.data.collectAsState(initial = WeatherCacheData()).value.snapshot
        ?: return fallback()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = tempLabel(snapshot.tempC),
            color = FaceText,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-1).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeatherFront(snapshot: WeatherSnapshot, size: TileSize) {
    // WIDE and MEDIUM share the same 2-row height (only LARGE's 3 rows have the
    // extra vertical room for the enlarged temperature) — sizing "big" off WIDE
    // clipped the condition line at the bottom of a WIDE stack member.
    val big = size == TileSize.LARGE
    // TALL/COLUMN are only 1 column wide (same as SMALL) — the place/condition
    // lines above clip at that width, so narrow tiles get a centred, width-safe
    // layout instead, spread across whatever row height the tile has.
    val narrow = size.narrowLive
    // WIDE_SMALL/BANNER are the row-axis squeeze instead: plenty of width, but
    // only one grid row tall, so the normal place/temp/condition stack clips
    // its last line unless every size in it shrinks to fit (unlike [narrow],
    // there's no width problem here, so the layout/alignment stays the same
    // left-aligned stack — just smaller text and tighter spacing).
    val short = size.shortLive
    val tempSize = if (narrow) 34.sp else if (short) 26.sp else if (big) 60.sp else 40.sp
    val labelSize = if (narrow) 11.sp else if (short) 10.sp else 13.sp
    val place = snapshot.place.ifBlank { "weather" }
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        // Location name (prototype shows none; the user asked for it).
        Text(
            text = place,
            color = FaceText,
            fontSize = labelSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (!narrow && !short) Spacer(Modifier.height(2.dp))
        Text(
            text = tempLabel(snapshot.tempC),
            color = FaceText,
            fontSize = tempSize,
            lineHeight = tempSize * 0.9f,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-2).sp,
            maxLines = 1,
            overflow = if (narrow) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (!narrow && !short) Spacer(Modifier.height(4.dp))
        Text(
            text = snapshot.condition,
            color = FaceText,
            fontSize = labelSize,
            maxLines = if (narrow) 2 else 1,
            overflow = if (narrow) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

@Composable
private fun WeatherBack(snapshot: WeatherSnapshot, size: TileSize) {
    val narrow = size.narrowLive
    val short = size.shortLive
    Column(
        modifier = Modifier.fillMaxSize().padding(if (narrow || short) 4.dp else 11.dp),
        verticalArrangement = if (narrow) Arrangement.SpaceEvenly else Arrangement.Center,
        horizontalAlignment = if (narrow) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = snapshot.place.ifBlank { "today" },
            color = FaceText.copy(alpha = 0.9f),
            fontSize = if (narrow) 11.sp else if (short) 10.sp else 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (!narrow && !short) Spacer(Modifier.height(6.dp))
        Text(
            text = highLowLabel(snapshot.highC, snapshot.lowC),
            color = FaceText,
            fontSize = if (narrow) 18.sp else if (short) 15.sp else 22.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = if (narrow) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
        )
        if (snapshot.detail.isNotEmpty()) {
            if (!narrow && !short) Spacer(Modifier.weight(1f))
            Text(
                text = snapshot.detail,
                color = FaceText.copy(alpha = 0.82f),
                fontSize = if (narrow) 11.sp else if (short) 10.sp else 12.sp,
                maxLines = if (narrow) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Unspecified,
            )
        }
    }
}
