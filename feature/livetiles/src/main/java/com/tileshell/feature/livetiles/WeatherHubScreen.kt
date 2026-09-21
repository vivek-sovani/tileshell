package com.tileshell.feature.livetiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.WeatherTile
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens

/**
 * Full-screen weather hub (Windows-Phone-Panorama-style: a small app label, a
 * huge lowercase hub title, then current conditions / hourly / daily) — the
 * destination for tapping a weather tile, replacing the old "open a google.com
 * search" fallback. Reads the same [WeatherCache] the tile faces already read
 * and write, so it shows whatever is currently cached with no separate fetch
 * path; opening the hub does trigger one refresh ([WeatherRefreshWorker
 * .refreshNow]) so a stale cache updates the moment the user actually looks.
 *
 * [location] is the tapped tile's own location (device-follow or a fixed
 * place) — the hub shows that tile's forecast, not necessarily the device's.
 * Follows the same visible/progress/[SheetStage] convention as [AboutSheet]
 * et al. in `:feature:personalize` (full-height sheet, not a partial one).
 */
@Composable
fun WeatherHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    location: WeatherTile.Location?,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "weatherHubProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val resolved = location ?: WeatherTile.Location.Current
    val cache = remember(context) { WeatherCache.create(context) }
    val snapshot = cache.data.collectAsState(initial = WeatherCacheData()).value.snapshotFor(resolved)

    // Refresh once whenever the hub actually opens, so a stale cache updates
    // right when it's looked at — the tiles' own staleness gate is tuned for
    // ambient background refresh, not "the user is looking at this right now".
    LaunchedEffect(visible, resolved) {
        if (visible) WeatherRefreshWorker.refreshNow(context)
    }

    BackHandler(enabled = visible) { onDismiss() }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TileIcons["back"],
                    contentDescription = "back",
                    tint = tokens.fg,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "tileshell",
                color = tokens.fgDim,
                fontSize = 12.sp,
            )
            Text(
                text = "weather",
                color = accent,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = weatherHubSubtitle(snapshot),
                color = tokens.fgDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(24.dp))

            if (snapshot == null) {
                Text(
                    text = "no forecast yet",
                    color = tokens.fgDim,
                    fontSize = 14.sp,
                )
            } else {
                CurrentConditions(snapshot, accent, tokens)
                if (snapshot.forecast.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("daily", tokens)
                    Spacer(Modifier.height(8.dp))
                    DailyForecastRow(snapshot.forecast, accent, tokens)
                }
                if (snapshot.hourly.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("hourly", tokens)
                    Spacer(Modifier.height(8.dp))
                    HourlyForecastRow(snapshot.hourly, accent, tokens)
                }
            }
        }
    }
}

/** "place · updated Xm ago", degrading gracefully as pieces go missing. */
private fun weatherHubSubtitle(snapshot: WeatherSnapshot?): String {
    if (snapshot == null) return ""
    val place = snapshot.place.ifBlank { null }
    val updated = "updated ${feedAgo(snapshot.fetchedAtMillis)} ago"
    return listOfNotNull(place, updated).joinToString(" · ")
}

@Composable
private fun SectionLabel(text: String, tokens: ColorTokens) {
    Text(text = text, color = tokens.fgDim, fontSize = 11.sp)
}

@Composable
private fun CurrentConditions(snapshot: WeatherSnapshot, accent: Color, tokens: ColorTokens) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = tempLabel(snapshot.tempC),
                color = tokens.fg,
                fontSize = 64.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.condition,
                color = tokens.fgDim,
                fontSize = 14.sp,
            )
        }
        WeatherConditionVisual(
            condition = snapshot.condition,
            tint = accent,
            modifier = Modifier.size(52.dp),
        )
    }
    val detail = weatherHubDetailLine(snapshot)
    if (detail.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(text = detail, color = tokens.fgDim, fontSize = 12.sp)
    }
}

/** "feels like 30° · wind 14 km/h · humidity 54%", skipping any missing field. */
private fun weatherHubDetailLine(snapshot: WeatherSnapshot): String =
    listOfNotNull(
        snapshot.feelsLikeC?.let { "feels like ${tempLabel(it)}" },
        snapshot.windKph?.let { "wind $it km/h" },
        snapshot.humidityPct?.let { "humidity $it%" },
    ).joinToString("  ·  ")

@Composable
private fun DailyForecastRow(days: List<DailyForecast>, accent: Color, tokens: ColorTokens) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days) { day ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.chip)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .width(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(day.dayLabel.take(3), color = tokens.fgDim, fontSize = 10.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                WeatherConditionVisual(condition = day.condition, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(6.dp))
                Text(tempLabel(day.highC), color = tokens.fg, fontSize = 12.sp, maxLines = 1)
                Text(tempLabel(day.lowC), color = tokens.fgDim, fontSize = 11.sp, maxLines = 1)
                day.precipProbabilityMax?.takeIf { it > 0 }?.let { precip ->
                    Spacer(Modifier.height(4.dp))
                    Text("$precip%", color = tokens.fgDim, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastRow(hours: List<HourlyForecast>, accent: Color, tokens: ColorTokens) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(hours) { hour ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hour.hourLabel, color = tokens.fgDim, fontSize = 10.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                WeatherConditionVisual(condition = hour.condition, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(6.dp))
                Text(tempLabel(hour.tempC), color = tokens.fg, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
