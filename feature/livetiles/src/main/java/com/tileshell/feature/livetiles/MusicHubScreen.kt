package com.tileshell.feature.livetiles

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    SheetStage(rightHalf = rightHalf, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = size.height * (1f - progress) }
                .background(tokens.bg)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
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
                Text(text = "tileshell", color = tokens.fgDim, fontSize = 12.sp)
                Text(
                    text = "music",
                    color = accent,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
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

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> NowPlayingPage(accent, tokens)
                    1 -> LibraryPage(context, accent, tokens)
                    2 -> MusicAppsPage(context, accent, tokens)
                    else -> HistoryPage(context, tokens)
                }
            }

            // Local-library playback survives navigating between pivot pages
            // (it's a single shared player, not page-scoped), so its controls
            // stay visible here regardless of which page is showing.
            localPlayback.track?.let { track ->
                LocalPlaybackBar(track, localPlayback.playing, context, tokens, accent)
            }
        }
    }
}

@Composable
private fun LocalPlaybackBar(
    track: LocalTrack,
    playing: Boolean,
    context: Context,
    tokens: ColorTokens,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.sheet)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = tokens.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.artist.isNotEmpty()) {
                Text(track.artist, color = tokens.fgDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LocalPlaybackButton("prev", "previous", tokens.fg) { LocalMusicPlayer.previous(context) }
            LocalPlaybackButton(if (playing) "pause" else "play", "play/pause", tokens.fg) {
                LocalMusicPlayer.togglePlayPause()
            }
            LocalPlaybackButton("next", "next", tokens.fg) { LocalMusicPlayer.next(context) }
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

@Composable
private fun NowPlayingPage(accent: Color, tokens: ColorTokens) {
    val media by MediaCenter.nowPlaying.collectAsState()
    val artworkMap by MediaCenter.artwork.collectAsState()
    val entry = media.entries.firstOrNull { it.value.playing } ?: media.entries.firstOrNull()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 32.dp),
    ) {
        if (entry == null) {
            Text("nothing playing", color = tokens.fgDim, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "start something in one of your music apps",
                color = tokens.fgDim,
                fontSize = 12.sp,
            )
            return@Column
        }
        val (packageName, np) = entry
        TileImageBackground(
            image = artworkMap[packageName]?.asImageBitmap(),
            modifier = Modifier.fillMaxWidth().height(220.dp),
        ) {
            if (artworkMap[packageName] == null) {
                Box(Modifier.fillMaxSize().background(accent), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = TileIcons["music"],
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(text = np.title, color = tokens.fg, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (np.artist.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(text = np.artist, color = tokens.fgDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        MediaTransportControls(
            playing = np.playing,
            packageName = packageName,
            tint = tokens.fg,
            enabled = true,
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
                androidx.compose.foundation.Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text("playing from $label", color = tokens.fgDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MusicAppsPage(context: Context, accent: Color, tokens: ColorTokens) {
    val repository = remember(context) { AppCatalogRepository(context) }
    val apps by repository.apps.collectAsState(initial = emptyList())
    val musicApps = remember(apps) { apps.filter { AppCategories.isMusicApp(it) } }

    if (musicApps.isEmpty()) {
        Text(
            "no music apps found",
            color = tokens.fgDim,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        items(musicApps, key = { it.key }) { app ->
            MusicAppCell(app, accent, tokens) { AppLauncher.launch(context, app.packageName, app.activityName) }
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
private fun HistoryPage(context: Context, tokens: ColorTokens) {
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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { openApp(context, track.packageName) },
                    )
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = rememberAppIconBitmap(track.packageName, sizePx = 64)
                if (icon != null) {
                    androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
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

@Composable
private fun LibraryPage(context: Context, accent: Color, tokens: ColorTokens) {
    val granted = rememberPermissionGranted(LOCAL_AUDIO_PERMISSION)
    if (!granted) {
        LibraryPermissionGate(tokens, accent)
        return
    }
    var mode by remember { mutableStateOf(LibraryMode.TRACKS) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LibraryModeLabel("tracks", mode == LibraryMode.TRACKS, tokens) { mode = LibraryMode.TRACKS }
            LibraryModeLabel("albums", mode == LibraryMode.ALBUMS, tokens) { mode = LibraryMode.ALBUMS }
            LibraryModeLabel("playlists", mode == LibraryMode.PLAYLISTS, tokens) { mode = LibraryMode.PLAYLISTS }
        }
        when (mode) {
            LibraryMode.TRACKS -> TracksList(context, tokens)
            LibraryMode.ALBUMS -> AlbumsGrid(context, accent, tokens)
            LibraryMode.PLAYLISTS -> PlaylistsList(context, tokens)
        }
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
private fun TracksList(context: Context, tokens: ColorTokens) {
    val tracks by produceState<List<LocalTrack>?>(initialValue = null, context) {
        value = LocalMusicLibrary.tracks(context)
    }
    val list = tracks
    when {
        list == null -> LibraryEmptyState("loading your library…", tokens)
        list.isEmpty() -> LibraryEmptyState("no tracks found", tokens)
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
private fun AlbumsGrid(context: Context, accent: Color, tokens: ColorTokens) {
    val albums by produceState<List<LocalAlbum>?>(initialValue = null, context) {
        value = LocalMusicLibrary.albums(context)
    }
    val list = albums
    val scope = rememberCoroutineScope()
    when {
        list == null -> LibraryEmptyState("loading your library…", tokens)
        list.isEmpty() -> LibraryEmptyState("no albums found", tokens)
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(list, key = { it.id }) { album ->
                AlbumCell(album, accent, tokens) {
                    scope.launch {
                        val tracks = LocalMusicLibrary.tracksForAlbum(context, album.id)
                        if (tracks.isNotEmpty()) LocalMusicPlayer.playQueue(context, tracks, 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCell(album: LocalAlbum, accent: Color, tokens: ColorTokens, onClick: () -> Unit) {
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
        Box(Modifier.size(64.dp).background(accent), contentAlignment = Alignment.Center) {
            Icon(TileIcons["music"], contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(album.title, color = tokens.fg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${album.trackCount} tracks", color = tokens.fgDim, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun PlaylistsList(context: Context, tokens: ColorTokens) {
    val playlists by produceState<List<LocalPlaylist>?>(initialValue = null, context) {
        value = LocalMusicLibrary.playlists(context)
    }
    val list = playlists
    val scope = rememberCoroutineScope()
    when {
        list == null -> LibraryEmptyState("loading your library…", tokens)
        // Genuinely common, not a bug: most apps stopped writing playlists
        // through this legacy provider once scoped storage landed, so an
        // empty result here just means none of this device's apps used it.
        list.isEmpty() -> LibraryEmptyState("no playlists found on this device", tokens)
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
                            onClick = {
                                scope.launch {
                                    val tracks = LocalMusicLibrary.tracksForPlaylist(context, playlist.id)
                                    if (tracks.isNotEmpty()) LocalMusicPlayer.playQueue(context, tracks, 0)
                                }
                            },
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
