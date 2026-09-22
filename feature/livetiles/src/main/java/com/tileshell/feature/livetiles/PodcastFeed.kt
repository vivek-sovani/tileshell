package com.tileshell.feature.livetiles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

/** One episode of a subscribed podcast, parsed from its RSS `<item>`. */
data class PodcastEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val durationMs: Long?,
    val publishedMillis: Long,
    val imageUrl: String?,
)

/** A subscribed show's feed, freshly fetched — the show's own metadata plus its
 * current episode list, in whatever order the feed itself publishes them
 * (almost always newest-first, by convention). */
data class PodcastShow(
    val feedUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val episodes: List<PodcastEpisode>,
)

/**
 * Fetches and parses a podcast's own RSS feed — the same mechanism as
 * [fetchPodcastFeed]'s sibling in `RssFeed.kt` (a plain `HttpURLConnection` +
 * hardened `DocumentBuilderFactory`, no third-party library), except an
 * episode is identified by its audio `<enclosure>`, not an image. Null on any
 * failure (unreachable, malformed XML, no title) — the caller degrades to
 * "couldn't load episodes" rather than crashing on a third-party feed that
 * can misbehave in any way.
 */
suspend fun fetchPodcastFeed(feedUrl: String): PodcastShow? = withContext(Dispatchers.IO) {
    val xml = httpGetText(feedUrl) ?: return@withContext null
    parsePodcastFeed(xml, feedUrl)
}

/** Pure XML parsing, split out from the network call so it's unit-testable
 * against a fixed RSS string with no network/Android dependency. */
fun parsePodcastFeed(xml: String, feedUrl: String): PodcastShow? = runCatching {
    val doc = DocumentBuilderFactory.newInstance()
        .apply {
            // Feeds are arbitrary third-party URLs — hardened the same way
            // RssFeed.kt's own parseFeed is, against untrusted XML.
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { isExpandEntityReferences = false }
            runCatching { isXIncludeAware = false }
        }
        .newDocumentBuilder()
        .parse(xml.byteInputStream())
    doc.documentElement?.normalize()

    val channel = doc.documentElement
    val title = podcastChildText(channel, "title")?.takeIf { it.isNotBlank() } ?: return@runCatching null
    val description = stripHtml(podcastChildText(channel, "description"))
    val channelImage = channelImageUrl(channel)

    val items = doc.getElementsByTagName("item")
    val episodes = (0 until items.length).mapNotNull { i ->
        (items.item(i) as? Element)?.let { episodeFrom(it) }
    }
    PodcastShow(feedUrl = feedUrl, title = title, description = description, imageUrl = channelImage, episodes = episodes)
}.getOrNull()

private fun episodeFrom(item: Element): PodcastEpisode? {
    val title = stripHtml(podcastChildText(item, "title"))
    if (title.isEmpty()) return null
    val audioUrl = audioEnclosureUrl(item) ?: return null
    val guid = podcastChildText(item, "guid")?.takeIf { it.isNotBlank() } ?: audioUrl
    val description = stripHtml(podcastChildText(item, "description") ?: podcastChildText(item, "itunes:summary"))
    val duration = parseItunesDuration(podcastChildText(item, "itunes:duration"))
    val published = parseFeedDate(podcastChildText(item, "pubDate")) ?: 0L
    val image = (item.getElementsByTagName("itunes:image").item(0) as? Element)
        ?.getAttribute("href")?.takeIf { it.isNotEmpty() }
    return PodcastEpisode(
        guid = guid,
        title = title,
        description = description,
        audioUrl = audioUrl,
        durationMs = duration,
        publishedMillis = published,
        imageUrl = image,
    )
}

/** The episode's own audio file — the first `<enclosure>` whose declared type
 * starts with `audio`, or (many feeds omit the type) whose url ends in a
 * common audio extension. Null when an item genuinely has no audio enclosure
 * (a text-only post some feeds mix in alongside real episodes). */
private fun audioEnclosureUrl(item: Element): String? {
    val enclosures = item.getElementsByTagName("enclosure")
    val audioExtension = Regex("""\.(mp3|m4a|aac|ogg|wav)(\?.*)?$""", RegexOption.IGNORE_CASE)
    for (i in 0 until enclosures.length) {
        val el = enclosures.item(i) as? Element ?: continue
        val type = el.getAttribute("type")
        val url = el.getAttribute("url").takeIf { it.isNotEmpty() } ?: continue
        if (type.isEmpty() || type.startsWith("audio") || audioExtension.containsMatchIn(url)) return url
    }
    return null
}

private fun channelImageUrl(channel: Element?): String? {
    channel ?: return null
    (channel.getElementsByTagName("itunes:image").item(0) as? Element)
        ?.getAttribute("href")?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    val imageNodes = channel.getElementsByTagName("image")
    for (i in 0 until imageNodes.length) {
        val el = imageNodes.item(i) as? Element ?: continue
        if (el.parentNode !== channel) continue // skip a per-item <image>, if any
        podcastChildText(el, "url")?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

/**
 * `itunes:duration`'s three allowed shapes — `"HH:MM:SS"`, `"MM:SS"`, or a
 * plain seconds count — whichever a given feed happens to use. Null when
 * absent or unparsable.
 */
fun parseItunesDuration(raw: String?): Long? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    if (value.all { it.isDigit() }) return value.toLongOrNull()?.times(1000L)
    val parts = value.split(":").map { it.toIntOrNull() }
    if (parts.isEmpty() || parts.size > 3 || parts.any { it == null }) return null
    var seconds = 0L
    for (p in parts) seconds = seconds * 60 + p!!
    return seconds * 1000L
}

private fun podcastChildText(parent: Element?, name: String): String? {
    parent ?: return null
    return parent.getElementsByTagName(name).item(0)?.textContent?.trim()
}

private suspend fun httpGetText(url: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) TileShell/1.0")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
