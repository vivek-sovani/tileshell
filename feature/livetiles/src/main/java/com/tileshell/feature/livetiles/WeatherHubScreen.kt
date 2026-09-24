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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

private val HUB_PIVOTS = listOf("today", "daily", "hourly")

/**
 * Full-screen weather hub — real WP Panorama/Pivot shape: a fixed header (small
 * app label, huge lowercase hub title, place/updated subtitle), then a row of
 * pivot labels ("today"/"daily"/"hourly") the user can tap or swipe between,
 * each its own independently-scrolling page. The destination for tapping a
 * weather tile, replacing the old "open a google.com search" fallback.
 *
 * Reads the same [WeatherCache] the tile faces already read and write, so it
 * shows whatever is currently cached with no separate fetch path; opening the
 * hub does trigger one refresh ([WeatherRefreshWorker.refreshNow]) so a stale
 * cache updates the moment the user actually looks.
 *
 * [location] is the tapped tile's own location (device-follow or a fixed
 * place) — the hub shows that tile's forecast, not necessarily the device's.
 * Follows the same visible/progress/[SheetStage] convention as `AboutSheet`
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

    val pagerState = rememberPagerState(pageCount = { HUB_PIVOTS.size })
    val pagerScope = rememberCoroutineScope()

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                // Swallows every tap on this screen, same as every other
                // sheet's own content column (see AboutSheet) — without
                // this, a tap that misses a specific button falls through
                // to whatever Start tile sits at that same screen position
                // underneath, since a plain background() doesn't consume
                // touches on its own. Real user-reported bug: tapping near
                // "daily"/"hourly" sometimes opened a Start calendar tile's
                // own google-search fallback instead.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(20.dp))
                Text(text = "tileshell", color = tokens.fgDim, fontSize = 12.sp)
                Text(
                    text = "weather",
                    color = accent,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                // The location itself, not a caption — real WP weather apps
                // give the place its own prominent line right under the hub
                // title (foreground weight, well above caption size), with
                // "updated" folded into the today page instead of crowding it.
                snapshot?.place?.ifBlank { null }?.let { place ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = place,
                        color = tokens.fg,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))

                // The pivot row — tap a label to jump there, same destination
                // a swipe on the pager below reaches.
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    HUB_PIVOTS.forEachIndexed { index, label ->
                        val selected = pagerState.currentPage == index
                        Text(
                            text = label,
                            color = if (selected) tokens.fg else tokens.fgDim,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { pagerScope.launch { pagerState.animateScrollToPage(index) } },
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (snapshot == null) {
                Text(
                    text = "no forecast yet",
                    color = tokens.fgDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp)
                            .padding(bottom = 16.dp),
                    ) {
                        when (page) {
                            0 -> CurrentConditionsPage(snapshot, accent, tokens)
                            1 -> DailyForecastPage(snapshot.forecast, accent, tokens)
                            else -> HourlyForecastPage(snapshot.hourly, accent, tokens)
                        }
                    }
                }
            }

            // Bottom app bar (mockup's own convention) — back lives here, not
            // as a standalone top-corner button; refresh is a real manual
            // re-fetch alongside the automatic one this screen already does
            // on open.
            HubAppBar(
                tokens = tokens,
                actions = listOf(
                    HubAppBarAction("back", "back", onDismiss),
                    HubAppBarAction("refresh", "refresh") { WeatherRefreshWorker.refreshNow(context) },
                ),
            )
        }
    }
}

@Composable
private fun CurrentConditionsPage(snapshot: WeatherSnapshot, accent: Color, tokens: ColorTokens) {
    // "Updated Xm ago" — matches the real weather app's ordering: place is the
    // header's own line (see WeatherHubScreen), this timestamp sits with the
    // reading it actually describes, not crowding the place name above.
    // feedAgo returns the bare word "now" for a just-fetched snapshot, which
    // reads as "updated now ago" if "ago" is appended unconditionally.
    val ago = feedAgo(snapshot.fetchedAtMillis)
    Text(
        text = if (ago == "now") "updated just now" else "updated $ago ago",
        color = tokens.fgDim,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(12.dp))
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
            Text(text = snapshot.condition, color = tokens.fgDim, fontSize = 14.sp)
            // Today's range (user-requested), labelled explicitly rather than the
            // tile's bare "26° / 17°" since this page has the room.
            Spacer(Modifier.height(4.dp))
            Text(
                text = weatherHubHighLowLine(snapshot.highC, snapshot.lowC),
                color = tokens.fg,
                fontSize = 14.sp,
            )
        }
        WeatherConditionVisual(
            condition = snapshot.condition,
            tint = accent,
            modifier = Modifier.size(52.dp),
            night = rememberWeatherNight(snapshot),
        )
    }
    // Labelled two-column grid (user-requested sunrise/sunset/uv alongside the
    // existing feels-like/wind/humidity) — one joined line no longer fits.
    val stats = weatherHubStats(snapshot)
    if (stats.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        stats.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = label, color = tokens.fgDim, fontSize = 11.sp)
                        Text(text = value, color = tokens.fg, fontSize = 16.sp, maxLines = 1)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "max 31°  ·  min 22°" — the today page's range line. Pure. */
