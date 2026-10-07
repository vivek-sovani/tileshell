package com.tileshell.feature.livetiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.TileSize
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.HubAppBar
import com.tileshell.core.design.HubAppBarAction
import com.tileshell.core.design.HubFilter
import com.tileshell.core.design.HubPanorama
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val NEWS_PIVOTS = listOf("top stories", "my topics", "saved", "live tv")
private const val LIVE_PAGE = 3
private val statusGate = Semaphore(4)

/**
 * The news hub: the feed's stories as a Windows Phone panorama. "top stories"
 * (a lead with its picture, then headlines), "my topics" (the same stories by
 * topic), "saved" (stories kept to read later, on this phone only) and "live
 * tv" (news channels broadcasting on YouTube for the countries followed in the
 * news settings, each opening in YouTube; the "+" picks channels).
 *
 * It reads the news the feed worker already keeps and asks for a refresh when
 * opened and on the refresh button; nothing polls. A live channel's on-air state
 * is read from YouTube's own page when the live tv page is shown and on refresh.
 */
@Composable
fun NewsHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    onPinHub: () -> Unit,
    pinMessages: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow(),
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "newsHubProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current
    val feed by remember { FeedStore.create(context).data }.collectAsState(initial = FeedData())
    val saved by NewsMarks.saved.collectAsState()
    val read by NewsMarks.read.collectAsState()
    val pagerState = rememberPagerState(pageCount = { NEWS_PIVOTS.size })

    var picking by remember { mutableStateOf(false) }
    var prefsTick by remember { mutableIntStateOf(0) }
    var statusTick by remember { mutableIntStateOf(0) }
    var liveRegion by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<LiveChannel?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenHide by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(visible) {
        if (visible) {
            NewsMarks.load(context)
            FeedRefreshWorker.refreshNow(context)
        }
    }
    BackHandler(enabled = visible) {
        when {
            fullscreenView != null -> fullscreenHide?.invoke()
            settingsOpen -> settingsOpen = false
            picking -> picking = false
            else -> onDismiss()
        }
    }

    val regions = remember(feed.regions) { feed.regions.ifEmpty { setOf(INTERNATIONAL_REGION_CODE) }.toList().sorted() }
    val regionNow = liveRegion?.takeIf { it in regions } ?: regions.first()
    val customChannels = remember(prefsTick) { NewsLivePrefs.custom(context) }
    val chosen = remember(prefsTick) { NewsLivePrefs.chosen(context) }
    val channels = remember(feed.regions, chosen, customChannels) { effectiveLiveChannels(feed.regions, chosen, customChannels) }

    val onLivePage = pagerState.currentPage == LIVE_PAGE
    // A video never keeps playing once the live tv page is left or the hub closed.
    LaunchedEffect(onLivePage, visible, picking, settingsOpen) { if (!onLivePage || !visible || picking || settingsOpen) playing = null }
    val statusCache = remember { mutableMapOf<String, LiveStatus?>() }
    val statuses by produceState(emptyMap<String, LiveStatus?>(), visible, onLivePage, channels, statusTick) {
        if (!visible || !onLivePage) return@produceState
        val wanted = channels.map { it.handle }.distinct()
        value = statusCache.filterKeys { it in wanted }
        val missing = wanted.filter { it !in statusCache }
        if (missing.isEmpty()) return@produceState
        coroutineScope {
            missing.map { h ->
                async {
                    val s = statusGate.withPermit { fetchLiveStatus(h) }
                    statusCache[h] = s
                    value = statusCache.filterKeys { it in wanted }
                }
            }.awaitAll()
        }
    }

    val nowMs = remember(feed.articles) { System.currentTimeMillis() }
    val articles = remember(feed.articles) { feed.articles.sortedByDescending { it.publishedAtMillis } }

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .statusBarsPadding()
                .navigationBarsPadding()
                // The keyboard lifts the hub so the field being typed in stays above it.
                .imePadding(),
        ) {
            if (settingsOpen) {
                NewsSettingsScreen(feed = feed, tokens = tokens, accent = accent, modifier = Modifier.weight(1f))
            } else if (picking) {
                ChannelPicker(
                    followed = regions,
                    current = channels,
                    tokens = tokens,
                    accent = accent,
                    onChanged = { prefsTick++ },
                    modifier = Modifier.weight(1f),
                )
            } else {
                HubPanorama(
                    title = "news",
                    sections = NEWS_PIVOTS,
                    pagerState = pagerState,
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        0 -> TopStoriesPage(articles, read, saved.map { it.link }.toSet(), nowMs, tokens, accent, context)
                        1 -> TopicsPage(articles, feed.sources, read, saved.map { it.link }.toSet(), nowMs, tokens, accent, context)
                        2 -> SavedPage(saved, read, nowMs, tokens, accent, context)
                        else -> LiveTvPage(
                            regions = regions,
                            region = regionNow,
                            onRegion = { liveRegion = it },
                            channels = channels.filter { it.region.equals(regionNow, ignoreCase = true) },
                            statuses = statuses,
                            tokens = tokens,
                            accent = accent,
                            onChoose = { picking = true },
                            playing = playing,
                            onPlay = { playing = it },
                            onFullscreen = { v, hide -> fullscreenView = v; fullscreenHide = hide },
                            context = context,
                        )
                    }
                }
            }
            // With the keyboard up the bar would only eat the room the field needs.
            val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val typing = keyboardUp && (picking || settingsOpen)
            if (!typing) HubPinNote(pinMessages, tokens, accent)
            if (!typing) HubAppBar(
                tokens = tokens,
                actions = buildList {
                    add(HubAppBarAction("back", "back") { if (settingsOpen) settingsOpen = false else if (picking) picking = false else onDismiss() })
                    if (!settingsOpen) add(HubAppBarAction("settings", "news settings", "settings") { picking = false; settingsOpen = true })
                    if ((onLivePage || picking) && !settingsOpen) {
                        add(HubAppBarAction("plus", "choose channels", "channels") { picking = true })
                    }
                    add(
                        HubAppBarAction("refresh", "refresh the news", "refresh") {
                            FeedRefreshWorker.refreshNow(context)
                            statusCache.clear()
                            statusTick++
                        },
                    )
                    add(HubAppBarAction("pin", "pin news to start", "pin to start", onPinHub))
                },
            )
        }
        fullscreenView?.let { v ->
            AndroidView(factory = { v }, modifier = Modifier.fillMaxSize().background(Color.Black))
        }
      }
    }
}

