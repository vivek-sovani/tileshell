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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import com.tileshell.core.design.Glass
import com.tileshell.core.design.isLightBackground
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.WallpaperGradient
import com.tileshell.core.design.WallpaperLayer
import com.tileshell.core.design.wallpaperBackground
import com.tileshell.core.design.Wallpapers
import com.tileshell.feature.personalize.FeedSourceItem
import com.tileshell.feature.personalize.RegionChipGrid
import com.tileshell.feature.personalize.RegionOption
import com.tileshell.core.design.LocalColorTokens
import com.tileshell.feature.start.dominantIconColor
import com.tileshell.feature.start.rememberChosenWallpaperIsLight
import com.tileshell.feature.start.rememberWallpaperBitmap
import com.tileshell.feature.livetiles.CalendarFace
import com.tileshell.feature.livetiles.FeedArticle
import com.tileshell.feature.livetiles.FeedData
import com.tileshell.feature.livetiles.FeedRefreshWorker
import com.tileshell.feature.livetiles.FeedStore
import com.tileshell.feature.livetiles.INDIA_COUNTRY_CODE
import com.tileshell.feature.livetiles.INTERNATIONAL_REGION_CODE
import com.tileshell.feature.livetiles.SELECTABLE_COUNTRIES
import com.tileshell.feature.livetiles.regionDisplayName
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
 * @param onOpenQuickSearch opens the same apps/contacts/web-engines/ask-ai overlay the
 *   two-finger swipe gesture does — the search pill itself is just an entry point into it.
 * @param onWeatherDetails opens fuller weather for the given place query.
 * @param onAddSchedule opens the calendar app's add-event screen.
 * @param onOpenArticle opens a tapped article's link in the browser.
 * @param onRefresh forces a manual news refresh.
 * @param active whether the feed is the foreground page — drives a light media
 *   poll so now-playing (art + play state) stays fresh here, since this surface
 *   sits outside the live-tile gate and per-app media callbacks are unreliable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedPage(
    accent: Color,
    statusBarTopPx: Float,
    userName: String,
    wallpaper: WallpaperGradient,
    customWallpaperUri: String?,
    dark: Boolean,
    noWallpaper: Boolean,
    feedNoBackground: Boolean,
    feeds: List<FeedSourceItem>,
    onToggleFeed: (url: String, enabled: Boolean) -> Unit,
    onToggleCategory: (category: String, enabled: Boolean) -> Unit,
    onRemoveFeed: (url: String) -> Unit,
    onAddFeed: (url: String, name: String) -> Unit,
    feedRegions: Set<String>,
    onFeedRegionToggle: (region: String, enabled: Boolean) -> Unit,
    onOpenQuickSearch: () -> Unit,
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

    // The glance screen never shows the literal wallpaper image — always a
    // synthesized abstract colour gradient built from its prominent colours
    // (a stock gradient's own vivid glow tints, or a custom photo's palette
    // via androidx.palette), rendered crisp — never blurred. feedAccent (the
    // most prominent extracted colour) drives the cards/chrome below; the
    // feed settings sheet (a modal, like Personalize) intentionally keeps the
    // global accent instead.
    //
    // "no background" is a real, supported choice, and independent of
    // Start's own wallpaper: either Start has no wallpaper set at all
    // (`noWallpaper`), or the user has explicitly opted the feed out via
    // `feedNoBackground` even though Start itself shows a photo/gradient —
    // the feed is a denser reading surface and a colourful background behind
    // it isn't always wanted even when it looks fine behind Start's tiles.
    // The background box below renders flat `tokens.bg` either way; without
    // this branch feedAccent would still resolve to whatever stock
    // gradient's own glow colour `wallpaper` falls back to (e.g. Aurora's
    // teal) — a stray tint on the weather/today cards and chips even though
    // the screen reads as "no background". Skip palette synthesis entirely
    // and use the plain global accent instead.
    val flatBackground = noWallpaper || feedNoBackground
    val feedCustomPhoto = customWallpaperUri?.let { rememberWallpaperBitmap(it) }
    val (feedGradient, feedAccent) = if (flatBackground) {
        wallpaper to accent
    } else {
        rememberFeedPalette(feedCustomPhoto, wallpaper, accent)
    }

    // Text sitting directly on the background (greeting, date/clock, section
    // labels) can't use the fixed theme fg/fgDim — those are white in dark
    // theme regardless of what's actually behind them, and a light gradient
    // would render them unreadably pale. Reuses the same brightness
    // classification Start already applies to glass/tiled tile faces (see
    // docs/DECISIONS.md "Live tile text: black when the wallpaper behind it
    // is light"), evaluated against feedGradient — what's actually drawn —
    // rather than the raw photo. Text inside the page's own opaque cards
    // (AccentCard/GCard/ArticleCard) is unaffected — those already guarantee
    // their own contrast.
    val feedBackgroundIsLight = rememberChosenWallpaperIsLight(
        customPhoto = null,
        noWallpaper = flatBackground,
        wallpaper = feedGradient,
        dark = dark,
        screenBg = tokens.bg,
    )
    val feedFg = Glass.faceTextColor(feedBackgroundIsLight)
    val feedFgDim = feedFg.copy(alpha = 0.62f)

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
    val clock = feedGlanceClock(now)
    val topPad = with(density) { statusBarTopPx.toDp() } + 8.dp

    var feedSettingsOpen by rememberSaveable { mutableStateOf(false) }

    // Single global edit-mode toggle for the glance section (weather/agenda/
    // now-playing + hosted widgets — see WidgetSection's "edit"/"done" header
    // action), same shape as Quick Panel's own toggle: local UI state, reset when
    // the page stops being the active pager page rather than surviving in the
    // background indefinitely.
    var glanceEditMode by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) glanceEditMode = false }

    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
    if (flatBackground) {
        Box(modifier = Modifier.fillMaxSize().background(tokens.bg))
    } else {
        Box(modifier = Modifier.fillMaxSize().wallpaperBackground(feedGradient, dark))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 14.dp, top = topPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GreetingHeader(userName = userName, hour = now.get(Calendar.HOUR_OF_DAY), fg = feedFg, fgDim = feedFgDim)
        GlanceRow(glance = glance, clock = clock, fg = feedFg, fgDim = feedFgDim)
        SearchPill(accent = accent, tokens = tokens, onOpenQuickSearch = onOpenQuickSearch)

        // Everything below scrolls as one continuous feed — no more glance/news
        // tab switch.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WidgetSection(
                accent = feedAccent,
                tokens = tokens,
                labelColor = feedFgDim,
                weatherSnapshot = snapshot,
                onWeatherClick = { onWeatherDetails(("weather " + (snapshot?.place ?: "")).trim()) },
                agenda = agenda,
                calendarGranted = calGranted,
                onAddSchedule = onAddSchedule,
                onAgendaClick = { openCalendar(context) },
                nowPlaying = nowPlaying,
                nowPlayingPackage = nowPlayingPackage,
                nowPlayingArt = nowPlayingPackage?.let { artwork[it] },
                onNowPlayingClick = nowPlayingPackage?.let { pkg -> { launchPackage(context, pkg) } },
                editMode = glanceEditMode,
                onEditModeChange = { glanceEditMode = it },
            )

            NewsHeader(
                accent = feedAccent,
                fg = feedFg,
                fgDim = feedFgDim,
                onRefresh = onRefresh,
                onSettings = { feedSettingsOpen = true },
            )
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
                    ArticleCard(article, nowMs, feedAccent, tokens) { onOpenArticle(article.link) }
                }
            }
        }
    }

    // Feed settings sheet — slides up over the page when the ⚙ icon is tapped.
    FeedSettingsSheet(
        visible = feedSettingsOpen,
        accent = accent,
        tokens = tokens,
        feeds = feeds,
        onToggleFeed = onToggleFeed,
        onToggleCategory = onToggleCategory,
        onRemoveFeed = onRemoveFeed,
        onAddFeed = onAddFeed,
        feedRegions = feedRegions,
        onFeedRegionToggle = onFeedRegionToggle,
        onDismiss = { feedSettingsOpen = false },
    )
    }  // Box
}

