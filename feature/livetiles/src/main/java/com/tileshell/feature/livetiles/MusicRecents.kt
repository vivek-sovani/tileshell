package com.tileshell.feature.livetiles

import android.content.Context
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

/** A podcast episode the hub itself played, for the podcasts tab's "recent"
 * section — carries everything needed to replay it without re-fetching the
 * show's feed first. */
data class RecentEpisode(
    val show: PodcastSubscription,
    val episode: PodcastEpisode,
    val playedAtMillis: Long,
)

/**
 * The last [MAX] podcast episodes and radio stations [LocalMusicPlayer]
 * actually started (recorded once playback is prepared, so a stream that
 * fails to load never lands here) — shown under favorites on the podcasts/
 * radio tabs. Separate from [MusicHistory] on purpose: that list is "one row
 * per source app," which has no sensible identity for an individual episode
 * or station. Most-recent first, one entry per episode guid / station id.
 * Radio recents reuse [FavoriteStation] and its codec as-is — the timestamp
 * field there just means "when it was last played" for this list.
 */
object MusicRecents {

    const val MAX = 10

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun episodes(context: Context): Flow<List<RecentEpisode>> =
        context.applicationContext.recentEpisodesStore.data

    fun stations(context: Context): Flow<List<FavoriteStation>> =
        context.applicationContext.recentStationsStore.data

    /** Records [item] if it's a podcast episode or radio station (a local
     * library track is [MusicHistory]'s job) — fire-and-forget. */
    fun record(context: Context, item: PlayableAudio) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        when (item) {
            is PlayableAudio.Episode -> writeScope.launch {
                app.recentEpisodesStore.updateData { current ->
                    pushRecent(current, RecentEpisode(item.show, item.episode, now)) { it.episode.guid }
                }
            }
            is PlayableAudio.RadioStream -> writeScope.launch {
                val s = item.station
                app.recentStationsStore.updateData { current ->
                    pushRecent(current, FavoriteStation(s.stationId, s.name, s.streamUrl, s.faviconUrl, now)) { it.stationId }
                }
            }
            is PlayableAudio.Local -> Unit
        }
    }
}

/** Puts [item] first, drops any earlier entry with the same [key], and caps
 * the list at [max] — pure, so it's unit-testable. */
fun <T> pushRecent(current: List<T>, item: T, max: Int = MusicRecents.MAX, key: (T) -> Any): List<T> {
    val k = key(item)
    return (listOf(item) + current.filterNot { key(it) == k }).take(max)
}

/** Pipe-delimited codec, one line per [RecentEpisode] (tolerant of malformed
 * lines) — pure, so it round-trips in a plain JUnit test. */
object RecentEpisodeCodec {

    private const val FIELDS = 10

    fun encode(items: List<RecentEpisode>): String = items.joinToString("\n") { r ->
        listOf(
            clean(r.show.feedUrl),
            clean(r.show.title),
            clean(r.show.artworkUrl.orEmpty()),
            clean(r.episode.guid),
            clean(r.episode.title),
            clean(r.episode.audioUrl),
            r.episode.durationMs?.toString().orEmpty(),
            r.episode.publishedMillis.toString(),
            clean(r.episode.imageUrl.orEmpty()),
            r.playedAtMillis.toString(),
        ).joinToString("|")
    }

    fun decode(text: String): List<RecentEpisode> = text
        .lineSequence()
        .mapNotNull { line ->
            val p = line.split('|')
            if (p.size != FIELDS) return@mapNotNull null
            val playedAt = p[9].toLongOrNull() ?: return@mapNotNull null
            if (p[0].isEmpty() || p[5].isEmpty()) return@mapNotNull null
            RecentEpisode(
                show = PodcastSubscription(
                    feedUrl = p[0],
                    title = p[1],
                    artworkUrl = p[2].takeIf { it.isNotEmpty() },
                    subscribedAtMillis = 0L,
                ),
                episode = PodcastEpisode(
                    guid = p[3],
                    title = p[4],
                    // Never persisted — show notes can be long and aren't
                    // shown anywhere a recent entry is rendered.
                    description = "",
                    audioUrl = p[5],
                    durationMs = p[6].toLongOrNull(),
                    publishedMillis = p[7].toLongOrNull() ?: 0L,
                    imageUrl = p[8].takeIf { it.isNotEmpty() },
                ),
                playedAtMillis = playedAt,
            )
        }
        .toList()

    private fun clean(value: String): String = value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ')
}

private object RecentEpisodeSerializer : Serializer<List<RecentEpisode>> {
    override val defaultValue: List<RecentEpisode> = emptyList()

    override suspend fun readFrom(input: InputStream): List<RecentEpisode> =
        RecentEpisodeCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: List<RecentEpisode>, output: OutputStream) {
        output.write(RecentEpisodeCodec.encode(t).encodeToByteArray())
    }
}

private object RecentStationSerializer : Serializer<List<FavoriteStation>> {
    override val defaultValue: List<FavoriteStation> = emptyList()

    override suspend fun readFrom(input: InputStream): List<FavoriteStation> =
        FavoriteStationCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: List<FavoriteStation>, output: OutputStream) {
        output.write(FavoriteStationCodec.encode(t).encodeToByteArray())
    }
}

private val Context.recentEpisodesStore: DataStore<List<RecentEpisode>> by dataStore(
    fileName = "podcast_recents.pb",
    serializer = RecentEpisodeSerializer,
)

private val Context.recentStationsStore: DataStore<List<FavoriteStation>> by dataStore(
    fileName = "radio_recents.pb",
    serializer = RecentStationSerializer,
)
