package com.tileshell.feature.livetiles

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.tileshell.core.design.LocalTileFaceColor
import com.tileshell.core.design.TileIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

private val FaceText: Color
    @Composable get() = LocalTileFaceColor.current
private const val POLL_MS = 3_000L
private const val EQ_STEP_MS = 240L

/**
 * What the music tile shows, reduced from the active [MediaController]. `null`
 * media (no controller / no notification access) degrades the tile to static.
 *
 * @property title the track title (front line).
 * @property artist the artist / album-artist (sub line).
 * @property playing whether playback is active — drives the EQ animation and
 *   selects the front (now playing) vs. back (paused) face.
 */
data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
)

/**
 * Builds a [NowPlaying] from raw media fields, or `null` when there is nothing
 * worth showing (no title and no artist). Pure so the title/artist fallbacks and
 * the playing flag are unit-testable without a live [MediaSession].
 *
 * [state] is a [PlaybackState] constant; playback counts as "playing" while it is
 * actively playing or buffering.
 */
fun nowPlayingFrom(title: String?, artist: String?, state: Int): NowPlaying? {
    val t = title?.trim().orEmpty()
    val a = artist?.trim().orEmpty()
    if (t.isEmpty() && a.isEmpty()) return null
    val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    return NowPlaying(title = t.ifEmpty { "now playing" }, artist = a, playing = playing)
}

/**
 * Builds the now-playing track for every active media session, keyed by the
 * owning package (highest-priority session per package wins; sessions with no
 * metadata are skipped). Returns empty when notification access is off (the
 * platform throws) or nothing is playing. The notification-listener [ComponentName]
 * is the access token — the same grant that powers badges/faces, so this needs no
 * extra permission.
 */
private fun buildMediaState(
    manager: MediaSessionManager,
    component: ComponentName,
): MediaState {
    val controllers = runCatching { manager.getActiveSessions(component) }.getOrNull().orEmpty()
    val np = LinkedHashMap<String, NowPlaying>()
    val ctrls = LinkedHashMap<String, MediaController>()
    val art = LinkedHashMap<String, Bitmap>()
    for (controller in controllers) {
        val pkg = controller.packageName ?: continue
        if (np.containsKey(pkg)) continue // keep the highest-priority session per app
        val md = controller.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        val now = nowPlayingFrom(title, artist, state) ?: continue
        np[pkg] = now
        ctrls[pkg] = controller
        // Album cover art (FR-2.3): prefer the full album art, then the smaller
        // display/art bitmaps; null when the session carries no artwork.
        val cover = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (cover != null) art[pkg] = cover
    }
    return MediaState(np, ctrls, art)
}

/** Per-package now-playing, live controllers and album art from the sessions. */
private data class MediaState(
    val now: Map<String, NowPlaying>,
    val controllers: Map<String, MediaController>,
    val artwork: Map<String, Bitmap>,
)

/**
 * Process-wide now-playing state, keyed by package, published by
 * [MediaSessionsEffect] and read by every music-capable tile (the dedicated music
 * tile and any app tile bound to its own package, e.g. Apple Music / YT Music).
 * One shared listener instead of one per tile. Empty when notification access is
 * off or nothing is playing.
 */
object MediaCenter {
    private val _nowPlaying = MutableStateFlow<Map<String, NowPlaying>>(emptyMap())
    val nowPlaying: StateFlow<Map<String, NowPlaying>> = _nowPlaying.asStateFlow()

    // Per-package album cover art, shown behind the music tile face (FR-2.3). A
    // separate flow from [nowPlaying] since bitmaps are framework objects.
    private val _artwork = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val artwork: StateFlow<Map<String, Bitmap>> = _artwork.asStateFlow()

    // The live controllers behind [nowPlaying], for the tile's transport buttons.
    // Refreshed on every publish; commands are guarded so a stale controller no-ops.
    @Volatile
    private var controllers: Map<String, MediaController> = emptyMap()

    fun publish(
        map: Map<String, NowPlaying>,
        controllers: Map<String, MediaController>,
        artwork: Map<String, Bitmap>,
    ) {
        _nowPlaying.value = map
        this.controllers = controllers
        _artwork.value = artwork
    }

    fun clear() {
        _nowPlaying.value = emptyMap()
        _artwork.value = emptyMap()
        controllers = emptyMap()
    }

