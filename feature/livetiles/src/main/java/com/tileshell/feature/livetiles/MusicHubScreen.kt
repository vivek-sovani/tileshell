package com.tileshell.feature.livetiles

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tileshell.core.data.AppCatalogRepository
import com.tileshell.core.data.AppCategories
import com.tileshell.core.data.AppEntry
import com.tileshell.core.data.AppLauncher
import com.tileshell.core.design.ColorTokens
import com.tileshell.core.design.SheetStage
import com.tileshell.core.design.TileAccents
import com.tileshell.core.design.TileIcons
import com.tileshell.core.design.colorTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// All six real pages the pager holds (used for page routing/count) — "apps"
// and "history" are still fully reachable pages, just not shown as their own
// pivot label any more (see VISIBLE_HUB_TABS): with six labels this row
// overflowed a phone-width screen with no way to scroll to the rest
// (user-reported: "horizontal menu not showing correctly"). Reached instead
// via the "apps ›"/"history ›" links on "now playing", the same pattern
// "history ›" already used on its own.
private val HUB_PIVOTS = listOf("now playing", "library", "podcasts", "radio", "apps", "history")

// The pivot labels actually shown in the header row.
private val VISIBLE_HUB_TABS = listOf("now playing", "library", "podcasts", "radio")

private val LOCAL_AUDIO_PERMISSION: String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Full-screen music hub — same Panorama/Pivot shell as [WeatherHubScreen]:
 * fixed header, a tappable/swipeable pivot row, six independently-scrolling
 * pages ("now playing" / "library" / "podcasts" / "radio" / "apps" /
 * "history") — only the first four show up as their own pivot label
 * ([VISIBLE_HUB_TABS]; "apps"/"history" are reached via links on "now
 * playing" instead, see below). The destination for tapping the music tile.
 *
 * "apps" lists every installed music/audio player ([AppCategories.isMusicApp]
 * — the OS-resolved music role plus the declared `CATEGORY_AUDIO`, both
 * already surfaced by [AppCatalogRepository] with no extra permission) so the
 * hub covers every player on the device, not just whichever one last had an
 * active session. "library" browses on-device tracks/albums/playlists
 * ([LocalMusicLibrary]); "podcasts" searches/subscribes/browses-by-genre via
 * the free iTunes Search + charts APIs ([searchPodcasts], [topPodcasts]) and
 * browses a subscribed show's episodes ([fetchPodcastFeed]); "radio" searches/
 * favorites/browses-by-genre-or-language live internet radio stations via the
 * free Radio-Browser directory ([searchRadioStations], [stationsByTag],
 * [stationsByLanguage]). All three play through the same [LocalMusicPlayer]
 * ([PlayableAudio] is the shared abstraction), which keeps playing in the
 * background (a real foreground service + lock-screen/notification controls,
 * [LocalMusicPlaybackService]) after this screen closes. "history" is
 * [MusicHistory] — the last song played on each source (one row per app plus
 * one for the local library, each with its own play/pause control), since
 * there's no system "recently played" API to read instead; reachable via a
 * "history ›" link on "now playing" itself (same for "apps ›"). "now playing"
 * reads the same
 * [MediaCenter] the music tile already reads (its transport buttons dispatch
 * through the exact same session), so play/pause/skip here and on the tile
 * always agree — a separate concern from "library"'s own in-hub playback.
 */
@Composable
fun MusicHubScreen(
    visible: Boolean,
    dark: Boolean,
    accentId: String,
    onDismiss: () -> Unit,
    rightHalf: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "musicHubProgress",
    )
    if (!visible && progress == 0f) return

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current

    BackHandler(enabled = visible) { onDismiss() }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { HUB_PIVOTS.size })
    val pagerScope = rememberCoroutineScope()
    val localPlayback by LocalMusicPlayer.state.collectAsState()
    val externalMedia by MediaCenter.nowPlaying.collectAsState()
    val anyPlaying = localPlayback.playing || externalMedia.values.any { it.playing }

    // POST_NOTIFICATIONS (API 33+) gates whether LocalMusicPlaybackService's
    // foreground-service notification actually shows — asked contextually,
    // the first time a local track actually starts playing (not bundled into
    // the app's upfront ask), matching this project's existing "ask in
    // context" convention (e.g. the steps tile). Playback itself works either
    // way; declining just means no notification/lock-screen controls.
    var notificationsAsked by remember { mutableStateOf(false) }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(localPlayback.item?.id) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAsked && localPlayback.item != null) {
            notificationsAsked = true
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Keeps the display on while something is actively playing — user-
    // requested, since the screen timing out mid-playback/mid-browse is
    // annoying even though playback itself (audio) keeps running regardless.
    // Cleared automatically the moment playback stops or this screen closes
    // (DisposableEffect's onDispose fires on either).
    DisposableEffect(anyPlaying) {
        val activity = context.findActivity()
        if (anyPlaying) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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
                // underneath. Real user-reported bug: tapping near the
                // "library" pivot label sometimes opened a Start calendar
                // tile's own google-search fallback instead.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                Spacer(Modifier.height(22.dp))
                // Two-tone Zune-hub title ("music" + "apps"), matching the
                // approved mockup exactly — left-aligned, clipped at the
                // edge rather than wrapping, distinct from the other hubs'
                // plain centered title (the real Zune hub's own title was
                // itself two-tone/unique among WP's hubs, so this asymmetry
                // is WP-faithful, not an inconsistency).
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = tokens.fg)) { append("music") }
                        withStyle(SpanStyle(color = accent)) { append("+apps") }
                    },
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-1).sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.height(16.dp))

                // Stacked vertically, not the horizontal row this session
                // started with — user-requested ("display menus vertically
                // as shown in prototype"), and it sidesteps the overflow
                // problem entirely regardless of how many pivots there are.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VISIBLE_HUB_TABS.forEach { label ->
                        val index = HUB_PIVOTS.indexOf(label)
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

            // Jumps to "now playing" the moment a track actually starts —
            // covers both a fresh tap in the library and an auto-advance to
            // the next queued track. User-requested; replaces the persistent
            // bottom playback bar that used to show across every page (now
            // redundant — "now playing" itself shows this prominently).
            // Keyed on the *transition* into playing, not just the item id:
            // replaying the same already-loaded-but-paused item (e.g. from
            // "history", when it happens to be the current track) never
            // changes localPlayback.item?.id, so keying on id alone never
            // re-fired for that case — user-reported: "when i play from
            // history after playing need to jump to now playing". Covers
            // podcast episodes and radio stations too, not just local tracks.
            var wasLocalPlaying by remember { mutableStateOf(localPlayback.playing) }
            LaunchedEffect(localPlayback.item?.id, localPlayback.playing) {
                if (localPlayback.playing && !wasLocalPlaying) pagerState.animateScrollToPage(0)
                wasLocalPlaying = localPlayback.playing
            }
            // Same jump for an external app's own playback — this only ever
            // covered the local library before (user-reported: "when device
            // songs are played it jumps to now playing view not when playing
            // other app songs"). Keyed on the currently-playing entry's own
            // identity (package + title/artist, not just "something is
            // playing") so it re-fires on a genuinely different track the
            // same way the local jump does, not on every unrelated republish
            // of the same still-playing track.
            val activeExternalKey = externalMedia.entries.firstOrNull { it.value.playing }
                ?.let { (pkg, np) -> "$pkg|${np.title}|${np.artist}" }
            LaunchedEffect(activeExternalKey) {
                if (activeExternalKey != null) pagerState.animateScrollToPage(0)
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> NowPlayingPage(
                        accent = accent,
                        tokens = tokens,
                        onOpenApps = { pagerScope.launch { pagerState.animateScrollToPage(HUB_PIVOTS.indexOf("apps")) } },
                        onOpenHistory = { pagerScope.launch { pagerState.animateScrollToPage(HUB_PIVOTS.indexOf("history")) } },
                    )
                    1 -> LibraryPage(context, accent, tokens)
                    2 -> PodcastsPage(context, accent, tokens)
                    3 -> RadioPage(context, accent, tokens)
                    4 -> MusicAppsPage(context, accent, tokens)
                    else -> HistoryPage(context, accent, tokens)
                }
            }

            // Bottom app bar (mockup's own convention) — back lives here, not
            // as a standalone top-corner button.
            HubAppBar(tokens = tokens, actions = listOf(HubAppBarAction("back", "back", onDismiss)))
        }
    }
}

