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
import kotlinx.coroutines.flow.map
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
 * **At most one entry per source** (external app package, or the local
 * library under [PlayedTrack.LOCAL_LIBRARY_MARKER]) — [record] replaces
 * whichever entry already existed for that source rather than appending, so
 * the list reads as "the last song played on each app," not a full log of
 * every play. This is a deliberate redesign, not the original shape: a
 * chronological per-play log meant tapping an *older* entry for an app that
 * had since moved on to a different track could only ever resume whatever
 * that app's session was CURRENTLY on — never the specific past track shown
 * — which read as "the wrong song plays" (user-reported: "only last played
 * song is playing irrespective of which song you were tapping"). Collapsing
 * to one row per source makes the row's own play control unambiguous: it
 * always reflects and controls that source's one real current session.
 * Ordered most-recently-played-source first, capped at [MAX]. Recorded by
 * [MusicHistoryEffect]; the same shape as [RecentApps] (`com.tileshell.core
 * .data`) minus a couple of fields.
 */
object MusicHistory {

    const val MAX = 20

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [record]'s one-per-source rule only applies going forward on write — a
     * file saved before that redesign (or by an older build) can still hold
     * several rows for the same source on disk. Deduped again here at read
     * time (keeping each source's first/most-recent occurrence — the stored
     * list is already most-recent-first) so already-persisted duplicates
     * collapse immediately too, not only once each source happens to play
     * again (user-reported: "it is still showing list in history" after the
     * one-row-per-source redesign, for exactly this reason). Also drops any
     * row keyed by [Context.getPackageName] itself: before the
     * `buildMediaState` fix in `MusicTile.kt`, TileShell's own
     * `LocalMusicPlaybackService` session was briefly indistinguishable from
     * a real external app's, so a local play could get double-recorded once
     * under [PlayedTrack.LOCAL_LIBRARY_MARKER] and once under this app's own
     * package (user-reported: "music tiles song is listed two times in
     * history") — filtered here too since that stray row, once persisted,
     * would otherwise never get replaced (nothing records under this app's
     * own package going forward). */
    fun history(context: Context): Flow<List<PlayedTrack>> {
        val ownPackage = context.applicationContext.packageName
        return context.applicationContext.musicHistoryStore.data
            .map { list -> list.filterNot { it.packageName == ownPackage }.distinctBy { it.packageName } }
    }

    /** Records [track] as the given source's latest play, replacing any earlier
     * entry for that same source (fire-and-forget). */
    fun record(context: Context, track: PlayedTrack) {
        val app = context.applicationContext
        writeScope.launch {
            app.musicHistoryStore.updateData { current ->
                (listOf(track) + current.filterNot { it.packageName == track.packageName }).take(MAX)
            }
        }
    }
}

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
            // TileShell's own session (local library/podcasts/radio, all
            // sharing LocalMusicPlaybackService) is excluded here, not from
            // MediaCenter itself anymore (see buildMediaState's own updated
            // doc comment) — local-library plays are already recorded below
            // from LocalMusicPlayer.state directly, and podcasts/radio
            // deliberately aren't recorded into this "one row per source"
            // history at all (see the comment on that second block) — either
            // way, this package's own now-playing must never fall into this
            // generic per-package loop, or it would double- or wrongly-record.
            if (pkg == context.packageName) return@forEach
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

    // Only a genuine local-library track is recorded here — a podcast
    // episode or radio station isn't a good fit for this "one row per
    // source" model (there's no single stable "source" identity the way an
    // app package is, and resuming a specific past episode/station belongs
    // in the podcasts/radio tabs' own subscription/favorites lists instead).
    val localPlayback by LocalMusicPlayer.state.collectAsState()
    var lastLocalTrackId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(localPlayback) {
        val track = (localPlayback.item as? PlayableAudio.Local)?.track
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