    private fun controllerFor(packageName: String?): MediaController? = when {
        packageName != null -> controllers[packageName]
        else -> controllers.entries
            .firstOrNull { it.value.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?.value ?: controllers.values.firstOrNull()
    }

    /** Toggle play/pause on the session for [packageName] (or the playing one). */
    fun togglePlayPause(packageName: String?) {
        val controller = controllerFor(packageName) ?: return
        runCatching {
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) controller.transportControls.pause()
            else controller.transportControls.play()
        }
    }

    fun skipToNext(packageName: String?) {
        runCatching { controllerFor(packageName)?.transportControls?.skipToNext() }
    }

    fun skipToPrevious(packageName: String?) {
        runCatching { controllerFor(packageName)?.transportControls?.skipToPrevious() }
    }
}

/**
 * Binds the active media sessions into [MediaCenter] for the whole Start screen
 * (call once). A single [MediaSessionManager] listener catches session add/remove;
 * a light poll while [active] catches in-session track/playback changes that don't
 * fire the callback. All calls are guarded — denied access publishes an empty map,
 * so every music tile degrades rather than crashing.
 */
@Composable
fun MediaSessionsEffect(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager
        val component = ComponentName(context, TileNotificationListenerService::class.java)
        if (manager == null) {
            MediaCenter.clear()
            return@DisposableEffect onDispose { }
        }
        val handler = Handler(Looper.getMainLooper())
        // Per-controller callbacks give live play/pause + track-change updates even
        // when the poll is gated off (e.g. on the feed page) — the session-changed
        // listener alone only fires on session add/remove, not playback/metadata.
        val perController = mutableListOf<Pair<MediaController, MediaController.Callback>>()

        // Republish the snapshot without re-binding callbacks (playback/metadata tick).
        val publishNow = {
            val state = buildMediaState(manager, component)
            MediaCenter.publish(state.now, state.controllers, state.artwork)
        }
        // Re-read the session set, publish, and (re)register a callback on each
        // controller. Called on first run and whenever the session set changes.
        lateinit var rebind: () -> Unit
        rebind = {
            perController.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
            perController.clear()
            val state = buildMediaState(manager, component)
            MediaCenter.publish(state.now, state.controllers, state.artwork)
            state.controllers.values.forEach { controller ->
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(s: PlaybackState?) = publishNow()
                    override fun onMetadataChanged(m: MediaMetadata?) = publishNow()
                    override fun onSessionDestroyed() = rebind()
                }
                runCatching { controller.registerCallback(cb, handler) }
                perController.add(controller to cb)
            }
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { rebind() }
        val registered = runCatching {
            manager.addOnActiveSessionsChangedListener(listener, component)
        }.isSuccess
        rebind()
        onDispose {
            perController.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
            perController.clear()
            if (registered) runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
        }
    }
    LaunchedEffect(active, context) {
        if (!active) return@LaunchedEffect
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return@LaunchedEffect
        val component = ComponentName(context, TileNotificationListenerService::class.java)
        while (true) {
            val state = buildMediaState(manager, component)
            MediaCenter.publish(state.now, state.controllers, state.artwork)
            delay(POLL_MS)
        }
    }
}

/**
 * One-shot rebuild + publish of the media snapshot. Lets a surface that shows
 * now-playing but sits outside the live-tile gate (the feed page) keep itself
 * fresh by polling, since per-app `MediaController.Callback`s are unreliable on
 * some players. Guarded — a denied/absent manager just no-ops.
 */
fun refreshMediaSessions(context: Context) {
    val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
        as? MediaSessionManager ?: return
    val component = ComponentName(context, TileNotificationListenerService::class.java)
    runCatching {
        val state = buildMediaState(manager, component)
        MediaCenter.publish(state.now, state.controllers, state.artwork)
    }
}

/**
 * The live music tile (FR-2.3): front = animated EQ bars + track title/artist
 * (prototype `liveFace('music')`), back = "paused / tap to resume". Reads
 * [MediaCenter]. When [packageName] is set the tile shows *that app's* now-playing
 * (so an Apple Music / YT Music app tile shows its own song); when null it shows
 * whatever is currently playing (the dedicated music tile). EQ bars animate only
 * while playing and the live gate is [active]. With no matching session it renders
 * [fallback].
 */