@Composable
private fun LocalPlaybackButton(
    iconKey: String,
    description: String,
    tint: Color,
    size: Dp = 34.dp,
    iconSize: Dp = 20.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(TileIcons[iconKey], contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * "Now playing" — reads *two* separate playback sources and shows whichever
 * is actually active: [MediaCenter] (another app's own session) and
 * [LocalMusicPlayer] (a track/album/playlist played in-hub from the
 * "library" page). These are genuinely different mechanisms — MediaCenter
 * needs no player of its own, LocalMusicPlayer needs no external app — so
 * there is no single "current track" to read; whichever is playing wins, an
 * external session breaking the tie since a real app's own now-playing takes
 * precedence over TileShell's own library browsing.
 */
@Composable
private fun NowPlayingPage(accent: Color, tokens: ColorTokens, onOpenApps: () -> Unit, onOpenHistory: () -> Unit) {
    val media by MediaCenter.nowPlaying.collectAsState()
    val artworkMap by MediaCenter.artwork.collectAsState()
    val externalEntry = media.entries.firstOrNull { it.value.playing } ?: media.entries.firstOrNull()
    val localPlayback by LocalMusicPlayer.state.collectAsState()
    val localItem = localPlayback.item
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        // User-requested links straight from "now playing" to the "apps" and
        // "history" pages, rather than making people swipe/tap over to them
        // — both moved off the crowded pivot row (see VISIBLE_HUB_TABS).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 2.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "apps ›",
                color = accent,
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenApps,
                ),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "history ›",
                color = accent,
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenHistory,
                ),
            )
        }
        when {
            externalEntry != null && (externalEntry.value.playing || localItem == null) ->
                ExternalNowPlaying(externalEntry, artworkMap, accent, tokens, context)
            localItem != null -> PlayerNowPlaying(localItem, localPlayback.playing, accent, tokens, context)
            else -> Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Text("nothing playing", color = tokens.fgDim, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "start something in one of your music apps, or play a track from the library, a podcast, or a radio station",
                    color = tokens.fgDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingHero(art: ImageBitmap?, accent: Color) {
    // "Big size, as in the mockup" — a full-width square hero instead of a
    // fixed short strip, matching the approved now-playing page treatment.
    TileImageBackground(image = art, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        if (art == null) {
            Box(Modifier.fillMaxSize().background(accent), contentAlignment = Alignment.Center) {
                Icon(TileIcons["music"], contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
        }
    }
}

@Composable
private fun ExternalNowPlaying(
    entry: Map.Entry<String, NowPlaying>,
    artworkMap: Map<String, Bitmap>,
    accent: Color,
    tokens: ColorTokens,
    context: Context,
) {
    val (packageName, np) = entry
    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        NowPlayingHero(artworkMap[packageName]?.asImageBitmap(), accent)
        Spacer(Modifier.height(14.dp))
        Text(text = np.title, color = tokens.fg, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (np.artist.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(text = np.artist, color = tokens.fgDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        // Bigger than the shared default (34dp/22dp) — this page's transport
        // row is its own main interactive element, not a secondary control on
        // a small tile/card, and read as too small at the shared size
        // (user-requested).
        MediaTransportControls(
            playing = np.playing,
            packageName = packageName,
            tint = tokens.fg,
            enabled = true,
            buttonSize = 56.dp,
            iconSize = 28.dp,
        )
        Spacer(Modifier.height(20.dp))
        val label = remember(packageName) { appLabelOrNull(context, packageName) } ?: packageName
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { openApp(context, packageName) },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = rememberAppIconBitmap(packageName, sizePx = 64)
            if (icon != null) {
                androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text("playing from $label", color = tokens.fgDim, fontSize = 12.sp)
        }
    }
}

/**
 * "Now playing" for anything [LocalMusicPlayer] itself is streaming — a
 * local track, a podcast episode, or a radio station — sharing one hero/
 * transport-controls layout ([PlayableAudio] is exactly the abstraction that
 * makes this possible; only art loading and the "playing from…" caption
 * differ per kind).
 */
@Composable
private fun PlayerNowPlaying(item: PlayableAudio, playing: Boolean, accent: Color, tokens: ColorTokens, context: Context) {
    // A full-width square hero needs a much larger request than a list
    // thumbnail — 600px upscaled across a ~1080px-wide screen was visibly
    // blurry. 1024 is generous enough to not be our own bottleneck for a
    // local track; podcast/radio art loads at whatever resolution its host
    // actually serves.
    val art = when (item) {
        is PlayableAudio.Local -> rememberLocalAlbumArt(context, item.track.albumId, sizePx = 1024)
        is PlayableAudio.Episode -> rememberRemoteArt(item.episode.imageUrl ?: item.show.artworkUrl)
        is PlayableAudio.RadioStream -> rememberRemoteArt(item.station.faviconUrl)
    }
    val caption = when (item) {
        is PlayableAudio.Local -> "playing from your library"
        is PlayableAudio.Episode -> "podcast · ${item.show.title}"
        is PlayableAudio.RadioStream -> "live radio"
    }
    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        NowPlayingHero(art, accent)
        Spacer(Modifier.height(14.dp))
        Text(text = item.title, color = tokens.fg, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.subtitle.isNotEmpty() && item !is PlayableAudio.RadioStream) {
            Spacer(Modifier.height(2.dp))
            Text(text = item.subtitle, color = tokens.fgDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        // Bigger than the shared default (34dp/20dp), matching
        // ExternalNowPlaying's own bump — this page's transport row is its
        // own main interactive element (user-requested). Previous/next are
        // meaningless for a lone radio stream (a queue of one) but harmless
        // no-ops, same as at the end/start of any single-item queue.
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LocalPlaybackButton("prev", "previous", tokens.fg, size = 56.dp, iconSize = 28.dp) {
                LocalMusicPlayer.previous(context)
            }
            LocalPlaybackButton(
                if (playing) "pause" else "play",
                "play/pause",
                tokens.fg,
                size = 56.dp,
                iconSize = 28.dp,
            ) {
                LocalMusicPlayer.togglePlayPause()
            }
            LocalPlaybackButton("next", "next", tokens.fg, size = 56.dp, iconSize = 28.dp) {
                LocalMusicPlayer.next(context)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(caption, color = tokens.fgDim, fontSize = 12.sp)
    }
}

@Composable
private fun MusicAppsPage(context: Context, accent: Color, tokens: ColorTokens) {
    val repository = remember(context) { AppCatalogRepository(context) }
    val apps by repository.apps.collectAsState(initial = emptyList())
    // AppCatalogRepository enumerates every launcher activity, not every
    // distinct app — an app that declares more than one would otherwise list
    // itself twice here. One row per package, same dedup EdgeStripSheet
    // already applies for the same reason. That alone wasn't enough for
    // Amazon Music specifically though (still user-reported afterward): on
    // this Samsung device it's genuinely installed twice under two different
    // real packages — `com.amazon.mp3` and `com.amazon.mp3.galaxy`, a
    // Samsung Galaxy Store-specific build bundled alongside the regular one,
    // both correctly resolving as their own separate music app. Distinct
    // packages, so packageName-dedup can't (and, for the real App List,
    // shouldn't — both are genuinely separately launchable) tell them apart;
    // this picker's whole point is offering a de-duplicated set of *players*
    // to choose from, though, so a second dedup pass on the normalized label
    // collapses same-named duplicates like this one too.
    val musicApps = remember(apps) {
        apps.filter { AppCategories.isMusicApp(it) }
            .distinctBy { it.packageName }
            .distinctBy { it.label.trim().lowercase() }
    }
    var query by remember { mutableStateOf("") }
    val shown = remember(musicApps, query) {
        if (query.isBlank()) musicApps else musicApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibrarySearchField(query, { query = it }, "search apps", tokens)
        if (musicApps.isEmpty()) {
            LibraryEmptyState("no music apps found", tokens)
        } else if (shown.isEmpty()) {
            LibraryEmptyState("no matches", tokens)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(shown, key = { it.key }) { app ->
                    MusicAppCell(app, accent, tokens) { AppLauncher.launch(context, app.packageName, app.activityName) }
                }
            }
        }
    }
}

/**
 * Search box matching the app's existing convention exactly (see
 * QuickSearchOverlay's own search field: `tokens.chip` background, 4dp
 * corners, 44dp height, leading search icon, a "clear" close icon once
 * there's text) — live-filters the caller's list on every keystroke, no
 * submit action needed.
 */
@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, tokens: ColorTokens) {
    Row(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .height(44.dp)
            .background(tokens.chip, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TileIcons["search"], contentDescription = null, tint = tokens.fgDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, color = tokens.fgDim, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = tokens.fg, fontSize = 14.sp),
                cursorBrush = SolidColor(tokens.fg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                TileIcons["close"],
                contentDescription = "clear",
                tint = tokens.fgDim,
                modifier = Modifier.size(16.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onQueryChange("") },
                ),
            )
        }
    }
}

@Composable
private fun MusicAppCell(app: AppEntry, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
    val icon = rememberAppIconBitmap(app.packageName, sizePx = 96)
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(48.dp))
        } else {
            Box(Modifier.size(48.dp).background(accent), contentAlignment = Alignment.Center) {
                Icon(TileIcons["music"], contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            color = tokens.fg,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The last song played on each source (one row per app, plus one for the
 * local library — see [MusicHistory]'s own doc comment for why this is
 * collapsed to "last per source" rather than a full chronological log): a
 * play/pause control per row that's always unambiguous, since a row's source
 * only ever has one real current session to control.
 */
@Composable
private fun HistoryPage(context: Context, accent: Color, tokens: ColorTokens) {
    val history by MusicHistory.history(context).collectAsState(initial = emptyList())
    if (history.isEmpty()) {
        Text(
            "nothing played yet",
            color = tokens.fgDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        return
    }
    val scope = rememberCoroutineScope()
    val media by MediaCenter.nowPlaying.collectAsState()
    val localPlayback by LocalMusicPlayer.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 32.dp),
    ) {
        history.forEachIndexed { index, track ->
            val activeLocal = (localPlayback.item as? PlayableAudio.Local)?.track
            val playing = if (track.isLocal) {
                localPlayback.playing && activeLocal != null && (
                    (track.localTrackId != null && activeLocal.id == track.localTrackId) ||
                        (track.localTrackId == null && activeLocal.title == track.title && activeLocal.artist == track.artist)
                    )
            } else {
                media[track.packageName]?.playing == true
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (track.isLocal) {
                    // High-contrast accent plate, matching MusicAppCell's own
                    // no-icon fallback — the earlier muted fgDim/bg combo read
                    // as blank next to other rows' real app icons (user-reported).
                    Box(Modifier.size(28.dp).background(accent), contentAlignment = Alignment.Center) {
                        Icon(TileIcons["music"], contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                } else {
                    val icon = rememberAppIconBitmap(track.packageName, sizePx = 64)
                    if (icon != null) {
                        androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (track.artist.isNotEmpty()) {
                        Text(track.artist, color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                LocalPlaybackButton(
                    iconKey = if (playing) "pause" else "play",
                    description = if (playing) "pause" else "play",
                    tint = tokens.fg,
                ) {
                    if (track.isLocal) {
                        // Re-plays the actual file via the same local player
                        // the library page uses. Tries the recorded id
                        // first, falling back to an exact title+artist match
                        // for an entry recorded before localTrackId existed
                        // — silently does nothing only if the file's
                        // genuinely since been deleted/moved.
                        if (playing) {
                            LocalMusicPlayer.togglePlayPause()
                        } else {
                            scope.launch {
                                val id = track.localTrackId
                                val found = (id?.let { LocalMusicLibrary.trackById(context, it) })
                                    ?: LocalMusicLibrary.findByTitleArtist(context, track.title, track.artist)
                                if (found != null) LocalMusicPlayer.playQueue(context, listOf(found), 0)
                            }
                        }
                    } else if (media.containsKey(track.packageName)) {
                        // A live session for this app: this always controls
                        // its own actual current track, never a stale one —
                        // the whole point of collapsing history to one row
                        // per source (see MusicHistory's own doc comment).
                        MediaCenter.togglePlayPause(track.packageName)
                    } else {
                        // That app's session has genuinely ended since — there's
                        // no cross-app API to restart a specific past track from
                        // scratch, so the closest we can do is just open the app.
                        openApp(context, track.packageName)
                    }
                }
            }
            if (index < history.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.sheetLine))
            }
        }
    }
}

private fun appLabelOrNull(context: Context, packageName: String): String? = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}.getOrNull()

private fun openApp(context: Context, packageName: String) {
    runCatching {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let { context.startActivity(it) }
    }
}

private enum class LibraryMode { TRACKS, ALBUMS, PLAYLISTS }

/** Which album/playlist the library is currently drilled into — see [TrackListDetailPage]. */
private sealed interface LibrarySelection {
    data class Album(val album: LocalAlbum) : LibrarySelection
    data class Playlist(val playlist: LocalPlaylist) : LibrarySelection
}

@Composable
private fun LibraryPage(context: Context, accent: Color, tokens: ColorTokens) {
    val granted = rememberPermissionGranted(LOCAL_AUDIO_PERMISSION)
    if (!granted) {
        LibraryPermissionGate(tokens, accent)
        return
    }
    var mode by remember { mutableStateOf(LibraryMode.TRACKS) }
    var query by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf<LibrarySelection?>(null) }

    when (val sel = selection) {
        is LibrarySelection.Album -> {
            val tracks by produceState<List<LocalTrack>?>(initialValue = null, sel.album.id) {
                value = LocalMusicLibrary.tracksForAlbum(context, sel.album.id)
            }
            TrackListDetailPage(
                title = sel.album.title,
                subtitle = "${sel.album.trackCount} tracks",
                tracks = tracks,
                context = context,
                tokens = tokens,
                accent = accent,
                onBack = { selection = null },
            )
            return
        }
        is LibrarySelection.Playlist -> {
            val tracks by produceState<List<LocalTrack>?>(initialValue = null, sel.playlist.id) {
                value = LocalMusicLibrary.tracksForPlaylist(context, sel.playlist.id)
            }
            TrackListDetailPage(
                title = sel.playlist.name,
                subtitle = tracks?.size?.let { "$it tracks" } ?: "",
                tracks = tracks,
                context = context,
                tokens = tokens,
                accent = accent,
                onBack = { selection = null },
            )
            return
        }
        null -> Unit
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LibraryModeLabel("tracks", mode == LibraryMode.TRACKS, tokens) { mode = LibraryMode.TRACKS }
            LibraryModeLabel("albums", mode == LibraryMode.ALBUMS, tokens) { mode = LibraryMode.ALBUMS }
            LibraryModeLabel("playlists", mode == LibraryMode.PLAYLISTS, tokens) { mode = LibraryMode.PLAYLISTS }
        }
        LibrarySearchField(query, { query = it }, "search $mode", tokens)
        when (mode) {
            LibraryMode.TRACKS -> TracksList(context, accent, tokens, query)
            LibraryMode.ALBUMS -> AlbumsGrid(context, accent, tokens, query) { selection = LibrarySelection.Album(it) }
            LibraryMode.PLAYLISTS -> PlaylistsList(context, tokens, query) { selection = LibrarySelection.Playlist(it) }
        }
    }
}

/**
 * An album/playlist's own track list (user-requested: tapping one should show
 * its songs with play/shuffle, not start playing immediately). "play" queues
 * in album/playlist order from the top; "shuffle" queues the same tracks in a
 * freshly randomized order (a new shuffle each time, not a repeating one);
 * tapping an individual track starts the (unshuffled) queue there.
 */
@Composable
private fun TrackListDetailPage(
    title: String,
    subtitle: String,
    tracks: List<LocalTrack>?,
    context: Context,
    tokens: ColorTokens,
    accent: Color,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                TileIcons["back"],
                contentDescription = "back",
                tint = tokens.fg,
                modifier = Modifier.size(18.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = tokens.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) Text(subtitle, color = tokens.fgDim, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            tracks == null -> LibraryEmptyState("loading…", tokens)
            tracks.isEmpty() -> LibraryEmptyState("no tracks in here", tokens)
            else -> {
                Row(modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 8.dp)) {
                    DetailAction("play", "play", accent) { LocalMusicPlayer.playQueue(context, tracks, 0) }
                    Spacer(Modifier.width(20.dp))
                    DetailAction("shuffle", "shuffle", accent) {
                        LocalMusicPlayer.playQueue(context, tracks.shuffled(), 0)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(track, tokens) { LocalMusicPlayer.playQueue(context, tracks, index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailAction(iconKey: String, label: String, accent: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Icon(TileIcons[iconKey], contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = accent, fontSize = 13.sp)
    }
}

@Composable
private fun LibraryModeLabel(label: String, selected: Boolean, tokens: ColorTokens, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) tokens.fg else tokens.fgDim,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    )
}

@Composable
private fun LibraryPermissionGate(tokens: ColorTokens, accent: Color) {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (!isGranted && !canShowSystemPermissionDialog(context, LOCAL_AUDIO_PERMISSION, asked = true)) {
            blocked = true
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("browse your music", color = tokens.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "plays tracks, albums and playlists already on this device. stays on your device — nothing is sent anywhere.",
            color = tokens.fgDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        if (blocked) {
            Text(
                "allow access from settings",
                color = accent,
                fontSize = 14.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { openAppPermissionSettings(context) },
                ),
            )
        } else {
            Text(
                "allow access",
                color = accent,
                fontSize = 14.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { requestPermission.launch(LOCAL_AUDIO_PERMISSION) },
                ),
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(text: String, tokens: ColorTokens) {
    Text(text, color = tokens.fgDim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
}

@Composable
private fun TracksList(context: Context, accent: Color, tokens: ColorTokens, query: String) {
    val tracks by produceState<List<LocalTrack>?>(initialValue = null, context) {
        value = LocalMusicLibrary.tracks(context)
    }
    val list = remember(tracks, query) {
        tracks?.let { all -> if (query.isBlank()) all else all.filter { it.title.contains(query, ignoreCase = true) } }
    }
    when {
        list == null -> LibraryEmptyState("loading your library…", tokens)
        list.isEmpty() -> LibraryEmptyState(if (query.isBlank()) "no tracks found" else "no matches", tokens)
        else -> Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                // Extra bottom room so the last row isn't hidden under the
                // floating random-play button below.
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                itemsIndexed(list, key = { _, track -> track.id }) { index, track ->
                    TrackRow(track, tokens) { LocalMusicPlayer.playQueue(context, list, index) }
                }
            }
            // Random-play FAB, user-requested: shuffles whatever's currently
            // shown (respects the search filter) instead of requiring the
            // user to tap into a specific track first.
            RandomPlayButton(
                accent = accent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) { LocalMusicPlayer.playQueue(context, list.shuffled(), 0) }
        }
    }
}

@Composable
private fun RandomPlayButton(accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(TileIcons["shuffle"], contentDescription = "random play", tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TrackRow(track: LocalTrack, tokens: ColorTokens, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = rememberLocalAlbumArt(context, track.albumId, sizePx = 96)
        if (art != null) {
            androidx.compose.foundation.Image(
                bitmap = art,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(track.artist.ifEmpty { null }, track.album.ifEmpty { null }).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(formatTrackDuration(track.durationMs), color = tokens.fgDim, fontSize = 11.sp)
    }
}

@Composable
private fun AlbumsGrid(
    context: Context,
    accent: Color,
    tokens: ColorTokens,
    query: String,
    onSelect: (LocalAlbum) -> Unit,
) {
    val albums by produceState<List<LocalAlbum>?>(initialValue = null, context) {
        value = LocalMusicLibrary.albums(context)
    }
    val list = remember(albums, query) {
        albums?.let { all -> if (query.isBlank()) all else all.filter { it.title.contains(query, ignoreCase = true) } }
    }
    when {
        list == null -> LibraryEmptyState("loading your library…", tokens)
        list.isEmpty() -> LibraryEmptyState(if (query.isBlank()) "no albums found" else "no matches", tokens)
        // 2 columns, not 3 — user-requested bigger album art; each cell's art
        // fills the cell width as a square instead of a small fixed thumbnail.
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(list, key = { it.id }) { album ->
                AlbumCell(album, accent, tokens) { onSelect(album) }
            }
        }
    }
}

@Composable
private fun AlbumCell(album: LocalAlbum, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
    val context = LocalContext.current
    val art = rememberLocalAlbumArt(context, album.id, sizePx = 500)
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) {
            androidx.compose.foundation.Image(
                bitmap = art,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TileIcons["music"], contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(album.title, color = tokens.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${album.trackCount} tracks", color = tokens.fgDim, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun PlaylistsList(
    context: Context,
    tokens: ColorTokens,
    query: String,
    onSelect: (LocalPlaylist) -> Unit,
) {
    val playlists by produceState<List<LocalPlaylist>?>(initialValue = null, context) {
        value = LocalMusicLibrary.playlists(context)
    }
    val list = remember(playlists, query) {
        playlists?.let { all -> if (query.isBlank()) all else all.filter { it.name.contains(query, ignoreCase = true) } }
    }
    when {
        list == null -> LibraryEmptyState("loading your library…", tokens)
        // Genuinely common, not a bug: most apps stopped writing playlists
        // through this legacy provider once scoped storage landed, so an
        // empty result here just means none of this device's apps used it.
        list.isEmpty() -> LibraryEmptyState(
            if (query.isBlank()) "no playlists found on this device" else "no matches",
            tokens,
        )
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
        ) {
            list.forEachIndexed { index, playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(playlist) },
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(TileIcons["music"], contentDescription = null, tint = tokens.fgDim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(playlist.name, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index < list.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.sheetLine))
                }
            }
        }
    }
}

/** "3:07" — pure so it's unit-testable without a live [LocalTrack]. */
fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

// ---- Podcasts -----------------------------------------------------------

/**
 * Search (via the free iTunes Search API, [searchPodcasts]), browse by genre
 * ([PODCAST_GENRES]/[topPodcasts] — user-requested: "can we show categories
 * like genre, language etc."), and subscribed shows ([PodcastStore]).
 * Browsing a show's episodes ([PodcastEpisodesPage]) never auto-subscribes —
 * subscribing is its own explicit action there, the same way opening a show
 * in a real podcast app doesn't silently follow it.
 */
@Composable
private fun PodcastsPage(context: Context, accent: Color, tokens: ColorTokens) {
    val subscriptions by PodcastStore.subscriptions(context).collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PodcastSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var selectedGenre by remember { mutableStateOf<PodcastGenre?>(null) }
    var genreResults by remember { mutableStateOf<List<PodcastSearchResult>>(emptyList()) }
    var loadingGenre by remember { mutableStateOf(false) }
    var openFeedUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        selectedGenre = null // a typed search takes over from a genre browse
        searching = true
        delay(450) // debounce — avoids a network search per keystroke
        results = searchPodcasts(query)
        searching = false
    }

    LaunchedEffect(selectedGenre) {
        val genre = selectedGenre
        if (genre == null) {
            genreResults = emptyList()
            return@LaunchedEffect
        }
        loadingGenre = true
        genreResults = topPodcasts(genre.id)
        loadingGenre = false
    }

    val feedUrl = openFeedUrl
    if (feedUrl != null) {
        val show = subscriptions.firstOrNull { it.feedUrl == feedUrl }
            ?: (results + genreResults).firstOrNull { it.feedUrl == feedUrl }?.let {
                PodcastSubscription(it.feedUrl, it.title, it.artworkUrl, 0L)
            }
        if (show != null) {
            PodcastEpisodesPage(
                context = context,
                accent = accent,
                tokens = tokens,
                show = show,
                isSubscribed = subscriptions.any { it.feedUrl == show.feedUrl },
                onSubscribeToggle = { subscribe ->
                    if (subscribe) {
                        PodcastStore.subscribe(context, show.copy(subscribedAtMillis = System.currentTimeMillis()))
                    } else {
                        PodcastStore.unsubscribe(context, show.feedUrl)
                    }
                },
                onBack = { openFeedUrl = null },
            )
            return
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibrarySearchField(query, { query = it }, "search podcasts", tokens)
        if (query.isBlank()) {
            CategoryChipRow(PODCAST_GENRES.map { it.label }, selectedGenre?.label, accent, tokens) { label ->
                val genre = PODCAST_GENRES.first { it.label == label }
                selectedGenre = if (selectedGenre == genre) null else genre
            }
        }
        when {
            query.isNotBlank() && searching && results.isEmpty() -> LibraryEmptyState("searching…", tokens)
            query.isNotBlank() && results.isEmpty() -> LibraryEmptyState("no matches", tokens)
            query.isNotBlank() -> PodcastShowList(
                results.map { PodcastShowEntry(it.feedUrl, it.title, it.artworkUrl) },
                tokens,
            ) { openFeedUrl = it }
            selectedGenre != null && loadingGenre && genreResults.isEmpty() -> LibraryEmptyState("loading…", tokens)
            selectedGenre != null && genreResults.isEmpty() -> LibraryEmptyState("no matches", tokens)
            selectedGenre != null -> PodcastShowList(
                genreResults.map { PodcastShowEntry(it.feedUrl, it.title, it.artworkUrl) },
                tokens,
            ) { openFeedUrl = it }
            subscriptions.isEmpty() -> LibraryEmptyState("search, or pick a category above, to find podcasts", tokens)
            else -> PodcastShowList(
                subscriptions.map { PodcastShowEntry(it.feedUrl, it.title, it.artworkUrl) },
                tokens,
            ) { openFeedUrl = it }
        }
    }
}

/** Horizontally-scrollable pill row shared by the podcasts/radio pages'
 * genre/language category browsing — tapping the already-selected pill
 * deselects it. */
@Composable
private fun CategoryChipRow(
    labels: List<String>,
    selectedLabel: String?,
    accent: Color,
    tokens: ColorTokens,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            val isSelected = label == selectedLabel
            Text(
                label,
                color = if (isSelected) Color.White else tokens.fg,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) accent else tokens.chip)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(label) },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private data class PodcastShowEntry(val feedUrl: String, val title: String, val artworkUrl: String?)

@Composable
private fun PodcastShowList(shows: List<PodcastShowEntry>, tokens: ColorTokens, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 32.dp),
    ) {
        shows.forEachIndexed { index, show ->
            PodcastShowRow(show.title, show.artworkUrl, tokens) { onSelect(show.feedUrl) }
            if (index < shows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.sheetLine))
        }
    }
}

@Composable
private fun PodcastShowRow(title: String, artworkUrl: String?, tokens: ColorTokens, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = rememberRemoteArt(artworkUrl)
        if (art != null) {
            androidx.compose.foundation.Image(
                bitmap = art,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
            )
        } else {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(tokens.chip), contentAlignment = Alignment.Center) {
                Icon(TileIcons["music"], contentDescription = null, tint = tokens.fgDim, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** A subscribed (or merely being-browsed) show's episode list, freshly
 * fetched every time it's opened ([fetchPodcastFeed]) — a subscription only
 * ever persists the show's own identity, never a cached episode list, so
 * this can never go stale. */
@Composable
private fun PodcastEpisodesPage(
    context: Context,
    accent: Color,
    tokens: ColorTokens,
    show: PodcastSubscription,
    isSubscribed: Boolean,
    onSubscribeToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val feed by produceState<PodcastShow?>(initialValue = null, show.feedUrl) {
        value = fetchPodcastFeed(show.feedUrl)
    }
    val currentFeed = feed
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                TileIcons["back"],
                contentDescription = "back",
                tint = tokens.fg,
                modifier = Modifier.size(18.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(show.title, color = tokens.fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    currentFeed?.episodes?.size?.let { "$it episodes" } ?: "",
                    color = tokens.fgDim,
                    fontSize = 11.sp,
                )
            }
            Text(
                if (isSubscribed) "subscribed" else "subscribe",
                color = accent,
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSubscribeToggle(!isSubscribed) },
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            currentFeed == null -> LibraryEmptyState("loading episodes…", tokens)
            currentFeed.episodes.isEmpty() -> LibraryEmptyState("no episodes found", tokens)
            else -> {
                val episodes = currentFeed.episodes
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    itemsIndexed(episodes, key = { _, ep -> ep.guid }) { index, episode ->
                        Column {
                            PodcastEpisodeRow(episode, tokens) {
                                LocalMusicPlayer.playEpisodes(context, show, episodes, index)
                            }
                            if (index < episodes.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.sheetLine))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastEpisodeRow(episode: PodcastEpisode, tokens: ColorTokens, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
    ) {
        Text(episode.title, color = tokens.fg, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Row {
            if (episode.publishedMillis > 0) {
                Text(feedAgo(episode.publishedMillis), color = tokens.fgDim, fontSize = 11.sp)
            }
            val duration = episode.durationMs
            if (duration != null && duration > 0) {
                Text(
                    (if (episode.publishedMillis > 0) " · " else "") + formatTrackDuration(duration),
                    color = tokens.fgDim,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// ---- Radio ---------------------------------------------------------------

/**
 * Search (via the free, no-key Radio-Browser directory,
 * [searchRadioStations]), browse by genre or language ([RADIO_GENRES]/
 * [RADIO_LANGUAGES], [stationsByTag]/[stationsByLanguage] — user-requested:
 * "can we show categories like genre, language etc."), and favorited
 * stations ([RadioFavoritesStore]) — the same shape as podcasts' search+
 * genre-browse+subscriptions, one level flatter since a station has no
 * episode list: tapping any row starts it playing immediately (a live
 * stream has nothing to "browse into" first).
 */
@Composable
private fun RadioPage(context: Context, accent: Color, tokens: ColorTokens) {
    val favorites by RadioFavoritesStore.favorites(context).collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf<RadioCategoryFilter?>(null) }
    var filterResults by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loadingFilter by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        activeFilter = null // a typed search takes over from a category browse
        searching = true
        delay(450)
        results = searchRadioStations(query)
        searching = false
    }

    LaunchedEffect(activeFilter) {
        val filter = activeFilter
        if (filter == null) {
            filterResults = emptyList()
            return@LaunchedEffect
        }
        loadingFilter = true
        filterResults = when (filter) {
            is RadioCategoryFilter.Genre -> stationsByTag(filter.tag)
            is RadioCategoryFilter.Language -> stationsByLanguage(filter.language)
        }
        loadingFilter = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibrarySearchField(query, { query = it }, "search radio stations", tokens)
        if (query.isBlank()) {
            CategoryChipRow(RADIO_GENRES, (activeFilter as? RadioCategoryFilter.Genre)?.tag, accent, tokens) { tag ->
                activeFilter = (RadioCategoryFilter.Genre(tag) as RadioCategoryFilter?).takeUnless { it == activeFilter }
            }
            CategoryChipRow(RADIO_LANGUAGES, (activeFilter as? RadioCategoryFilter.Language)?.language, accent, tokens) { lang ->
                activeFilter = (RadioCategoryFilter.Language(lang) as RadioCategoryFilter?).takeUnless { it == activeFilter }
            }
        }
        when {
            query.isNotBlank() && searching && results.isEmpty() -> LibraryEmptyState("searching…", tokens)
            query.isNotBlank() && results.isEmpty() -> LibraryEmptyState("no matches", tokens)
            query.isNotBlank() -> RadioStationList(results, favorites, context, accent, tokens)
            activeFilter != null && loadingFilter && filterResults.isEmpty() -> LibraryEmptyState("loading…", tokens)
            activeFilter != null && filterResults.isEmpty() -> LibraryEmptyState("no matches", tokens)
            activeFilter != null -> RadioStationList(filterResults, favorites, context, accent, tokens)
            favorites.isEmpty() -> LibraryEmptyState("search, or pick a category above, to find radio stations", tokens)
            else -> RadioStationList(favorites.map { it.toRadioStation() }, favorites, context, accent, tokens)
        }
    }
}

private sealed interface RadioCategoryFilter {
    data class Genre(val tag: String) : RadioCategoryFilter
    data class Language(val language: String) : RadioCategoryFilter
}

private fun FavoriteStation.toRadioStation() =
    RadioStation(stationId = stationId, name = name, streamUrl = streamUrl, faviconUrl = faviconUrl, country = "", tags = "")

@Composable
private fun RadioStationList(
    stations: List<RadioStation>,
    favorites: List<FavoriteStation>,
    context: Context,
    accent: Color,
    tokens: ColorTokens,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 32.dp),
    ) {
        stations.forEachIndexed { index, station ->
            val isFav = RadioFavoritesStore.isFavorite(favorites, station.stationId)
            RadioStationRow(
                name = station.name,
                faviconUrl = station.faviconUrl,
                subtitle = listOfNotNull(
                    station.country.takeIf { it.isNotBlank() },
                    station.tags.takeIf { it.isNotBlank() },
                ).takeIf { it.isNotEmpty() }?.joinToString(" · "),
                isFavorite = isFav,
                tokens = tokens,
                accent = accent,
                onClick = { LocalMusicPlayer.playStation(context, RadioStationRef(station)) },
                onToggleFavorite = {
                    if (isFav) {
                        RadioFavoritesStore.removeFavorite(context, station.stationId)
                    } else {
                        RadioFavoritesStore.addFavorite(
                            context,
                            FavoriteStation(
                                stationId = station.stationId,
                                name = station.name,
                                streamUrl = station.streamUrl,
                                faviconUrl = station.faviconUrl,
                                favoritedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                },
            )
            if (index < stations.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.sheetLine))
        }
    }
}

@Composable
private fun RadioStationRow(
    name: String,
    faviconUrl: String?,
    subtitle: String?,
    isFavorite: Boolean,
    tokens: ColorTokens,
    accent: Color,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = rememberRemoteArt(faviconUrl)
        if (art != null) {
            androidx.compose.foundation.Image(
                bitmap = art,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tokens.chip), contentAlignment = Alignment.Center) {
                Icon(TileIcons["music"], contentDescription = null, tint = tokens.fgDim, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, color = tokens.fgDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            TileIcons[if (isFavorite) "check" else "plus"],
            contentDescription = if (isFavorite) "unfavorite" else "favorite",
            tint = if (isFavorite) accent else tokens.fgDim,
            modifier = Modifier.size(20.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleFavorite,
            ),
        )
    }
}
