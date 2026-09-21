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
import kotlinx.coroutines.launch

private val HUB_PIVOTS = listOf("now playing", "library", "apps", "history")

private val LOCAL_AUDIO_PERMISSION: String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Full-screen music hub — same Panorama/Pivot shell as [WeatherHubScreen]:
 * fixed header, a tappable/swipeable pivot row, four independently-scrolling
 * pages ("now playing" / "library" / "apps" / "history"). The destination for
 * tapping the music tile.
 *
 * "apps" lists every installed music/audio player ([AppCategories.isMusicApp]
 * — the OS-resolved music role plus the declared `CATEGORY_AUDIO`, both
 * already surfaced by [AppCatalogRepository] with no extra permission) so the
 * hub covers every player on the device, not just whichever one last had an
 * active session. "library" browses on-device tracks/albums/playlists
 * ([LocalMusicLibrary]) and plays them in-hub only ([LocalMusicPlayer] — no
 * background playback, released when this screen closes). "history" is
 * [MusicHistory] — tracks TileShell itself has seen play; there's no system
 * "recently played" API to read instead. "now playing" reads the same
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

    // Stops in-hub local playback the moment this screen actually leaves
    // composition (not merely `visible = false` — it stays mounted through
    // the exit animation above, same as every other sheet here).
    DisposableEffect(Unit) { onDispose { LocalMusicPlayer.release() } }

    val tokens = colorTokens(dark)
    val accent = TileAccents.forId(accentId)
    val context = LocalContext.current

    BackHandler(enabled = visible) { onDismiss() }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { HUB_PIVOTS.size })
    val pagerScope = rememberCoroutineScope()
    val localPlayback by LocalMusicPlayer.state.collectAsState()
    val externalMedia by MediaCenter.nowPlaying.collectAsState()
    val anyPlaying = localPlayback.playing || externalMedia.values.any { it.playing }

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

            // Jumps to "now playing" the moment a track actually starts —
            // covers both a fresh tap in the library and an auto-advance to
            // the next queued track. User-requested; replaces the persistent
            // bottom playback bar that used to show across every page (now
            // redundant — "now playing" itself shows this prominently).
            LaunchedEffect(localPlayback.track?.id) {
                if (localPlayback.track != null) pagerState.animateScrollToPage(0)
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> NowPlayingPage(accent, tokens)
                    1 -> LibraryPage(context, accent, tokens)
                    2 -> MusicAppsPage(context, accent, tokens)
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
private fun LocalPlaybackButton(iconKey: String, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(TileIcons[iconKey], contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
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
private fun NowPlayingPage(accent: Color, tokens: ColorTokens) {
    val media by MediaCenter.nowPlaying.collectAsState()
    val artworkMap by MediaCenter.artwork.collectAsState()
    val externalEntry = media.entries.firstOrNull { it.value.playing } ?: media.entries.firstOrNull()
    val localPlayback by LocalMusicPlayer.state.collectAsState()
    val localTrack = localPlayback.track
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        when {
            externalEntry != null && (externalEntry.value.playing || localTrack == null) ->
                ExternalNowPlaying(externalEntry, artworkMap, accent, tokens, context)
            localTrack != null -> LocalNowPlaying(localTrack, localPlayback.playing, accent, tokens, context)
            else -> Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Text("nothing playing", color = tokens.fgDim, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "start something in one of your music apps, or play a track from the library",
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
        MediaTransportControls(playing = np.playing, packageName = packageName, tint = tokens.fg, enabled = true)
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

@Composable
private fun LocalNowPlaying(track: LocalTrack, playing: Boolean, accent: Color, tokens: ColorTokens, context: Context) {
    // A full-width square hero needs a much larger request than a list
    // thumbnail — 600px upscaled across a ~1080px-wide screen was visibly
    // blurry. 1024 is generous enough to not be our own bottleneck; the
    // actual sharpness ceiling beyond that is whatever resolution the
    // track's embedded art actually has.
    val art = rememberLocalAlbumArt(context, track.albumId, sizePx = 1024)
    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
        NowPlayingHero(art, accent)
        Spacer(Modifier.height(14.dp))
        Text(text = track.title, color = tokens.fg, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (track.artist.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(text = track.artist, color = tokens.fgDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LocalPlaybackButton("prev", "previous", tokens.fg) { LocalMusicPlayer.previous(context) }
            LocalPlaybackButton(if (playing) "pause" else "play", "play/pause", tokens.fg) {
                LocalMusicPlayer.togglePlayPause()
            }
            LocalPlaybackButton("next", "next", tokens.fg) { LocalMusicPlayer.next(context) }
        }
        Spacer(Modifier.height(20.dp))
        Text("playing from your library", color = tokens.fgDim, fontSize = 12.sp)
    }
}

@Composable
private fun MusicAppsPage(context: Context, accent: Color, tokens: ColorTokens) {
    val repository = remember(context) { AppCatalogRepository(context) }
    val apps by repository.apps.collectAsState(initial = emptyList())
    val musicApps = remember(apps) { apps.filter { AppCategories.isMusicApp(it) } }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 32.dp),
    ) {
        history.forEachIndexed { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        // A locally-played track has no real app to open.
                        if (track.isLocal) {
                            base
                        } else {
                            base.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { openApp(context, track.packageName) },
                            )
                        }
                    }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (track.isLocal) {
                    // High-contrast accent plate, matching MusicAppCell's own
                    // no-icon fallback — the earlier muted fgDim/bg combo read
                    // as blank next to other rows' real app icons (user-reported).
                    Box(Modifier.size(28.dp).background(accent), contentAlignment = Alignment.Center) {
                        Icon(TileIcons["music"], contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                } else {
                    val icon = rememberAppIconBitmap(track.packageName, sizePx = 64)
                    if (icon != null) {
                        androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, color = tokens.fg, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (track.artist.isNotEmpty()) {
                        Text(track.artist, color = tokens.fgDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(feedAgo(track.playedAtMillis), color = tokens.fgDim, fontSize = 11.sp)
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
            LibraryMode.TRACKS -> TracksList(context, tokens, query)
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
private fun TracksList(context: Context, tokens: ColorTokens, query: String) {
    val tracks by produceState<List<LocalTrack>?>(initialValue = null, context) {
        value = LocalMusicLibrary.tracks(context)
    }
    val list = remember(tracks, query) {
        tracks?.let { all -> if (query.isBlank()) all else all.filter { it.title.contains(query, ignoreCase = true) } }
    }
    when {
        list == null -> LibraryEmptyState("loading your library…", tokens)
        list.isEmpty() -> LibraryEmptyState(if (query.isBlank()) "no tracks found" else "no matches", tokens)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            itemsIndexed(list, key = { _, track -> track.id }) { index, track ->
                TrackRow(track, tokens) { LocalMusicPlayer.playQueue(context, list, index) }
            }
        }
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