@Composable
fun MusicTileFace(
    flipped: Boolean,
    active: Boolean,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    packageName: String? = null,
    // Whether the transport buttons accept taps. Separate from [active] (which
    // only gates the EQ animation) so play/pause/skip stay operable even when the
    // live-tile animation gate is held low (animations off, battery saver) — they
    // should only go inert in edit mode, where the grid owns all touch.
    interactive: Boolean = active,
) {
    val media by MediaCenter.nowPlaying.collectAsState()
    val artworkMap by MediaCenter.artwork.collectAsState()
    // The package whose now-playing this tile shows: the bound app for a music-app
    // tile (Apple Music / YT Music), else the source of the playing session for the
    // generic music tile — so its launcher icon can sit in the corner either way.
    val iconPackage: String?
    val np: NowPlaying?
    if (packageName != null) {
        iconPackage = packageName
        np = media[packageName]
    } else {
        val entry = media.entries.firstOrNull { it.value.playing } ?: media.entries.firstOrNull()
        iconPackage = entry?.key
        np = entry?.value
    }
    np ?: return fallback()
    val art = iconPackage?.let { artworkMap[it] }?.asImageBitmap()

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = {
            MusicFront(
                np,
                animate = active && np.playing,
                interactive = interactive,
                packageName = iconPackage,
                art = art,
            )
        },
        back = { MusicBack(packageName = iconPackage, art = art) },
    )
}

@Composable
private fun MusicFront(
    np: NowPlaying,
    animate: Boolean,
    interactive: Boolean,
    packageName: String?,
    art: ImageBitmap?,
) {
    TileImageBackground(art, modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(11.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            EqBars(animate = animate)
            Spacer(Modifier.height(7.dp))
            Text(
                text = np.title,
                color = FaceText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (np.artist.isNotEmpty()) {
                Text(
                    text = np.artist,
                    color = FaceText.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            // Transport controls — clickable only while the live gate is active
            // (so edit-mode drag/select isn't intercepted); they consume the tap,
            // so the tile doesn't also launch the app.
            MusicControls(
                playing = np.playing,
                enabled = interactive && packageName != null,
                packageName = packageName,
            )
        }
        // The playing app's own launcher icon, top-left (matches notification tiles).
        if (packageName != null) {
            AppIconCorner(
                packageName = packageName,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
    }
    }
}

@Composable
private fun MusicControls(playing: Boolean, enabled: Boolean, packageName: String?) {
    MediaTransportControls(
        playing = playing,
        packageName = packageName,
        tint = FaceText,
        enabled = enabled,
    )
}

/**
 * Previous / play-pause / next transport row driving the [MediaCenter] session for
 * [packageName] (or the active one when null). Reusable across the music tile and
 * the feed's now-playing card; [tint] colours the icons for the host surface.
 */
@Composable
fun MediaTransportControls(
    playing: Boolean,
    packageName: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ControlButton("prev", "previous", enabled, tint) { MediaCenter.skipToPrevious(packageName) }
        ControlButton(
            iconKey = if (playing) "pause" else "play",
            description = if (playing) "pause" else "play",
            enabled = enabled,
            tint = tint,
        ) { MediaCenter.togglePlayPause(packageName) }
        ControlButton("next", "next", enabled, tint) { MediaCenter.skipToNext(packageName) }
    }
}

@Composable
private fun ControlButton(
    iconKey: String,
    description: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TileIcons[iconKey],
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun MusicBack(packageName: String?, art: ImageBitmap?) {
    TileImageBackground(art, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(11.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "paused", color = FaceText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = "tap to resume", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp)
            }
            if (packageName != null) {
                AppIconCorner(
                    packageName = packageName,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
        }
    }
}

private const val EQ_BARS = 5

/**
 * Five equaliser bars (prototype `.eqbars`). While [animate], each bar's height
 * steps to a fresh random level every [EQ_STEP_MS] and tweens there, giving the
 * bouncing-EQ look; when paused / off-screen the bars settle to a flat baseline so
 * an idle launcher does no per-frame work.
 */
@Composable
private fun EqBars(animate: Boolean) {
    var levels by remember { mutableStateOf(List(EQ_BARS) { 0.35f }) }
    LaunchedEffect(animate) {
        if (!animate) {
            levels = List(EQ_BARS) { 0.3f }
            return@LaunchedEffect
        }
        while (true) {
            levels = List(EQ_BARS) { 0.3f + Random.nextFloat() * 0.7f }
            delay(EQ_STEP_MS)
        }
    }
    Row(
        modifier = Modifier.height(22.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        levels.forEach { level ->
            val h by animateFloatAsState(targetValue = level, animationSpec = tween(180), label = "eq")
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(FaceText),
            )
        }
    }
}