/** Scrolls the focused field into the room left above the keyboard (which arrives after the field is focused). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.keepAboveKeyboard(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) { if (focused && imeBottom > 0) requester.bringIntoView() }
    return this.bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}

// --- opening --------------------------------------------------------------------

private fun openLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isFailure) {
        Toast.makeText(context, "nothing can open this link", Toast.LENGTH_SHORT).show()
    }
}

private fun openArticle(context: Context, article: FeedArticle) {
    NewsMarks.markRead(context, article.link)
    openLink(context, article.link)
}

// --- story pages ----------------------------------------------------------------

@Composable
private fun EmptyNote(text: String, tokens: ColorTokens) {
    Text(text, color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun TopStoriesPage(
    articles: List<FeedArticle>,
    read: List<String>,
    saved: Set<String>,
    nowMs: Long,
    tokens: ColorTokens,
    accent: Color,
    context: Context,
) {
    val lead = remember(articles) { articles.firstOrNull { it.imageUrl != null } ?: articles.firstOrNull() }
    val rest = remember(articles, lead) { articles.filter { it !== lead }.take(40) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (lead == null) {
            item(key = "empty") { EmptyNote("no stories yet. they arrive a moment after opening, or press refresh.", tokens) }
        } else {
            item(key = "lead") { LeadStory(lead, lead.link in read, lead.link in saved, nowMs, tokens, accent, context) }
        }
        items(rest, key = { it.link }) { a -> StoryRow(a, a.link in read, a.link in saved, nowMs, tokens, accent, context) }
    }
}

@Composable
private fun TopicsPage(
    articles: List<FeedArticle>,
    sources: List<FeedSource>,
    read: List<String>,
    saved: Set<String>,
    nowMs: Long,
    tokens: ColorTokens,
    accent: Color,
    context: Context,
) {
    // Topics are the categories of the feeds the stories came from, in the settings' order.
    val byTopic = remember(articles, sources) { articles.groupBy { topicOf(it, sources) } }
    val topics = remember(byTopic) {
        TOPIC_LABELS.keys.filter { it in byTopic } + byTopic.keys.filter { it !in TOPIC_LABELS }
    }
    var choice by remember { mutableStateOf<String?>(null) }
    val topic = choice?.takeIf { it in topics } ?: topics.firstOrNull()
    val shown = remember(byTopic, topic) { byTopic[topic].orEmpty().take(50) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        if (topics.isEmpty()) {
            item(key = "empty") { EmptyNote("no stories yet. they arrive a moment after opening, or press refresh.", tokens) }
        } else {
            item(key = "topics") {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { topics.forEach { t -> HubFilter(TOPIC_LABELS[t] ?: t, topic == t, tokens, accent) { choice = t } } }
            }
        }
        items(shown, key = { it.link }) { a -> StoryRow(a, a.link in read, a.link in saved, nowMs, tokens, accent, context) }
    }
}

@Composable
private fun SavedPage(
    saved: List<FeedArticle>,
    read: List<String>,
    nowMs: Long,
    tokens: ColorTokens,
    accent: Color,
    context: Context,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        item(key = "note") {
            Text("saved stories stay on this phone. tap ★ on a story to keep it here.", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (saved.isEmpty()) item(key = "empty") { EmptyNote("nothing saved yet", tokens) }
        items(saved, key = { it.link }) { a -> StoryRow(a, a.link in read, true, nowMs, tokens, accent, context) }
    }
}

@Composable
private fun SaveMark(on: Boolean, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
    Text(
        if (on) "★" else "☆",
        color = if (on) accent else tokens.fgDim,
        fontSize = 20.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun LeadStory(a: FeedArticle, read: Boolean, saved: Boolean, nowMs: Long, tokens: ColorTokens, accent: Color, context: Context) {
    val image = rememberRemoteImage(a.imageUrl)
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { openArticle(context, a) }) {
        if (a.imageUrl != null) {
            Box(modifier = Modifier.fillMaxWidth().height(170.dp).background(accent.copy(alpha = 0.25f)).clipToBounds()) {
                if (image != null) {
                    androidx.compose.foundation.Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        Text(
            a.title,
            color = if (read) tokens.fgDim else tokens.fg,
            fontSize = 24.sp, fontWeight = FontWeight.Light, lineHeight = 29.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${a.source} · ${feedAgo(a.publishedAtMillis, nowMs)}", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            SaveMark(saved, accent, tokens) { NewsMarks.toggleSaved(context, a) }
        }
    }
}

@Composable
private fun StoryRow(a: FeedArticle, read: Boolean, saved: Boolean, nowMs: Long, tokens: ColorTokens, accent: Color, context: Context) {
    val image = rememberRemoteImage(a.imageUrl)
    Column(modifier = Modifier.fillMaxWidth().clickable { openArticle(context, a) }) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    a.title,
                    color = if (read) tokens.fgDim else tokens.fg,
                    fontSize = 16.sp, fontWeight = FontWeight.Light, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Text("${a.source} · ${feedAgo(a.publishedAtMillis, nowMs)}", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (image != null) {
                androidx.compose.foundation.Image(
                    bitmap = image, contentDescription = null,
                    modifier = Modifier.padding(start = 10.dp).size(width = 72.dp, height = 54.dp).clipToBounds(),
                    contentScale = ContentScale.Crop,
                )
            }
            SaveMark(saved, accent, tokens) { NewsMarks.toggleSaved(context, a) }
        }
    }
}

// --- live tv --------------------------------------------------------------------

private val LiveRed = Color(0xFFD6262B)

@Composable
private fun RegionChips(regions: List<String>, selected: String, tokens: ColorTokens, accent: Color, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) { regions.forEach { r -> HubFilter(regionDisplayName(r).lowercase(), r.equals(selected, true), tokens, accent) { onSelect(r) } } }
}

@Composable
private fun LiveTvPage(
    regions: List<String>,
    region: String,
    onRegion: (String) -> Unit,
    channels: List<LiveChannel>,
    statuses: Map<String, LiveStatus?>,
    tokens: ColorTokens,
    accent: Color,
    onChoose: () -> Unit,
    playing: LiveChannel?,
    onPlay: (LiveChannel?) -> Unit,
    onFullscreen: (View?, (() -> Unit)?) -> Unit,
    context: Context,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        val playingId = playing?.let { statuses[it.handle]?.videoId }
        if (playing != null && playingId != null) {
            item(key = "player") {
                LivePlayer(playing, playingId, tokens, accent, onClose = { onPlay(null) }, onFullscreen = onFullscreen, context = context)
            }
        }
        item(key = "regions") { RegionChips(regions, region, tokens, accent, onRegion) }
        item(key = "note") {
            Text(
                "the countries you follow in news. tap a live channel to watch it here.",
                color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            )
        }
        if (channels.isEmpty()) {
            item(key = "empty") {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("no channels for ${regionDisplayName(region).lowercase()} yet", color = tokens.fgDim, fontSize = 15.sp)
                    Text("add one by its youtube name ›", color = accent, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp).clickable(onClick = onChoose))
                }
            }
        }
        items(channels, key = { it.region + "/" + it.handle }) { c ->
            val status = statuses[c.handle]
            val known = statuses.containsKey(c.handle)
            LiveRow(c, status, known, tokens, accent, playing = playing?.handle == c.handle) {
                // Plays here when the stream id is known; otherwise opens the channel in YouTube.
                if (status?.live == true && status.videoId != null) onPlay(c) else openLink(context, liveUrl(c.handle))
            }
        }
    }
}

@Composable
private fun LiveRow(c: LiveChannel, status: LiveStatus?, known: Boolean, tokens: ColorTokens, accent: Color, playing: Boolean, onClick: () -> Unit) {
    val image = rememberRemoteImage(status?.videoId?.let { liveThumbnailUrl(it) })
    val live = status?.live == true
    val off = known && status != null && !status.live
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Box(modifier = Modifier.size(width = 84.dp, height = 48.dp).background(accent.copy(alpha = 0.2f)).clipToBounds()) {
                if (image != null) {
                    androidx.compose.foundation.Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (live) {
                    Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium, modifier = Modifier.background(LiveRed).padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(c.name, color = if (off) tokens.fgDim else tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val state = when {
                    playing -> "playing"
                    live -> "live now · tap to watch"
                    off -> "off air"
                    known -> "tap to open in youtube"
                    else -> "checking…"
                }
                Text("${c.language} · $state", color = if (live) LiveRed else tokens.fgDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChannelPicker(
    followed: List<String>,
    current: List<LiveChannel>,
    tokens: ColorTokens,
    accent: Color,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val all = remember(followed) {
        followed + (listOf(INDIA_COUNTRY_CODE) + SELECTABLE_COUNTRIES.map { it.code } + INTERNATIONAL_REGION_CODE).filter { c -> followed.none { it.equals(c, true) } }
    }
    var showMore by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf(followed.first()) }
    var adding by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val chips = if (showMore) all else followed
    val chosenHandles = current.map { it.handle }.toSet()
    val custom = remember(current) { NewsLivePrefs.custom(context) }
    val channels = remember(region, custom) {
        (channelsForRegion(region) + custom.filter { it.region.equals(region, true) }).distinctBy { it.handle.lowercase() }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text("NEWS", color = tokens.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 18.dp))
        Text("choose channels", color = tokens.fg, fontSize = 34.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            item(key = "chips") {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    chips.forEach { r -> HubFilter(regionDisplayName(r).lowercase(), r.equals(region, true), tokens, accent) { region = r } }
                    if (!showMore) Text("+ more countries", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.clickable { showMore = true })
                }
            }
            item(key = "note") {
                Text(
                    "countries on top are the ones you follow in news settings. ▣ is on your live tv page.",
                    color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
            }
            items(channels, key = { region + "/" + it.handle }) { c ->
                val on = c.handle in chosenHandles
                Column(modifier = Modifier.fillMaxWidth().clickable { NewsLivePrefs.toggle(context, c, current); onChanged() }) {
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(tokens.sheetLine))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.name, color = tokens.fg, fontSize = 17.sp, fontWeight = FontWeight.Light)
                            Text(c.language, color = tokens.fgDim, fontSize = 12.sp)
                        }
                        Text(if (on) "▣" else "▢", color = if (on) accent else tokens.fgDim, fontSize = 22.sp)
                    }
                }
            }
            if (channels.isEmpty()) {
                item(key = "none") { EmptyNote("no channels listed for ${regionDisplayName(region).lowercase()} yet. add one below.", tokens) }
            }
            item(key = "add") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    if (!adding) {
                        Text("+ add a channel by its youtube name", color = accent, fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp).clickable { adding = true })
                    } else {
                        Text("its youtube name, like @NDTV", color = tokens.fgDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        BasicTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            singleLine = true,
                            textStyle = TextStyle(color = tokens.fg, fontSize = 17.sp),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.fillMaxWidth().keepAboveKeyboard().background(tokens.sheet).padding(12.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(
                                "add", color = accent, fontSize = 15.sp,
                                modifier = Modifier.clickable {
                                    val handle = youtubeHandleOf(typed)
                                    if (handle.isNotEmpty()) {
                                        NewsLivePrefs.addCustom(context, LiveChannel(handle, typed.trim().removePrefix("@").ifBlank { handle }, "", region), current)
                                        typed = ""
                                        adding = false
                                        onChanged()
                                    }
                                },
                            )
                            Text("cancel", color = tokens.fgDim, fontSize = 15.sp, modifier = Modifier.clickable { typed = ""; adding = false })
                        }
                    }
                }
            }
        }
    }
}

// --- playing in place --------------------------------------------------------------

/**
 * A live stream played inside the hub through YouTube's own embedded player in a WebView
 * (the channels probed all allow embedding). The page is loaded with this app as its
 * referrer, as YouTube asks of embedding apps; only YouTube's embed address may load.
 * Paused with the app and released when closed. "youtube" hands the same stream to the
 * YouTube app for full-screen viewing, and the player's own full-screen button works too.
 */
