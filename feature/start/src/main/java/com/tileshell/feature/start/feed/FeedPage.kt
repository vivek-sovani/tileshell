package com.tileshell.feature.start.feed

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.LocalColorTokens
import com.tileshell.feature.livetiles.CalendarFace
import com.tileshell.feature.livetiles.MediaCenter
import com.tileshell.feature.livetiles.NowPlaying
import com.tileshell.feature.livetiles.WeatherCache
import com.tileshell.feature.livetiles.WeatherCacheData
import com.tileshell.feature.livetiles.queryUpcomingEvents
import com.tileshell.feature.livetiles.rememberPermissionGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * The left "feed" page (the 3rd pager page, reached by swiping right from Start).
 * A Discover-style scroll faithful to the standalone prototype's `Feed` module:
 * search pill → glance row → weather → today → discover (articles, sport, stocks)
 * → footer. The live cards reuse existing sources — [WeatherCache], [MediaCenter]
 * and the calendar query — so this page adds no new data plumbing; the discover
 * articles + sport/stock are static placeholders until the RSS engine (S29).
 *
 * @param onSearch fires the user's typed query at Google (the host owns the intent).
 */
@Composable
fun FeedPage(
    accent: Color,
    statusBarTopPx: Float,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalColorTokens.current
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Weather (FR-2): the cached snapshot the weather tile already maintains.
    val weatherCache = remember(context) { WeatherCache.create(context) }
    val weather by weatherCache.data.collectAsStateWithLifecycle(initialValue = WeatherCacheData())
    val snapshot = weather.snapshot

    // Now-playing (reuse MediaCenter): prefer a playing session, else any.
    val media by MediaCenter.nowPlaying.collectAsStateWithLifecycle()
    val nowPlaying: NowPlaying? = media.values.firstOrNull { it.playing } ?: media.values.firstOrNull()

    // Today's agenda (reuse the calendar query); empty until READ_CALENDAR is granted.
    val calGranted = rememberPermissionGranted(Manifest.permission.READ_CALENDAR)
    var agenda by remember { mutableStateOf(CalendarFace(null, null)) }
    LaunchedEffect(calGranted) {
        agenda = if (calGranted) withContext(Dispatchers.IO) { queryUpcomingEvents(context, windowHours = 24) }
        else CalendarFace(null, null)
    }

    val glance = remember { feedGlanceDate(Calendar.getInstance()) }
    val topPad = with(density) { statusBarTopPx.toDp() } + 8.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.bg)
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = topPad, bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchPill(accent = accent, tokens = tokens, onSearch = onSearch)
        GlanceRow(glance = glance, tempC = snapshot?.tempC, tokens = tokens)

        SectionLabel("weather", tokens.fgDim)
        WeatherCard(snapshot = snapshot, tokens = tokens)

        SectionLabel("today", tokens.fgDim)
        AgendaCard(agenda = agenda, granted = calGranted, accent = accent, tokens = tokens)

        if (nowPlaying != null) {
            NowPlayingCard(nowPlaying = nowPlaying, accent = accent, tokens = tokens)
        }

        // News (discover), sport scores and a live stock watchlist arrive with the
        // RSS / market-data engine (S29). Until then this page shows only cards
        // backed by real data — no sample content.
    }
}

/* ---------------------------------------------------------------- cards ---- */

@Composable
private fun SearchPill(
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onSearch: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.sheet)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(21.dp)) {
            val r = size.minDimension * 0.30f
            val c = Offset(size.width * 0.42f, size.height * 0.42f)
            drawCircle(tokens.fg, radius = r, center = c, style = Stroke(width = size.width * 0.08f))
            drawLine(
                tokens.fg,
                start = Offset(c.x + r * 0.7f, c.y + r * 0.7f),
                end = Offset(size.width * 0.86f, size.height * 0.86f),
                strokeWidth = size.width * 0.08f,
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.width(11.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = tokens.fg, fontSize = 16.sp),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (query.isNotBlank()) onSearch(query)
                focus.clearFocus()
            }),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("search", color = tokens.fgDim, fontSize = 16.sp)
                inner()
            },
        )
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("g", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GlanceRow(glance: GlanceDate, tempC: Int?, tokens: com.tileshell.core.design.ColorTokens) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(glance.weekday, color = tokens.fg, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Text(glance.sub, color = tokens.fgDim, fontSize = 13.sp)
        }
        Text(
            tempC?.let { "$it°" } ?: "—",
            color = tokens.fg,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(text, color = color, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

@Composable
private fun GCard(tokens: com.tileshell.core.design.ColorTokens, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.sheet),
    ) { content() }
}

@Composable
private fun WeatherCard(
    snapshot: com.tileshell.feature.livetiles.WeatherSnapshot?,
    tokens: com.tileshell.core.design.ColorTokens,
) {
    GCard(tokens) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (snapshot == null) {
                Text("weather unavailable", color = tokens.fg, fontSize = 16.sp)
                Text(
                    "set a location or allow location access",
                    color = tokens.fgDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                return@Column
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        snapshot.place.ifEmpty { "your location" },
                        color = tokens.fg,
                        fontSize = 18.sp,
                    )
                    Text(snapshot.condition, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Text("${snapshot.tempC}°", color = tokens.fg, fontSize = 42.sp, fontWeight = FontWeight.Thin)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatCol("now", "${snapshot.tempC}°", tokens)
                StatCol("high", "${snapshot.highC}°", tokens)
                StatCol("low", "${snapshot.lowC}°", tokens)
            }
            if (snapshot.detail.isNotEmpty()) {
                Text(snapshot.detail, color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun StatCol(label: String, value: String, tokens: com.tileshell.core.design.ColorTokens) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = tokens.fgDim, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = tokens.fg, fontSize = 14.sp)
    }
}

@Composable
private fun AgendaCard(
    agenda: CalendarFace,
    granted: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
) {
    GCard(tokens) {
        Column(modifier = Modifier.padding(16.dp)) {
            val events = listOfNotNull(agenda.next, agenda.following)
            when {
                !granted -> Text("allow calendar to see your day", color = tokens.fgDim, fontSize = 14.sp)
                events.isEmpty() -> Text("nothing on your calendar today", color = tokens.fgDim, fontSize = 14.sp)
                else -> events.forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(e.title.ifEmpty { "(busy)" }, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(e.timeLine, color = tokens.fgDim, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    nowPlaying: NowPlaying,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
) {
    GCard(tokens) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    val p = Path().apply {
                        moveTo(0f, 0f); lineTo(0f, size.height); lineTo(size.width, size.height / 2f); close()
                    }
                    drawPath(p, Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("now playing", color = tokens.fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    listOf(nowPlaying.title, nowPlaying.artist).filter { it.isNotBlank() }.joinToString(" · "),
                    color = tokens.fgDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