/**
 * The feed's own ambient colour identity: a synthesized [WallpaperGradient]
 * (never the actual photo) plus its most prominent colour as [feedAccent].
 * For a stock gradient this is just the gradient itself (already an abstract
 * colour mesh); for a custom photo, [photoGradient] extracts a palette via
 * androidx.palette off the main thread. Recomputes only when the underlying
 * photo/gradient actually changes; briefly shows the previous result (or the
 * plain stock gradient) while a new photo's palette is extracting.
 */
@Composable
internal fun rememberFeedPalette(
    customPhoto: ImageBitmap?,
    wallpaper: WallpaperGradient,
    fallbackAccent: Color,
): Pair<WallpaperGradient, Color> {
    val stockResult = wallpaper to (wallpaper.layers.firstOrNull()?.color ?: fallbackAccent)
    var result by remember(wallpaper, fallbackAccent) { mutableStateOf(stockResult) }
    LaunchedEffect(customPhoto, wallpaper, fallbackAccent) {
        result = if (customPhoto != null) {
            withContext(Dispatchers.Default) { photoGradient(customPhoto) } ?: stockResult
        } else {
            stockResult
        }
    }
    return result
}

/**
 * Extracts up to 3 prominent colours from [photo] via [Palette] and lays them
 * out as radial glow layers over a darker base — the same [WallpaperGradient]
 * shape the bundled gradients use (see [Wallpapers]) — so a custom photo's
 * *palette* drives the feed's background instead of the (blurred) photo
 * itself. Null when the photo yields no usable colour (e.g. fully transparent).
 */
