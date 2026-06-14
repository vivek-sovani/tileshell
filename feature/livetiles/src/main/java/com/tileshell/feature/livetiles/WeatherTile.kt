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
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize

private val FaceText = Color.White

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
    // Opt-in: request coarse location; refresh immediately once it is granted.
    rememberOptInPermission(
        permission = Manifest.permission.ACCESS_COARSE_LOCATION,
        onGranted = { WeatherRefreshWorker.refreshNow(context) },
    )

    val cache = remember(context) { WeatherCache.create(context) }
    val snapshot = cache.data.collectAsState(initial = WeatherCacheData()).value.snapshot
        ?: return fallback()

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { WeatherFront(snapshot, size) },
        back = { WeatherBack(snapshot) },
    )
}

@Composable
private fun WeatherFront(snapshot: WeatherSnapshot, size: TileSize) {
    val big = size == TileSize.WIDE || size == TileSize.LARGE
    val tempSize = if (big) 60.sp else 40.sp
    Column(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = tempLabel(snapshot.tempC),
            color = FaceText,
            fontSize = tempSize,
            lineHeight = tempSize * 0.9f,
            fontWeight = FontWeight.ExtraLight,
            letterSpacing = (-2).sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = snapshot.condition, color = FaceText, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(text = "weather", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun WeatherBack(snapshot: WeatherSnapshot) {
    Column(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "today", color = FaceText.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(
            text = highLowLabel(snapshot.highC, snapshot.lowC),
            color = FaceText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
        )
        if (snapshot.detail.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            Text(
                text = snapshot.detail,
                color = FaceText.copy(alpha = 0.82f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}
