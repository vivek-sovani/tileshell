package com.tileshell.feature.start.feed

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tileshell.core.design.TileIcons
import com.tileshell.feature.personalize.FeedSourceItem
import com.tileshell.core.design.LocalColorTokens
import com.tileshell.feature.livetiles.CalendarFace
import com.tileshell.feature.livetiles.FeedArticle
import com.tileshell.feature.livetiles.FeedData
import com.tileshell.feature.livetiles.FeedRefreshWorker
import com.tileshell.feature.livetiles.FeedStore
import com.tileshell.feature.livetiles.feedAgo
import com.tileshell.feature.livetiles.MediaCenter
import com.tileshell.feature.livetiles.MediaTransportControls
import com.tileshell.feature.livetiles.NowPlaying
import com.tileshell.feature.livetiles.refreshMediaSessions
import com.tileshell.feature.livetiles.WeatherCache
import com.tileshell.feature.livetiles.WeatherCacheData
import com.tileshell.feature.livetiles.queryUpcomingEvents
import com.tileshell.feature.livetiles.rememberPermissionGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * The left "feed" page (the 3rd pager page, reached by swiping right from Start).
 * An independent info screen: search pill → glance row (date + live clock) →
 * weather → today's schedule → now-playing. The live cards reuse existing
 * sources — [WeatherCache], [MediaCenter] and the calendar query — so this page
 * adds no new data plumbing. News / market data arrive with the RSS engine (S29).
 *
 * @param onSearch fires the user's typed query at Google (the host owns the intent).
 * @param onWeatherDetails opens fuller weather for the given place query.
 * @param onAddSchedule opens the calendar app's add-event screen.
 * @param onOpenArticle opens a tapped article's link in the browser.
 * @param onRefresh forces a manual news refresh.
 * @param active whether the feed is the foreground page — drives a light media
 *   poll so now-playing (art + play state) stays fresh here, since this surface
 *   sits outside the live-tile gate and per-app media callbacks are unreliable.
 */