private fun photoGradient(photo: ImageBitmap): Pair<WallpaperGradient, Color>? {
    val palette = runCatching { Palette.from(photo.asAndroidBitmap()).generate() }.getOrNull()
    val swatches = listOfNotNull(
        palette?.vibrantSwatch,
        palette?.lightVibrantSwatch,
        palette?.darkVibrantSwatch,
        palette?.mutedSwatch,
        palette?.lightMutedSwatch,
        palette?.darkMutedSwatch,
        palette?.dominantSwatch,
    ).distinctBy { it.rgb }

    val accent: Color
    val base: Color
    val layerColors: List<Color>
    if (swatches.isNotEmpty()) {
        accent = Color(swatches.first().rgb)
        base = (palette?.darkVibrantSwatch ?: palette?.darkMutedSwatch)?.let { Color(it.rgb) }
            ?: darken(accent, 0.35f)
        layerColors = swatches.map { Color(it.rgb) }.filter { it != base }.take(3).ifEmpty { listOf(accent) }
    } else {
        // Palette's target-based swatches (vibrant/muted/etc.) can all come back
        // null for a near-flat/low-variance photo (nothing for its target
        // criteria to select between) even when generate() itself succeeds —
        // fall back to a plain average colour instead of silently reverting to
        // an unrelated stock gradient.
        val avg = dominantIconColor(photo) ?: return null
        accent = avg
        base = darken(avg, 0.45f)
        layerColors = listOf(lighten(avg, 0.12f), avg, darken(avg, 0.15f))
    }

    // Same 3-corner layout the bundled gradients (e.g. Aurora) use.
    val positions = listOf(
        Triple(0.15f, 0.10f, 1.2f),
        Triple(0.85f, 0.00f, 1.2f),
        Triple(0.70f, 1.00f, 1.4f),
    )
    val layers = layerColors.mapIndexed { i, color ->
        val (cx, cy, radius) = positions.getOrElse(i) { Triple(0.5f, 0.5f, 1.3f) }
        WallpaperLayer(color = color, cx = cx, cy = cy, radiusPct = radius, fade = 0.55f)
    }
    return WallpaperGradient(id = "photo", label = "photo", base = base, layers = layers) to accent
}

/** Darkens [color] toward black by [factor] (0..1); alpha untouched. */
private fun darken(color: Color, factor: Float): Color = Color(
    red = color.red * (1f - factor),
    green = color.green * (1f - factor),
    blue = color.blue * (1f - factor),
    alpha = color.alpha,
)

/** Lightens [color] toward white by [factor] (0..1); alpha untouched. */
private fun lighten(color: Color, factor: Float): Color = Color(
    red = color.red + (1f - color.red) * factor,
    green = color.green + (1f - color.green) * factor,
    blue = color.blue + (1f - color.blue) * factor,
    alpha = color.alpha,
)

