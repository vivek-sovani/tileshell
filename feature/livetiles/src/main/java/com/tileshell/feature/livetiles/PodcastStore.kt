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

/** A podcast the user has subscribed to (persisted; not a search result). */
data class PodcastSubscription(
    val feedUrl: String,
    val title: String,
    val artworkUrl: String?,
    val subscribedAtMillis: Long,
)

/**
 * Subscribed-podcast list for the music hub's "podcasts" tab — just the
 * show's own identity (feed URL + display metadata), never its episodes:
 * episodes are always fetched fresh from [fetchPodcastFeed] when a show is
 * opened, so a subscription never goes stale the way a cached episode list
 * would. Ordered most-recently-subscribed first. Same pipe-delimited
 * DataStore codec shape as [MusicHistory].
 */
object PodcastStore {

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun subscriptions(context: Context): Flow<List<PodcastSubscription>> =
        context.applicationContext.podcastStore.data

    fun isSubscribed(subscriptions: List<PodcastSubscription>, feedUrl: String): Boolean =
        subscriptions.any { it.feedUrl == feedUrl }

    fun subscribe(context: Context, show: PodcastSubscription) {
        val app = context.applicationContext
        writeScope.launch {
            app.podcastStore.updateData { current ->
                listOf(show) + current.filterNot { it.feedUrl == show.feedUrl }
            }
        }
    }

    fun unsubscribe(context: Context, feedUrl: String) {
        val app = context.applicationContext
        writeScope.launch {
            app.podcastStore.updateData { current -> current.filterNot { it.feedUrl == feedUrl } }
        }
    }
}

/** Pipe-delimited codec, one line per [PodcastSubscription] (tolerant of
 * malformed lines) — pure, so it round-trips in a plain JUnit test. */
object PodcastSubscriptionCodec {

    fun encode(subs: List<PodcastSubscription>): String = subs.joinToString("\n") { sub ->
        "${clean(sub.feedUrl)}|${clean(sub.title)}|${clean(sub.artworkUrl.orEmpty())}|${sub.subscribedAtMillis}"
    }

    fun decode(text: String): List<PodcastSubscription> = text
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 4) return@mapNotNull null
            val subscribedAt = parts[3].toLongOrNull() ?: return@mapNotNull null
            PodcastSubscription(
                feedUrl = parts[0],
                title = parts[1],
                artworkUrl = parts[2].takeIf { it.isNotEmpty() },
                subscribedAtMillis = subscribedAt,
            )
        }
        .toList()

    private fun clean(value: String): String = value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ')
}

private object PodcastSubscriptionSerializer : Serializer<List<PodcastSubscription>> {
    override val defaultValue: List<PodcastSubscription> = emptyList()

    override suspend fun readFrom(input: InputStream): List<PodcastSubscription> =
        PodcastSubscriptionCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: List<PodcastSubscription>, output: OutputStream) {
        output.write(PodcastSubscriptionCodec.encode(t).encodeToByteArray())
    }
}

private val Context.podcastStore: DataStore<List<PodcastSubscription>> by dataStore(
    fileName = "podcast_subscriptions.pb",
    serializer = PodcastSubscriptionSerializer,
)
