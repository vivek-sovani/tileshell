package com.tileshell.feature.livetiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

/**
 * Persisted news-feed state (left feed discover section): the subscribed [sources]
 * and the last fetched [articles] cache. Seeded with [DEFAULT_FEED_SOURCES] until
 * the user edits the list. Kept in its own DataStore (mirroring WeatherCache) so
 * the feature is self-contained.
 */
data class FeedData(
    val sources: List<FeedSource> = DEFAULT_FEED_SOURCES,
    val articles: List<FeedArticle> = emptyList(),
)

/**
 * Tab-delimited, line-oriented codec for [FeedData], mirroring the project's other
 * flat codecs (DECISIONS S17/S21): pure, JVM-testable, tolerant — malformed lines
 * are skipped. `S` lines are sources (`S<TAB>enabled<TAB>url<TAB>name`); `A` lines
 * are cached articles. Field values have tabs/newlines stripped on encode so each
 * record stays on one line and splits back cleanly.
 */
object FeedCodec {
    private const val TAB = "\t"

    private fun clean(s: String?): String = s.orEmpty().replace(Regex("[\\t\\n\\r]"), " ").trim()

    fun encode(data: FeedData): String = buildString {
        data.sources.forEach { s ->
            append("S").append(TAB).append(if (s.enabled) "1" else "0")
                .append(TAB).append(clean(s.url)).append(TAB).append(clean(s.name))
                .append(TAB).append(clean(s.category)).append('\n')
        }
        data.articles.forEach { a ->
            append("A").append(TAB).append(clean(a.title)).append(TAB).append(clean(a.link))
                .append(TAB).append(clean(a.source)).append(TAB).append(clean(a.tag))
                .append(TAB).append(clean(a.imageUrl)).append(TAB).append(a.publishedAtMillis)
                .append('\n')
        }
    }

    fun decode(text: String): FeedData {
        val sources = ArrayList<FeedSource>()
        val articles = ArrayList<FeedArticle>()
        text.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val f = line.split(TAB)
            when (f.getOrNull(0)) {
                "S" -> if (f.size >= 4 && f[2].isNotEmpty()) {
                    val url = f[2]
                    // Backfill the category for sources stored before categories
                    // existed (match a known default by url, else "custom").
                    val category = f.getOrNull(4)?.takeIf { it.isNotEmpty() }
                        ?: DEFAULT_FEED_SOURCES.firstOrNull { it.url == url }?.category
                        ?: CUSTOM_CATEGORY
                    sources.add(FeedSource(url = url, name = f[3], category = category, enabled = f[1] == "1"))
                }
                "A" -> if (f.size >= 7 && f[1].isNotEmpty()) {
                    articles.add(
                        FeedArticle(
                            title = f[1],
                            link = f[2],
                            source = f[3],
                            tag = f[4],
                            imageUrl = f[5].ifEmpty { null },
                            publishedAtMillis = f[6].toLongOrNull() ?: 0L,
                        ),
                    )
                }
            }
        }
        return FeedData(sources = sources, articles = articles)
    }
}

private object FeedSerializer : Serializer<FeedData> {
    override val defaultValue = FeedData()

    override suspend fun readFrom(input: InputStream): FeedData =
        FeedCodec.decode(input.readBytes().decodeToString())

    override suspend fun writeTo(t: FeedData, output: OutputStream) {
        output.write(FeedCodec.encode(t).encodeToByteArray())
    }
}

private val Context.feedDataStore: DataStore<FeedData> by dataStore(
    fileName = "news_feed.pb",
    serializer = FeedSerializer,
)

/** Reads/writes the news-feed sources and article cache. Own DataStore file. */
class FeedStore(private val store: DataStore<FeedData>) {

    val data: Flow<FeedData> = store.data

    suspend fun read(): FeedData = store.data.first()

    suspend fun setArticles(articles: List<FeedArticle>) {
        store.updateData { it.copy(articles = articles) }
    }

    suspend fun addSource(url: String, name: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        store.updateData { current ->
            if (current.sources.any { it.url.equals(cleanUrl, ignoreCase = true) }) {
                current
            } else {
                current.copy(
                    sources = current.sources + FeedSource(cleanUrl, name.trim().ifEmpty { cleanUrl }),
                )
            }
        }
    }

    suspend fun removeSource(url: String) {
        store.updateData { it.copy(sources = it.sources.filterNot { s -> s.url == url }) }
    }

    suspend fun setEnabled(url: String, enabled: Boolean) {
        store.updateData {
            it.copy(sources = it.sources.map { s -> if (s.url == url) s.copy(enabled = enabled) else s })
        }
    }

    /** Enable/disable every feed in [category] at once (the category toggle). */
    suspend fun setCategoryEnabled(category: String, enabled: Boolean) {
        store.updateData {
            it.copy(sources = it.sources.map { s -> if (s.category == category) s.copy(enabled = enabled) else s })
        }
    }

    /**
     * Adds any [DEFAULT_FEED_SOURCES] not already present (by url) so default feeds
     * introduced in a newer version appear in existing installs (DataStore keeps the
     * first-seen list and never picks up new defaults on its own). Existing feeds —
     * including the user's enable/disable choices and custom feeds — are untouched.
     */
    suspend fun reconcileDefaults() {
        store.updateData { current ->
            // Drop former-default feeds removed in a newer version, then add any
            // current defaults not yet present (by url).
            val kept = current.sources.filterNot { it.url in DEPRECATED_FEED_URLS }
            val present = kept.mapTo(HashSet()) { it.url }
            val missing = DEFAULT_FEED_SOURCES.filterNot { it.url in present }
            val next = kept + missing
            if (next == current.sources) current else current.copy(sources = next)
        }
    }

    companion object {
        fun create(context: Context): FeedStore =
            FeedStore(context.applicationContext.feedDataStore)
    }
}
