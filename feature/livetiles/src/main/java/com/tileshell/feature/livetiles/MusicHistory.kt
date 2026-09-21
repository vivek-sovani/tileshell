package com.tileshell.feature.livetiles

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
)

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
 * Watches [MediaCenter.nowPlaying] for the whole app session (call once, e.g.
 * alongside [MediaSessionsEffect]) and records each package's track into
 * [MusicHistory] the moment it starts *playing* (not merely present/paused —
 * a session that never plays, e.g. one the OS restored on boot, shouldn't
 * count as "played"). Tracked per-package so two apps playing in quick
 * succession both get recorded, not just the last one published.
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
}

/**
 * Pipe-delimited codec, one line per [PlayedTrack] (tolerant of malformed
 * lines) — pure, so it round-trips in a plain JUnit test without a real
 * DataStore.
 */
object PlayedTrackCodec {

    fun encode(tracks: List<PlayedTrack>): String = tracks.joinToString("\n") { track ->
        "${clean(track.title)}|${clean(track.artist)}|${clean(track.packageName)}|${track.playedAtMillis}"
    }

    fun decode(text: String): List<PlayedTrack> = text
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 4) return@mapNotNull null
            val playedAt = parts[3].toLongOrNull() ?: return@mapNotNull null
            PlayedTrack(title = parts[0], artist = parts[1], packageName = parts[2], playedAtMillis = playedAt)
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
