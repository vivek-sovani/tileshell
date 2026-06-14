package com.tileshell.feature.livetiles

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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

private val FaceText = Color.White
private const val POLL_MS = 2_000L
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
private fun nowPlayingMap(
    manager: MediaSessionManager,
    component: ComponentName,
): Map<String, NowPlaying> {
    val controllers = runCatching { manager.getActiveSessions(component) }.getOrNull().orEmpty()
    val out = LinkedHashMap<String, NowPlaying>()
    for (controller in controllers) {
        val pkg = controller.packageName ?: continue
        if (out.containsKey(pkg)) continue // keep the highest-priority session per app
        val md = controller.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        val np = nowPlayingFrom(title, artist, state) ?: continue
        out[pkg] = np
    }
    return out
}

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

    fun publish(map: Map<String, NowPlaying>) {
        _nowPlaying.value = map
    }

    fun clear() {
        _nowPlaying.value = emptyMap()
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
        val refresh = { MediaCenter.publish(nowPlayingMap(manager, component)) }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }
        val registered = runCatching {
            manager.addOnActiveSessionsChangedListener(listener, component)
        }.isSuccess
        refresh()
        onDispose {
            if (registered) runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
        }
    }
    LaunchedEffect(active, context) {
        if (!active) return@LaunchedEffect
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager ?: return@LaunchedEffect
        val component = ComponentName(context, TileNotificationListenerService::class.java)
        while (true) {
            MediaCenter.publish(nowPlayingMap(manager, component))
            delay(POLL_MS)
        }
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
) {
    val media by MediaCenter.nowPlaying.collectAsState()
    val np = if (packageName != null) {
        media[packageName]
    } else {
        media.values.firstOrNull { it.playing } ?: media.values.firstOrNull()
    }
    np ?: return fallback()

    FlipTile(
        flipped = flipped,
        modifier = modifier.fillMaxSize(),
        front = { MusicFront(np, animate = active && np.playing) },
        back = { MusicBack() },
    )
}

@Composable
private fun MusicFront(np: NowPlaying, animate: Boolean) {
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
        Text(text = "music", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun MusicBack() {
    Column(
        modifier = Modifier.fillMaxSize().padding(11.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "paused", color = FaceText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(text = "tap to resume", color = FaceText.copy(alpha = 0.82f), fontSize = 12.sp)
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