@Composable
fun FeedPage(
    accent: Color,
    statusBarTopPx: Float,
    feedEnabled: Boolean,
    onFeedEnabledChange: (Boolean) -> Unit,
    feeds: List<FeedSourceItem>,
    onToggleFeed: (url: String, enabled: Boolean) -> Unit,
    onToggleCategory: (category: String, enabled: Boolean) -> Unit,
    onRemoveFeed: (url: String) -> Unit,
    onAddFeed: (url: String, name: String) -> Unit,
    onSearch: (String) -> Unit,
    onWeatherDetails: (String) -> Unit,
    onAddSchedule: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onRefresh: () -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalColorTokens.current
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Weather (FR-2): the cached snapshot the weather tile already maintains.
    val weatherCache = remember(context) { WeatherCache.create(context) }
    val weather by weatherCache.data.collectAsStateWithLifecycle(initialValue = WeatherCacheData())
    val snapshot = weather.snapshot

    // Now-playing (reuse MediaCenter): prefer a playing session, else any. Keep the
    // package key so the card's transport controls drive the right session.
    val media by MediaCenter.nowPlaying.collectAsStateWithLifecycle()
    val artwork by MediaCenter.artwork.collectAsStateWithLifecycle()
    val mediaEntry = media.entries.firstOrNull { it.value.playing } ?: media.entries.firstOrNull()
    val nowPlaying: NowPlaying? = mediaEntry?.value
    val nowPlayingPackage: String? = mediaEntry?.key
    // Poll media while the feed is foreground so play/pause + artwork stay current
    // (the Start poll is gated off here; player callbacks aren't always reliable).
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            refreshMediaSessions(context)
            delay(2_500L)
        }
    }

    // News (discover): live articles fetched by FeedRefreshWorker from the user's
    // subscribed RSS feeds. Schedule the worker while the feed page is composed.
    val feedStore = remember(context) { FeedStore.create(context) }
    val feedData by feedStore.data.collectAsStateWithLifecycle(initialValue = FeedData())
    LaunchedEffect(Unit) { FeedRefreshWorker.ensureScheduled(context) }

    // Today's agenda (reuse the calendar query); empty until READ_CALENDAR is granted.
    val calGranted = rememberPermissionGranted(Manifest.permission.READ_CALENDAR)
    var agenda by remember { mutableStateOf(CalendarFace(null, null)) }
    LaunchedEffect(calGranted) {
        agenda = if (calGranted) withContext(Dispatchers.IO) { queryUpcomingEvents(context, windowHours = 24) }
        else CalendarFace(null, null)
    }

    // Live clock + date, re-read on each minute boundary (cheap; only while the
    // page is composed, i.e. the feed is enabled).
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            val secondsToMinute = 60 - now.get(Calendar.SECOND)
            delay(secondsToMinute.coerceAtLeast(1) * 1000L)
        }
    }
    val glance = feedGlanceDate(now)
    val clock = feedClock12(now)
    val topPad = with(density) { statusBarTopPx.toDp() } + 8.dp

    var tab by rememberSaveable { mutableStateOf(FeedTab.GLANCE) }
    var feedSettingsOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.bg),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 14.dp, top = topPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchPill(accent = accent, tokens = tokens, onSearch = onSearch)
        GlanceRow(glance = glance, clock = clock, tokens = tokens)
        FeedTabs(
            selected = tab,
            accent = accent,
            tokens = tokens,
            onSelect = { tab = it },
            onSettings = { feedSettingsOpen = true },
        )

        // Tab content scrolls independently below the persistent header.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                FeedTab.GLANCE -> {
                    SectionLabel("weather", tokens.fgDim)
                    WeatherCard(
                        snapshot = snapshot,
                        accent = accent,
                        onClick = { onWeatherDetails(("weather " + (snapshot?.place ?: "")).trim()) },
                    )

                    SectionHeader("today", actionText = "add", accent = accent, tokens = tokens, showPlus = true, onAction = onAddSchedule)
                    AgendaCard(
                        agenda = agenda,
                        granted = calGranted,
                        accent = accent,
                        onClick = { openCalendar(context) },
                    )

                    if (nowPlaying != null) {
                        NowPlayingCard(
                            nowPlaying = nowPlaying,
                            packageName = nowPlayingPackage,
                            art = nowPlayingPackage?.let { artwork[it] },
                            accent = accent,
                            onClick = nowPlayingPackage?.let { pkg -> { launchPackage(context, pkg) } },
                        )
                    }

                    SectionLabel("widgets", tokens.fgDim)
                    WidgetSection(accent = accent, tokens = tokens)
                }

                FeedTab.NEWS -> {
                    SectionHeader("discover", actionText = "refresh", accent = accent, tokens = tokens, onAction = onRefresh)
                    val articles = feedData.articles
                    if (articles.isEmpty()) {
                        GCard(tokens) {
                            Text(
                                "no articles yet — add news feeds via feed settings ⚙",
                                color = tokens.fgDim,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        val nowMs = now.timeInMillis
                        articles.forEach { article ->
                            ArticleCard(article, nowMs, accent, tokens) { onOpenArticle(article.link) }
                        }
                    }
                }
            }
        }
    }

    // Feed settings sheet — slides up over the page when the ⚙ icon is tapped.
    FeedSettingsSheet(
        visible = feedSettingsOpen,
        accent = accent,
        tokens = tokens,
        feedEnabled = feedEnabled,
        onFeedEnabledChange = onFeedEnabledChange,
        feeds = feeds,
        onToggleFeed = onToggleFeed,
        onToggleCategory = onToggleCategory,
        onRemoveFeed = onRemoveFeed,
        onAddFeed = onAddFeed,
        onDismiss = { feedSettingsOpen = false },
    )
    }  // Box
}

private enum class FeedTab { GLANCE, NEWS }