@Composable
private fun LivePlayer(
    channel: LiveChannel,
    videoId: String,
    tokens: ColorTokens,
    accent: Color,
    onClose: () -> Unit,
    onFullscreen: (View?, (() -> Unit)?) -> Unit,
    context: Context,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var web by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(lifecycleOwner, web) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> web?.onPause()
                Lifecycle.Event.ON_RESUME -> web?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val host = request?.url?.host.orEmpty()
                                // Stay on YouTube's embed; anything else (a channel link in the player) is not followed here.
                                return !(host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") || host.endsWith("googlevideo.com"))
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                onFullscreen(view) { callback?.onCustomViewHidden(); onFullscreen(null, null) }
                            }

                            override fun onHideCustomView() {
                                onFullscreen(null, null)
                            }
                        }
                        loadUrl(
                            "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1",
                            mapOf("Referer" to "https://${ctx.packageName}"),
                        )
                        web = this
                    }
                },
                onRelease = { it.stopLoading(); it.destroy(); web = null },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(channel.name, color = tokens.fg, fontSize = 15.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("youtube ›", color = accent, fontSize = 14.sp, modifier = Modifier.clickable { openLink(context, liveUrl(channel.handle)) }.padding(horizontal = 10.dp, vertical = 6.dp))
            Text("close", color = tokens.fgDim, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

// --- choosing the news ----------------------------------------------------------

private val TOPIC_LABELS = linkedMapOf(
    "nation" to "national news",
    "entertainment" to "entertainment",
    "cricket" to "cricket",
    "sports" to "sports",
    "tech" to "technology",
    "business" to "business",
    "food" to "food",
)

/**
 * The news settings, as in the glance page's feed settings: the countries followed (any
 * number), the topics on or off with each topic's feeds, and the feeds added by hand
 * (with a place to add one). Changes apply at once and ask the feed worker to refresh;
 * live tv follows the same countries.
 */
@Composable
private fun NewsSettingsScreen(feed: FeedData, tokens: ColorTokens, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { FeedStore.create(context) }
    fun change(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) {
            block()
            FeedRefreshWorker.refreshNow(context)
        }
    }
    val regionCodes = remember { listOf(INDIA_COUNTRY_CODE, INTERNATIONAL_REGION_CODE) + SELECTABLE_COUNTRIES.map { it.code } }
    var url by remember { mutableStateOf("") }
    val custom = feed.sources.filter { it.category !in TOPIC_LABELS }

    Column(modifier = modifier.fillMaxWidth()) {
        Text("NEWS", color = tokens.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 18.dp))
        Text("news settings", color = tokens.fg, fontSize = 34.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
            item(key = "regions-h") { Text("news regions (select any number)", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) }
            item(key = "regions") {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    regionCodes.forEach { code ->
                        val on = code in feed.regions
                        Text(
                            (if (on) "▣ " else "▢ ") + regionDisplayName(code).lowercase(),
                            color = if (on) accent else tokens.fgDim,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable { change { store.toggleRegion(code, !on) } },
                        )
                    }
                }
            }
            item(key = "topics-h") { Text("topics and their feeds", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)) }
            TOPIC_LABELS.forEach { (category, label) ->
                val inCategory = feed.sources.filter { it.category == category }
                if (inCategory.isEmpty()) return@forEach
                val anyOn = inCategory.any { it.enabled }
                item(key = "topic-$category") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            (if (anyOn) "▣ " else "▢ ") + label,
                            color = if (anyOn) tokens.fg else tokens.fgDim,
                            fontSize = 17.sp, fontWeight = FontWeight.Light,
                            modifier = Modifier.fillMaxWidth().clickable { change { store.setCategoryEnabled(category, !anyOn) } }.padding(vertical = 8.dp),
                        )
                        if (anyOn) {
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, bottom = 6.dp),
                            ) {
                                inCategory.forEach { f ->
                                    Text(
                                        (if (f.enabled) "▣ " else "▢ ") + f.name,
                                        color = if (f.enabled) accent else tokens.fgDim,
                                        fontSize = 13.sp,
                                        modifier = Modifier.clickable { change { store.setEnabled(f.url, !f.enabled) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (custom.isNotEmpty()) {
                item(key = "custom-h") { Text("your own feeds", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)) }
                items(custom, key = { "custom-" + it.url }) { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            (if (f.enabled) "▣ " else "▢ ") + f.name,
                            color = if (f.enabled) tokens.fg else tokens.fgDim,
                            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { change { store.setEnabled(f.url, !f.enabled) } },
                        )
                        Text("remove", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.clickable { change { store.removeSource(f.url) } }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                    }
                }
            }
            item(key = "add") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp)) {
                    Text("add a feed by its address", color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth().keepAboveKeyboard(),
                        singleLine = true,
                        textStyle = TextStyle(color = tokens.fg, fontSize = 16.sp),
                        cursorBrush = SolidColor(accent),
                        decorationBox = { inner ->
                            Box(modifier = Modifier.fillMaxWidth().background(tokens.sheet).padding(12.dp)) {
                                if (url.isEmpty()) Text("https://…/rss", color = tokens.fgDim, fontSize = 16.sp)
                                inner()
                            }
                        },
                    )
                    Text(
                        "add", color = accent, fontSize = 15.sp,
                        modifier = Modifier.clickable {
                            val u = url.trim()
                            if (u.startsWith("http", ignoreCase = true)) {
                                change { store.addSource(u, "") }
                                url = ""
                            } else {
                                Toast.makeText(context, "a feed address starts with http", Toast.LENGTH_SHORT).show()
                            }
                        }.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

// --- the tile -------------------------------------------------------------------

/** How long a headline stays on the news tile before the next rolls in. */
const val NEWS_TILE_ROLL_MS = 4_500L

/** How many of the newest stories roll on the news tile. */
const val NEWS_TILE_STORIES = 8

/**
 * The news hub's tile: the newest stories rolling one after another, each with its photo
 * when it has one (headline over the bottom of the picture), else the headline on the
 * tile's own colour. Rolls only while the live tiles are active (like every other live
 * tile) and reads the stored news only; tapping opens the hub.
 */
@Composable
fun NewsHubTileFace(size: TileSize, active: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val feed by remember { FeedStore.create(context).data }.collectAsState(initial = FeedData())
    val color = LocalTileFaceColor.current
    val stories = remember(feed.articles) { feed.articles.sortedByDescending { it.publishedAtMillis }.take(NEWS_TILE_STORIES) }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, stories.size) {
        if (!active || stories.size < 2) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(NEWS_TILE_ROLL_MS)
            index = (index + 1) % stories.size
        }
    }
    if (size == TileSize.SMALL || stories.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            Icon(TileIcons["newshub"], contentDescription = null, tint = color, modifier = Modifier.align(Alignment.Center).size(if (size == TileSize.SMALL) 28.dp else 44.dp))
            if (size != TileSize.SMALL) Text("news", color = color, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        return
    }
    val story = stories[index.coerceIn(0, stories.lastIndex)]
    val lines = when (size) {
        TileSize.LARGE, TileSize.XLARGE, TileSize.TALL_MEDIUM, TileSize.COLUMN -> 4
        TileSize.WIDE, TileSize.WIDE_MEDIUM -> 3
        else -> 2
    }
    androidx.compose.animation.Crossfade(targetState = story, animationSpec = tween(500), modifier = modifier.fillMaxSize(), label = "newsTileRoll") { a ->
        val photo = rememberRemoteImage(a.imageUrl)
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            if (photo != null) {
                androidx.compose.foundation.Image(bitmap = photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xCC000000)),
                    ),
                )
            }
            Column(
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            ) {
                Text(
                    a.source,
                    color = (if (photo != null) Color.White else color).copy(alpha = 0.8f),
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    a.title,
                    color = if (photo != null) Color.White else color,
                    fontSize = if (lines >= 3) 15.sp else 13.sp, lineHeight = if (lines >= 3) 18.sp else 15.sp,
                    fontWeight = FontWeight.Light, maxLines = lines, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