/**
 * "good morning, `<name>`" — the time-of-day bucket from [greetingFor], with the
 * name (if any) rendered in an emphasized italic. No comma/name at all when
 * [userName] is blank, matching the launcher's existing graceful-degrade
 * convention (weather/calendar do the same when a permission is denied).
 */
@Composable
private fun GreetingHeader(userName: String, hour: Int, fg: Color, fgDim: Color) {
    val greeting = greetingFor(hour)
    // A deliberate one-off display treatment (serif, unlike the app's own
    // Outfit/Nunito tile typography) to match the mockup's hero greeting —
    // FontFamily.Serif is a built-in generic family, no new font assets needed.
    // [fg]/[fgDim] adapt to the actual wallpaper's brightness (not the fixed
    // theme colour) since this sits directly on it — see the brightness
    // classification computed once in FeedPage.
    Column(modifier = Modifier.padding(horizontal = 6.dp)) {
        Text(
            text = if (userName.isBlank()) greeting else "$greeting,",
            color = fg,
            fontSize = 28.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
        )
        if (userName.isNotBlank()) {
            Text(
                text = userName,
                color = fgDim,
                fontSize = 34.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

/** The inline "news" section header: label + refresh action + settings gear. */
@Composable
private fun NewsHeader(
    accent: Color,
    fg: Color,
    fgDim: Color,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "news",
            color = fgDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 6.dp).weight(1f),
        )
        Text(
            "refresh",
            color = accent,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onRefresh)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
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
                tint = fgDim,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/* ---------------------------------------------------------------- cards ---- */

/**
 * Not a real text field — tapping anywhere on the pill opens [QuickSearchOverlay]
 * (apps/contacts/web-engines/ask-ai), which owns its own input box. Kept as a
 * plain clickable row rather than an inline field so the whole pill is a single
 * unambiguous tap target with no fall-through to whatever page sits underneath.
 */
@Composable
private fun SearchPill(
    accent: Color,
    tokens: com.tileshell.core.design.ColorTokens,
    onOpenQuickSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.sheet)
            .clickable(onClick = onOpenQuickSearch)
            .padding(start = 16.dp, end = 16.dp),
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
        Text("search apps, contacts, web", color = tokens.fgDim, fontSize = 16.sp)
    }
}

@Composable
private fun GlanceRow(glance: GlanceDate, clock: String, fg: Color, fgDim: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(glance.dateLine, color = fgDim, fontSize = 13.sp, letterSpacing = 0.5.sp)
        Text(clock, color = fg, fontSize = 22.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(text, color = color, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

/**
 * A section label with a trailing text action (e.g. the widgets header's
 * "+ add"). The leading plus glyph shows only when [showPlus] is set. Takes an
 * explicit [labelColor] rather than a [com.tileshell.core.design.ColorTokens]
 * since its caller (the widgets section) sits directly on the feed's
 * wallpaper, so the label needs to adapt to the wallpaper's brightness, not
 * the fixed theme colour.
 */
@Composable
internal fun SectionHeader(
    text: String,
    actionText: String,
    accent: Color,
    labelColor: Color,
    showPlus: Boolean = false,
    onAction: () -> Unit,
    /** A second, plain text action rendered before [actionText] — used for the
     *  glance section's "edit"/"done" edit-mode toggle. */
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, color = labelColor, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (secondaryActionText != null && onSecondaryAction != null) {
                Text(
                    secondaryActionText,
                    color = accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onSecondaryAction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
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

/** Accent-filled card (weather/today/now-playing) — the WP live-tile colour block. */
@Composable
private fun AccentCard(
    accent: Color,
    onClick: (() -> Unit)? = null,
    /** Lets a caller stretch the card to fill a taller container (e.g. a
     *  resized built-in glance card, see `WidgetSlot.kt`'s `BuiltinCardView`) —
     *  the accent fill then covers the whole resized area instead of leaving
     *  blank space below a natural-height card. */
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(accent),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Condensed for the half-width weather+today row (FR-7 glance restyle): icon +
 * big temp + condition + one "h/l" line — no room at half width for the
 * separate now/high/low stat trio the full-width version used to show.
 */
@Composable
internal fun WeatherCard(
    snapshot: com.tileshell.feature.livetiles.WeatherSnapshot?,
    accent: Color,
    onClick: () -> Unit,
) {
    // Card text adapts to this card's own accent fill (not the page background —
    // a wallpaper-derived accent can be light even when the page itself is dark).
    val onAccent = Glass.faceTextColor(useDarkText = isLightBackground(accent))
    val onAccentDim = onAccent.copy(alpha = 0.78f)
    AccentCard(accent, onClick = onClick, modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (snapshot == null) {
                Text("weather unavailable", color = onAccent, fontSize = 14.sp)
                Text(
                    "set a location or allow location access",
                    color = onAccentDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                return@Column
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text("${snapshot.tempC}°", color = onAccent, fontSize = 34.sp, fontWeight = FontWeight.Thin)
                Icon(
                    imageVector = TileIcons["weather"],
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                snapshot.condition,
                color = onAccentDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "h ${snapshot.highC}° · l ${snapshot.lowC}°",
                color = onAccentDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Condensed for the half-width weather+today row: the "today" caption + add
 * action now live inside the card itself (mirrors the mockup) instead of a
 * separate [SectionHeader] above it.
 */
@Composable
internal fun AgendaCard(
    agenda: CalendarFace,
    granted: Boolean,
    accent: Color,
    onAddSchedule: () -> Unit,
    onClick: () -> Unit,
) {
    val onAccent = Glass.faceTextColor(useDarkText = isLightBackground(accent))
    val onAccentDim = onAccent.copy(alpha = 0.78f)
    AccentCard(accent, onClick = onClick, modifier = Modifier.fillMaxHeight()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("today", color = onAccentDim, fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAddSchedule,
                        )
                        .padding(4.dp),
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        val s = size.width
                        drawLine(onAccent, Offset(s / 2f, s * 0.1f), Offset(s / 2f, s * 0.9f), strokeWidth = s * 0.14f, cap = StrokeCap.Round)
                        drawLine(onAccent, Offset(s * 0.1f, s / 2f), Offset(s * 0.9f, s / 2f), strokeWidth = s * 0.14f, cap = StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val events = listOfNotNull(agenda.next, agenda.following)
            when {
                !granted -> Text("allow calendar to see your day", color = onAccentDim, fontSize = 12.sp)
                events.isEmpty() -> Text("nothing on your calendar today", color = onAccentDim, fontSize = 12.sp)
                else -> events.forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(onAccent),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(e.title.ifEmpty { "(busy)" }, color = onAccent, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(e.timeLine, color = onAccentDim, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NowPlayingCard(
    nowPlaying: NowPlaying,
    packageName: String?,
    art: android.graphics.Bitmap?,
    accent: Color,
    onClick: (() -> Unit)? = null,
) {
    val onAccent = Glass.faceTextColor(useDarkText = isLightBackground(accent))
    val onAccentDim = onAccent.copy(alpha = 0.78f)
    AccentCard(accent, onClick = onClick, modifier = Modifier.fillMaxHeight()) {
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
                    color = onAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nowPlaying.artist.isNotBlank()) {
                    Text(
                        nowPlaying.artist,
                        color = onAccentDim,
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
                tint = onAccent,
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
    feeds: List<FeedSourceItem>,
    onToggleFeed: (url: String, enabled: Boolean) -> Unit,
    onToggleCategory: (category: String, enabled: Boolean) -> Unit,
    onRemoveFeed: (url: String) -> Unit,
    onAddFeed: (url: String, name: String) -> Unit,
    feedRegions: Set<String>,
    onFeedRegionToggle: (region: String, enabled: Boolean) -> Unit,
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

            // News region: swaps the whole subscribed-feed list for a curated preset
            // (India vs a generic international set) — an explicit override of the
            // locale-detected default seeded on first run (StartViewModel.init).
            FeedSheetGroup(label = "news regions (select any number)", labelColor = tokens.fgDim) {
                RegionChipGrid(
                    regions = (listOf(INDIA_COUNTRY_CODE, INTERNATIONAL_REGION_CODE) + SELECTABLE_COUNTRIES.map { it.code })
                        .map { code -> RegionOption(code, regionDisplayName(code), code in feedRegions) },
                    accent = accent,
                    tokens = tokens,
                    onToggle = onFeedRegionToggle,
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