/** Two-segment tab selector (glance | news) with a trailing settings icon. */
@Composable
private fun FeedTabs(
    selected: FeedTab,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onSelect: (FeedTab) -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedTab.values().forEach { t ->
            val on = t == selected
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(t) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (t == FeedTab.GLANCE) "glance" else "news",
                    color = if (on) accent else tokens.fgDim,
                    fontSize = 16.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (on) 22.dp else 0.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (on) accent else Color.Transparent),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // Settings gear — opens FeedSettingsSheet.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSettings,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TileIcons["settings"],
                contentDescription = "feed settings",
                tint = tokens.fgDim,
                modifier = Modifier.size(20.dp),
            )
        }
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
private fun GlanceRow(glance: GlanceDate, clock: String, tokens: com.tileshell.core.design.ColorTokens) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(glance.weekday, color = tokens.fg, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Text(glance.sub, color = tokens.fgDim, fontSize = 13.sp)
        }
        Text(clock, color = tokens.fg, fontSize = 22.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(text, color = color, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

/**
 * A section label with a trailing text action (e.g. "+ add" on the today header,
 * "refresh" on the discover header). The leading plus glyph shows only when
 * [showPlus] is set.
 */
@Composable
private fun SectionHeader(
    text: String,
    actionText: String,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    showPlus: Boolean = false,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showPlus) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    val s = size.width
                    drawLine(accent, Offset(s / 2f, s * 0.1f), Offset(s / 2f, s * 0.9f), strokeWidth = s * 0.12f, cap = StrokeCap.Round)
                    drawLine(accent, Offset(s * 0.1f, s / 2f), Offset(s * 0.9f, s / 2f), strokeWidth = s * 0.12f, cap = StrokeCap.Round)
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(actionText, color = accent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun GCard(
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(tokens.sheet),
    ) { content() }
}

// White text on the accent-filled "your data" cards (WP live-tile look).
private val OnAccent = Color.White
private val OnAccentDim = Color.White.copy(alpha = 0.78f)

/** Accent-filled card (weather/today/now-playing) — the WP live-tile colour block. */
@Composable
private fun AccentCard(
    accent: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(accent),
    ) { content() }
}

@Composable
private fun WeatherCard(
    snapshot: com.tileshell.feature.livetiles.WeatherSnapshot?,
    accent: Color,
    onClick: () -> Unit,
) {
    AccentCard(accent, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (snapshot == null) {
                Text("weather unavailable", color = OnAccent, fontSize = 16.sp)
                Text(
                    "set a location or allow location access",
                    color = OnAccentDim,
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
                    Text(snapshot.place.ifEmpty { "your location" }, color = OnAccent, fontSize = 18.sp)
                    Text(snapshot.condition, color = OnAccentDim, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Text("${snapshot.tempC}°", color = OnAccent, fontSize = 42.sp, fontWeight = FontWeight.Thin)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatCol("now", "${snapshot.tempC}°")
                StatCol("high", "${snapshot.highC}°")
                StatCol("low", "${snapshot.lowC}°")
            }
            if (snapshot.detail.isNotEmpty()) {
                Text(snapshot.detail, color = OnAccentDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun StatCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = OnAccentDim, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = OnAccent, fontSize = 14.sp)
    }
}

@Composable
private fun AgendaCard(
    agenda: CalendarFace,
    granted: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    AccentCard(accent, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            val events = listOfNotNull(agenda.next, agenda.following)
            when {
                !granted -> Text("allow calendar to see your day", color = OnAccentDim, fontSize = 14.sp)
                events.isEmpty() -> Text("nothing on your calendar today", color = OnAccentDim, fontSize = 14.sp)
                else -> events.forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(OnAccent),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(e.title.ifEmpty { "(busy)" }, color = OnAccent, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(e.timeLine, color = OnAccentDim, fontSize = 11.sp)
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
    packageName: String?,
    art: android.graphics.Bitmap?,
    accent: Color,
    onClick: (() -> Unit)? = null,
) {
    AccentCard(accent, onClick = onClick) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    androidx.compose.foundation.Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val p = Path().apply {
                            moveTo(0f, 0f); lineTo(0f, size.height); lineTo(size.width, size.height / 2f); close()
                        }
                        drawPath(p, Color.White)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    nowPlaying.title.ifBlank { "now playing" },
                    color = OnAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nowPlaying.artist.isNotBlank()) {
                    Text(
                        nowPlaying.artist,
                        color = OnAccentDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            MediaTransportControls(
                playing = nowPlaying.playing,
                packageName = packageName,
                tint = OnAccent,
            )
        }
    }
}

@Composable
private fun ArticleCard(
    article: FeedArticle,
    nowMs: Long,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: () -> Unit,
) {
    val image = rememberRemoteImage(article.imageUrl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.sheet)
            .clickable(onClick = onClick),
    ) {
        Column {
            if (article.imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(accent.copy(alpha = 0.25f)),
                ) {
                    if (image != null) {
                        androidx.compose.foundation.Image(
                            bitmap = image,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
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
            }
            Column(modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 13.dp, bottom = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        article.source,
                        color = tokens.fgDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    article.title,
                    color = tokens.fg,
                    fontSize = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp, bottom = 10.dp),
                )
                Text(
                    if (article.imageUrl == null) "${article.tag} · ${feedAgo(article.publishedAtMillis, nowMs)}"
                    else feedAgo(article.publishedAtMillis, nowMs),
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Feed settings sheet
// ---------------------------------------------------------------------------

private val FEED_CATEGORY_LABELS = linkedMapOf(
    "nation" to "national news",
    "state" to "local news",
    "entertainment" to "entertainment",
    "cricket" to "cricket",
    "sports" to "sports",
    "tech" to "technology",
    "business" to "business",
    "food" to "food",
)

/**
 * Slide-up bottom sheet with news/feed settings: toggle the feed page on/off and
 * manage subscribed RSS categories + custom feed URLs. Mirrors the PersonalizeSheet
 * animation (300 ms cubic-bezier) and visual language (grip, lowercase title, token
 * colours) so the two sheets feel like the same system.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedSettingsSheet(
    visible: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    feedEnabled: Boolean,
    onFeedEnabledChange: (Boolean) -> Unit,
    feeds: List<FeedSourceItem>,
    onToggleFeed: (url: String, enabled: Boolean) -> Unit,
    onToggleCategory: (category: String, enabled: Boolean) -> Unit,
    onRemoveFeed: (url: String) -> Unit,
    onAddFeed: (url: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "feedSheetProgress",
    )
    if (!visible && progress == 0f) return

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.sheet)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            // Grip.
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.fgDim.copy(alpha = 0.5f)),
            )
            Text(
                text = "news settings",
                color = tokens.fg,
                fontSize = 30.sp,
                fontWeight = FontWeight.W200,
                letterSpacing = (-1).sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
            )

            // Toggle: show feed page.
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                FeedSettingToggle(
                    label = "show feed page",
                    on = feedEnabled,
                    accent = accent,
                    tokens = tokens,
                    onChange = onFeedEnabledChange,
                )
            }

            // Feed sources section.
            FeedSheetGroup(label = "news feeds", labelColor = tokens.fgDim) {
                FeedsManager(
                    feeds = feeds,
                    accent = accent,
                    tokens = tokens,
                    onToggleFeed = onToggleFeed,
                    onToggleCategory = onToggleCategory,
                    onRemove = onRemoveFeed,
                    onAdd = onAddFeed,
                )
            }
        }
    }
}

/** A section group in the feed settings sheet. */
@Composable
private fun FeedSheetGroup(
    label: String,
    labelColor: Color,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp)) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

/** A simple pill-toggle row for use inside the feed settings sheet. */
@Composable
private fun FeedSettingToggle(
    label: String,
    on: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = tokens.fg, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (on) accent else tokens.tileLine),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** Pill toggle used for individual feed items within categories. */
@Composable
private fun FeedItemPill(
    on: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) accent else tokens.tileLine)
            .clickable(onClick = onClick),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Selectable chip for per-feed source selection. */
@Composable
private fun FeedSourceChip(
    label: String,
    on: Boolean,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (on) Modifier.background(accent)
                else Modifier.border(1.dp, tokens.tileLine, RoundedCornerShape(16.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (on) Color.White else tokens.fgDim, fontSize = 13.sp)
    }
}

/**
 * Category-grouped feed management: toggle categories and individual sources,
 * manage custom URLs. Identical in function to the version previously hosted in
 * PersonalizeSheet; now lives here alongside the news content it configures.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedsManager(
    feeds: List<FeedSourceItem>,
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onToggleFeed: (String, Boolean) -> Unit,
    onToggleCategory: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FEED_CATEGORY_LABELS.forEach { (category, label) ->
            val inCategory = feeds.filter { it.category == category }
            if (inCategory.isEmpty()) return@forEach
            val anyOn = inCategory.any { it.enabled }
            FeedSettingToggle(label = label, on = anyOn, accent = accent, tokens = tokens) {
                onToggleCategory(category, it)
            }
            if (anyOn) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    inCategory.forEach { feed ->
                        FeedSourceChip(feed.name, feed.enabled, accent, tokens) {
                            onToggleFeed(feed.url, !feed.enabled)
                        }
                    }
                }
            }
        }

        val customFeeds = feeds.filter { it.category !in FEED_CATEGORY_LABELS }
        if (customFeeds.isNotEmpty()) {
            Text("custom feeds", color = tokens.fgDim, fontSize = 12.sp)
            customFeeds.forEach { feed ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        feed.name,
                        color = if (feed.enabled) tokens.fg else tokens.fgDim,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "remove",
                        color = tokens.fgDim,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRemove(feed.url) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    FeedItemPill(on = feed.enabled, accent = accent, tokens = tokens) {
                        onToggleFeed(feed.url, !feed.enabled)
                    }
                }
            }
        }

        var url by remember { mutableStateOf("") }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, tokens.tileLine, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.fg, fontSize = 14.sp),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (url.isNotBlank()) { onAdd(url, ""); url = "" }
                    }),
                    decorationBox = { inner ->
                        if (url.isEmpty()) Text("add feed url", color = tokens.fgDim, fontSize = 14.sp)
                        inner()
                    },
                )
            }
            Text(
                "add",
                color = accent,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (url.isNotBlank()) { onAdd(url, ""); url = "" } }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Opens the default calendar app. Tries the calendar content provider VIEW intent
 * (the standard way to land in the device's calendar at the current time), then
 * falls back to the add-event INSERT intent. Best-effort — toasts if no calendar
 * app handles either.
 */
private fun openCalendar(context: android.content.Context) {
    val view = Intent(Intent.ACTION_VIEW)
        .setData(Uri.parse("content://com.android.calendar/time"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(view) }.isSuccess) return
    val insert = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(insert) }.isSuccess) return
    Toast.makeText(context, "no calendar app found", Toast.LENGTH_SHORT).show()
}

/** Launches [packageName]'s main activity via the launcher intent. Best-effort; silently no-ops on failure. */
private fun launchPackage(context: android.content.Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
    runCatching { context.startActivity(intent) }
}
