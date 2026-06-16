package com.tileshell.feature.start.feed

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
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

        SectionLabel("discover", tokens.fgDim)
        ARTICLES.take(2).forEach { ArticleCard(it, accent, tokens, context) }
        SportCard(tokens)
        ARTICLES.drop(2).take(2).forEach { ArticleCard(it, accent, tokens, context) }
        StockCard(tokens)
        ARTICLES.drop(4).forEach { ArticleCard(it, accent, tokens, context) }

        Text(
            "that's all for now",
            color = tokens.fgDim,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
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

@Composable
private fun SportCard(tokens: com.tileshell.core.design.ColorTokens) {
    GCard(tokens) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("match · full time", color = tokens.fgDim, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            ScoreRow("Northside", "2", Color(0xFF2F9E57), tokens)
            Spacer(Modifier.height(4.dp))
            ScoreRow("Rovers", "1", Color(0xFFD6262B), tokens)
            Spacer(Modifier.height(8.dp))
            Text("2 hours ago · league cup", color = tokens.fgDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScoreRow(team: String, score: String, badge: Color, tokens: com.tileshell.core.design.ColorTokens) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(badge),
                contentAlignment = Alignment.Center,
            ) { Text(team.take(1), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.width(10.dp))
            Text(team, color = tokens.fg, fontSize = 16.sp)
        }
        Text(score, color = tokens.fg, fontSize = 20.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun StockCard(tokens: com.tileshell.core.design.ColorTokens) {
    GCard(tokens) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("watchlist", color = tokens.fgDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            STOCKS.forEach { (name, value, change, up) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = tokens.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(value, color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(
                        change,
                        color = if (up) Color(0xFF2F9E57) else Color(0xFFD6262B),
                        fontSize = 13.sp,
                        modifier = Modifier.width(56.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(
    article: Article,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    context: android.content.Context,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.sheet)
            .clickable { Toast.makeText(context, "live articles arrive with rss feeds", Toast.LENGTH_SHORT).show() },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(article.image),
            ) {
                Text(
                    article.tag,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x80000000))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 13.dp, bottom = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(7.dp))
                    Text(article.source, color = tokens.fgDim, fontSize = 12.sp)
                }
                Text(
                    article.title,
                    color = tokens.fg,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(top = 7.dp, bottom = 10.dp),
                )
                Text(article.meta, color = tokens.fgDim, fontSize = 12.sp)
            }
        }
    }
}

/* ----------------------------------------------------------- static data ---- */

private data class Article(
    val source: String,
    val tag: String,
    val title: String,
    val meta: String,
    val image: Brush,
)

private val ARTICLES = listOf(
    Article("Frontier Daily", "science", "New telescope captures the sharpest image yet of a forming star", "4 min read · 2h", Brush.linearGradient(listOf(Color(0xFF3A2C6E), Color(0xFF0E1430)))),
    Article("The Grid", "technology", "On-device assistants are quietly getting a lot more capable", "6 min read · 4h", Brush.linearGradient(listOf(Color(0xFF1B3A6B), Color(0xFF2F7D8C)))),
    Article("Field Notes", "food", "A simple weeknight dal that comes together in twenty minutes", "3 min read · 6h", Brush.linearGradient(listOf(Color(0xFF7A3414), Color(0xFFD0892B)))),
    Article("Open Court", "sport", "Underdogs stun the league leaders in a late comeback", "2 min read · 7h", Brush.linearGradient(listOf(Color(0xFF15512F), Color(0xFF2F9E57)))),
    Article("City Desk", "local", "The riverfront redevelopment finally breaks ground this spring", "5 min read · 9h", Brush.linearGradient(listOf(Color(0xFF23303F), Color(0xFF5A6B7B)))),
)

// (name, value, change, up) — Indian indices as a static placeholder until S29
// wires the Moneycontrol / ET markets feed.
private val STOCKS = listOf(
    StockRow("Sensex", "79,243.18", "+0.6%", true),
    StockRow("Nifty 50", "24,010.60", "+0.7%", true),
    StockRow("Nifty Bank", "51,338.4", "-0.3%", false),
)

private data class StockRow(val name: String, val value: String, val change: String, val up: Boolean)