internal fun weatherHubHighLowLine(highC: Int, lowC: Int): String =
    "max ${tempLabel(highC)}  ·  min ${tempLabel(lowC)}"

/**
 * The today page's label → value stats, skipping any field the snapshot doesn't
 * carry (an old cache file, or a response missing it). Sunrise/sunset are
 * today's (the first forecast day), shown in the forecast place's own local
 * time. Pure.
 */
internal fun weatherHubStats(snapshot: WeatherSnapshot): List<Pair<String, String>> {
    val today = snapshot.forecast.firstOrNull()
    return listOfNotNull(
        snapshot.feelsLikeC?.let { "feels like" to tempLabel(it) },
        snapshot.windKph?.let { "wind speed" to "$it km/h" },
        snapshot.humidityPct?.let { "humidity" to "$it%" },
        snapshot.uvIndexMax?.let { "uv index" to "$it · ${uvIndexCategory(it)}" },
        today?.sunriseMillis?.let { "sunrise" to sunTimeLabel(it, snapshot.utcOffsetSeconds) },
        today?.sunsetMillis?.let { "sunset" to sunTimeLabel(it, snapshot.utcOffsetSeconds) },
    )
}

/** WHO UV index bands, lowercase. Pure. */
internal fun uvIndexCategory(uv: Int): String = when {
    uv <= 2 -> "low"
    uv <= 5 -> "moderate"
    uv <= 7 -> "high"
    uv <= 10 -> "very high"
    else -> "extreme"
}

/**
 * "6:12 am" for [epochMillis] in the place's own [utcOffsetSeconds]; the device
 * zone when that's unknown (a cache file written before it was stored). Pure.
 */
internal fun sunTimeLabel(epochMillis: Long, utcOffsetSeconds: Int?): String {
    val zone = utcOffsetSeconds?.let { java.time.ZoneOffset.ofTotalSeconds(it) } ?: java.time.ZoneId.systemDefault()
    val t = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    val suffix = if (t.hour < 12) "am" else "pm"
    return "$h12:${t.minute.toString().padStart(2, '0')} $suffix"
}

@Composable
private fun DailyForecastPage(days: List<DailyForecast>, accent: Color, tokens: ColorTokens) {
    if (days.isEmpty()) {
        Text("no daily forecast yet", color = tokens.fgDim, fontSize = 13.sp)
        return
    }
    days.forEachIndexed { index, day ->
        if (index > 0) Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.dayLabel,
                color = tokens.fg,
                fontSize = 14.sp,
                modifier = Modifier.width(84.dp),
            )
            WeatherConditionVisual(condition = day.condition, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = day.condition,
                color = tokens.fgDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            day.precipProbabilityMax?.takeIf { it > 0 }?.let { precip ->
                Text("$precip%", color = tokens.fgDim, fontSize = 11.sp, modifier = Modifier.padding(end = 10.dp))
            }
            Text(tempLabel(day.highC), color = tokens.fg, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(tempLabel(day.lowC), color = tokens.fgDim, fontSize = 13.sp)
        }
        if (index < days.lastIndex) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.sheetLine))
        }
    }
}

@Composable
private fun HourlyForecastPage(hours: List<HourlyForecast>, accent: Color, tokens: ColorTokens) {
    if (hours.isEmpty()) {
        Text("no hourly forecast yet", color = tokens.fgDim, fontSize = 13.sp)
        return
    }
    hours.forEachIndexed { index, hour ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = hour.hourLabel,
                color = tokens.fg,
                fontSize = 14.sp,
                modifier = Modifier.width(56.dp),
            )
            WeatherConditionVisual(
                condition = hour.condition,
                tint = accent,
                modifier = Modifier.size(20.dp),
                night = hour.isDay == false,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = hour.condition,
                color = tokens.fgDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(tempLabel(hour.tempC), color = tokens.fg, fontSize = 13.sp)
        }
        if (index < hours.lastIndex) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.sheetLine))
        }
    }
}
