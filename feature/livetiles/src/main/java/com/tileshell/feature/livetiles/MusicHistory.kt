package com.tileshell.feature.livetiles

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

/** One track TileShell itself saw play, for the music hub's "history" page. */
data class PlayedTrack(
    val title: String,
    val artist: String,
    val packageName: String,
    val playedAtMillis: Long,
    /** [LocalTrack.id] for a local-library entry, so tapping it can replay the
     * actual file — null for an external-app entry (nothing to replay) and for
     * a local entry recorded before this field existed. */
    val localTrackId: Long? = null,
) {
    /** True for a track played through the hub's own local library, not an external app. */
    val isLocal: Boolean get() = packageName == LOCAL_LIBRARY_MARKER

    companion object {
        /**
         * [packageName] sentinel for a locally-played track — there's no real
         * app package to record (and nothing to "open" from a history row for
         * one), so this marks it distinctly rather than reusing TileShell's
         * own package or an empty string, either of which could collide with
         * a real value or read as "unknown".
         */
        const val LOCAL_LIBRARY_MARKER = "tileshell.local"
    }
}

/**
 * Locally-recorded play history for the music hub (there's no system "recently
 * played" API to read, and reading a music library needs `READ_MEDIA_AUDIO` —
 * this needs neither: it just remembers what [MediaCenter] already showed us).
 * Ordered most-recent first, capped at [MAX], de-duped against its own most
 * recent entry so a metadata-churn republish of the *same* track doesn't spam
 * the list. Recorded by [MusicHistoryEffect]; the same shape as [RecentApps]
 * (`com.tileshell.core.data`) minus a couple of fields.
 */
object MusicHistory {

    const val MAX = 20

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun history(context: Context): Flow<List<PlayedTrack>> =
        context.applicationContext.musicHistoryStore.data

    /** Record a track starting to play (fire-and-forget). */
    fun record(context: Context, track: PlayedTrack) {
        val app = context.applicationContext
        writeScope.launch {
            app.musicHistoryStore.updateData { current ->
                if (current.firstOrNull()?.isSameTrack(track) == true) {
                    current
                } else {
                    (listOf(track) + current).take(MAX)
                }
            }
        }
    }
}

private fun PlayedTrack.isSameTrack(other: PlayedTrack): Boolean =
    title == other.title && artist == other.artist && packageName == other.packageName

/**
 * Watches both playback sources for the whole app session (call once, e.g.
 * alongside [MediaSessionsEffect]) and records into [MusicHistory] the
 * moment each starts *playing* (not merely present/paused — a session that
 * never plays, e.g. one the OS restored on boot, shouldn't count as
 * "played"): [MediaCenter.nowPlaying] for other apps' own sessions, tracked
 * per-package so two apps playing in quick succession both get recorded, not
 * just the last one published; and [LocalMusicPlayer.state] for the music
 * hub's own in-hub library playback — a real gap, once, since these are two
 * entirely separate mechanisms with no overlap otherwise (user-reported:
 * "songs played through music hub not added in history").
 */
@Composable
fun MusicHistoryEffect() {
    val context = LocalContext.current
    val media by MediaCenter.nowPlaying.collectAsState()
    val lastRecorded = remember { HashMap<String, NowPlaying>() }
    LaunchedEffect(media) {
        media.forEach { (pkg, np) ->
            if (np.playing && lastRecorded[pkg] != np) {
                lastRecorded[pkg] = np
                MusicHistory.record(
                    context,
                    PlayedTrack(
                        title = np.title,
                        artist = np.artist,
                        packageName = pkg,
                        playedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    val localPlayback by LocalMusicPlayer.state.collectAsState()
    var lastLocalTrackId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(localPlayback) {
        val track = localPlayback.track
        if (localPlayback.playing && track != null && lastLocalTrackId != track.id) {
            lastLocalTrackId = track.id
            MusicHistory.record(
                context,
                PlayedTrack(
                    title = track.title,
                    artist = track.artist,
                    packageName = PlayedTrack.LOCAL_LIBRARY_MARKER,
                    playedAtMillis = System.currentTimeMillis(),
                    localTrackId = track.id,
                ),
            )
        }
    }
}

/**
 * Pipe-delimited codec, one line per [PlayedTrack] (tolerant of malformed
 * lines) — pure, so it round-trips in a plain JUnit test without a real
 * DataStore. The trailing [PlayedTrack.localTrackId] field is optional on
 * decode (blank or altogether missing → null) so a history file written
 * before that field existed still loads fine.
 */
object PlayedTrackCodec {

    fun encode(tracks: List<PlayedTrack>): String = tracks.joinToString("\n") { track ->
        "${clean(track.title)}|${clean(track.artist)}|${clean(track.packageName)}|${track.playedAtMillis}|${track.localTrackId ?: ""}"
    }

    fun decode(text: String): List<PlayedTrack> = text
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 4) return@mapNotNull null
            val playedAt = parts[3].toLongOrNull() ?: return@mapNotNull null
            val localTrackId = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }?.toLongOrNull()
            PlayedTrack(
                title = parts[0],
                artist = parts[1],
                packageName = parts[2],
                playedAtMillis = playedAt,
                localTrackId = localTrackId,
            )
        }
        .toList()

    private fun clean(value: String): String = value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ')
}

private object PlayedTrackSerializer : Serializer<List<PlayedTrack>> {
    override val defaultValue: List<PlayedTrack> = emptyList()

    override suspend fun readFrom(input: InputStream): List<PlayedTrack> =
        PlayedTrackCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: List<PlayedTrack>, output: OutputStream) {
        output.write(PlayedTrackCodec.encode(t).encodeToByteArray())
    }
}

private val Context.musicHistoryStore: DataStore<List<PlayedTrack>> by dataStore(
    fileName = "music_history.pb",
    serializer = PlayedTrackSerializer,
)
