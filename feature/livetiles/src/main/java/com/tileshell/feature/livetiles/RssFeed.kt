package com.tileshell.feature.livetiles

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One parsed article from a news feed (FR — left feed discover section). Pure data
 * so the parser and formatters are JVM-unit-testable without Android.
 */
data class FeedArticle(
    val title: String,
    val link: String,
    val source: String,
    val tag: String,
    val imageUrl: String?,
    val publishedAtMillis: Long,
)

/**
 * A subscribed news feed: its [url], a display [name], the [category] it belongs to
 * (for category-level selection in personalize), and whether it is currently
 * [enabled]. Disabled feeds are kept (so the toggle is reversible) but skipped on
 * refresh. User-added feeds use the [CUSTOM_CATEGORY].
 */
data class FeedSource(
    val url: String,
    val name: String,
    val category: String = CUSTOM_CATEGORY,
    val enabled: Boolean = true,
)

const val CUSTOM_CATEGORY = "custom"

/**
 * Default feeds removed in a newer version. `reconcileDefaults` drops these from
 * existing installs (DataStore keeps the first-seen list, so a dropped default
 * would otherwise linger). Only ever list *former defaults* here — never a URL a
 * user might add themselves.
 */
val DEPRECATED_FEED_URLS: Set<String> = setOf(
    "https://indianexpress.com/feed/",
    "https://timesofindia.indiatimes.com/rssfeeds/-2128932452.cms", // generic "TOI City"
    "https://feeds.feedburner.com/ndtvnews-cities-news", // generic "NDTV Cities"
)

/**
 * Selectable news categories, in display order (the user toggles whole categories in
 * personalize). [CUSTOM_CATEGORY] is handled separately (per-feed add/remove).
 */
val FEED_CATEGORIES: List<String> = listOf(
    "nation", "state", "entertainment", "cricket", "sports", "tech", "business", "food",
)

/**
 * The India-specific default feeds, tagged by [FEED_CATEGORIES]. Free RSS/Atom, no
 * API key, all verified reachable. The refresh worker tolerates a dead feed (skips
 * it, keeps the rest). A sensible subset is enabled out of the box; the rest are
 * opt-in via the category toggles.
 */
val DEFAULT_FEED_SOURCES: List<FeedSource> = listOf(
    FeedSource("https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en", "Google News", "nation", enabled = true),
    FeedSource("https://www.thehindu.com/news/national/feeder/default.rss", "The Hindu", "nation", enabled = true),
    FeedSource("https://feeds.feedburner.com/ndtvnews-top-stories", "NDTV", "nation", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeedstopstories.cms", "TOI Top Stories", "nation", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/-2128821991.cms", "TOI Pune", "state", enabled = false),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/-2128838597.cms", "TOI Mumbai", "state", enabled = false),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/442002.cms", "TOI Nagpur", "state", enabled = false),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/-2128839596.cms", "TOI Delhi", "state", enabled = false),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/-2128833038.cms", "TOI Bengaluru", "state", enabled = false),
    FeedSource("https://www.thehindu.com/news/cities/feeder/default.rss", "The Hindu · Cities", "state", enabled = false),
    FeedSource("https://www.thehindu.com/news/states/feeder/default.rss", "The Hindu · States", "state", enabled = false),
    FeedSource("https://feeds.feedburner.com/ndtvmovies-latest", "NDTV Movies", "entertainment", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/1081479906.cms", "TOI Entertainment", "entertainment", enabled = true),
    FeedSource("https://www.espncricinfo.com/rss/content/story/feeds/0.xml", "ESPNcricinfo", "cricket", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/54829575.cms", "TOI Cricket", "cricket", enabled = true),
    FeedSource("https://feeds.feedburner.com/ndtvsports-latest", "NDTV Sports", "sports", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/4719148.cms", "TOI Sports", "sports", enabled = false),
    FeedSource("https://feeds.feedburner.com/gadgets360-latest", "Gadgets 360", "tech", enabled = true),
    FeedSource("https://timesofindia.indiatimes.com/rssfeeds/66949542.cms", "TOI Tech", "tech", enabled = false),
    FeedSource("https://www.moneycontrol.com/rss/latestnews.xml", "Moneycontrol", "business", enabled = false),
    FeedSource("https://economictimes.indiatimes.com/markets/rssfeeds/1977021501.cms", "ET Markets", "business", enabled = false),
    FeedSource("https://feeds.feedburner.com/ndtvcooks-latest", "NDTV Food", "food", enabled = false),
)

/**
 * Generic international default feeds (English-language, globally reachable), used
 * for any device country other than India. Tagged by the same [FEED_CATEGORIES] as
 * [DEFAULT_FEED_SOURCES] so they drop into the same category UI/toggles; "state" and
 * "cricket" have no sensible global equivalent and are simply left unpopulated.
 */
val INTERNATIONAL_FEED_SOURCES: List<FeedSource> = listOf(
    FeedSource("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", "Google News", "nation", enabled = true),
    FeedSource("http://feeds.bbci.co.uk/news/world/rss.xml", "BBC World", "nation", enabled = true),
    FeedSource("http://feeds.bbci.co.uk/news/entertainment_arts/rss.xml", "BBC Entertainment", "entertainment", enabled = true),
    FeedSource("http://feeds.bbci.co.uk/sport/rss.xml?edition=int", "BBC Sport", "sports", enabled = true),
    FeedSource("http://feeds.bbci.co.uk/news/technology/rss.xml", "BBC Technology", "tech", enabled = true),
    FeedSource("http://feeds.bbci.co.uk/news/business/rss.xml", "BBC Business", "business", enabled = false),
    FeedSource("https://rss.nytimes.com/services/xml/rss/nyt/Food.xml", "NYT Food", "food", enabled = false),
)

/** Two-letter ISO country code the India-specific default feeds are tuned for. */
const val INDIA_COUNTRY_CODE = "IN"

/**
 * Region code stored for the manual "international" choice in feed settings — not a
 * real ISO country, just a value `defaultFeedSourcesForCountry` treats as non-India.
 */
const val INTERNATIONAL_REGION_CODE = "INTL"

/**
 * Picks the default feed preset for a device/locale [countryCode]: the curated India
 * list for `IN`, else the generic [INTERNATIONAL_FEED_SOURCES] set. Pure + exhaustive
 * (any unrecognised/blank code falls back to international) so it's unit-testable
 * without Android's Locale machinery.
 */
fun defaultFeedSourcesForCountry(countryCode: String): List<FeedSource> =
    if (countryCode.equals(INDIA_COUNTRY_CODE, ignoreCase = true)) DEFAULT_FEED_SOURCES else INTERNATIONAL_FEED_SOURCES

private val RSS_DATE_FORMATS = listOf(
    "EEE, dd MMM yyyy HH:mm:ss Z",   // RFC-822 with numeric offset
    "EEE, dd MMM yyyy HH:mm:ss zzz", // RFC-822 with zone name (GMT/UTC)
    "yyyy-MM-dd'T'HH:mm:ssXXX",      // RFC-3339 / Atom
    "yyyy-MM-dd'T'HH:mm:ss'Z'",      // RFC-3339 UTC literal Z
    "yyyy-MM-dd'T'HH:mm:ssZ",        // RFC-3339 numeric offset, no colon
)

/** Parses an RSS `pubDate` / Atom `published` string to epoch millis, or null. */
fun parseFeedDate(raw: String?): Long? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    for (pattern in RSS_DATE_FORMATS) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = true
            }.parse(text)
        }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return null
}

/**
 * Strips HTML tags and unescapes the common entities from a feed title/snippet,
 * collapsing whitespace. Feed descriptions are frequently HTML; this yields plain
 * text safe to render in a Compose `Text`.
 */
fun stripHtml(raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    return raw
        .replace(Regex("(?s)<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** Relative "time ago" label for [publishedMillis] vs [nowMillis] (now/Xm/Xh/Xd). */
fun feedAgo(publishedMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val delta = nowMillis - publishedMillis
    if (publishedMillis <= 0L || delta < 0L) return "now"
    val minutes = delta / 60_000L
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1_440 -> "${minutes / 60}h"
        else -> "${minutes / 1_440}d"
    }
}

/**
 * Parses an RSS 2.0 or Atom document into articles. Namespace-unaware DOM, so
 * prefixed tags (`media:content`, `media:thumbnail`) match by their literal name.
 * [sourceName] labels each article (the channel/feed title wins when present).
 * Any parse failure yields an empty list — a broken feed degrades to "no articles".
 */
fun parseFeed(xml: String, sourceName: String): List<FeedArticle> = runCatching {
    val doc = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = false }
        .newDocumentBuilder()
        .parse(xml.byteInputStream())
    doc.documentElement?.normalize()

    val channelTitle = firstChildText(doc.documentElement, "title")
    val label = channelTitle?.takeIf { it.isNotBlank() } ?: sourceName

    val items = doc.getElementsByTagName("item")
    val nodes = if (items.length > 0) items else doc.getElementsByTagName("entry")
    (0 until nodes.length).mapNotNull { i ->
        (nodes.item(i) as? Element)?.let { articleFrom(it, label) }
    }
}.getOrDefault(emptyList())

private fun articleFrom(item: Element, source: String): FeedArticle? {
    val title = stripHtml(childText(item, "title"))
    if (title.isEmpty()) return null
    val link = linkOf(item)
    val date = parseFeedDate(childText(item, "pubDate"))
        ?: parseFeedDate(childText(item, "published"))
        ?: parseFeedDate(childText(item, "updated"))
        ?: 0L
    val tag = stripHtml(childText(item, "category")).lowercase().takeIf { it.isNotEmpty() && it.length <= 24 }
    return FeedArticle(
        title = title,
        link = link,
        source = source,
        tag = tag ?: "news",
        imageUrl = imageOf(item),
        publishedAtMillis = date,
    )
}

/** RSS `<link>text</link>` or Atom `<link href="…">` (prefers an alternate link). */
private fun linkOf(item: Element): String {
    val text = childText(item, "link")?.trim()
    if (!text.isNullOrEmpty()) return text
    val links = item.getElementsByTagName("link")
    for (i in 0 until links.length) {
        val el = links.item(i) as? Element ?: continue
        val rel = el.getAttribute("rel")
        if (rel.isNullOrEmpty() || rel == "alternate") {
            val href = el.getAttribute("href")
            if (href.isNotEmpty()) return href
        }
    }
    return ""
}

/**
 * First image URL for an item, trying the common feed conventions in order:
 * media:content / media:thumbnail (incl. inside media:group), an image enclosure,
 * itunes:image, then an `<img>` (or lazy `data-src`) inside the HTML body
 * (content:encoded / description / content). Returns null when none is present.
 */
private fun imageOf(item: Element): String? {
    listOf("media:content", "media:thumbnail").forEach { tag ->
        val nodes = item.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val type = el.getAttribute("type")
            if (type.isNotEmpty() && !type.startsWith("image")) continue // skip video/audio media
            val url = el.getAttribute("url")
            if (url.isNotEmpty()) return normalizeImageUrl(url)
        }
    }
    val enclosures = item.getElementsByTagName("enclosure")
    for (i in 0 until enclosures.length) {
        val el = enclosures.item(i) as? Element ?: continue
        val type = el.getAttribute("type")
        val url = el.getAttribute("url")
        if (url.isNotEmpty() && (type.isNullOrEmpty() || type.startsWith("image"))) {
            return normalizeImageUrl(url)
        }
    }
    val itunes = item.getElementsByTagName("itunes:image")
    (itunes.item(0) as? Element)?.getAttribute("href")?.takeIf { it.isNotEmpty() }
        ?.let { return normalizeImageUrl(it) }

    // ESPNcricinfo and some Indian feeds carry the image as a <coverImages> element.
    childText(item, "coverImages")?.takeIf { it.startsWith("http") || it.startsWith("//") }
        ?.let { return normalizeImageUrl(it) }

    val html = listOf("content:encoded", "description", "content")
        .joinToString(" ") { childText(item, it).orEmpty() }
    return Regex("<img[^>]+(?:src|data-src)=[\"']([^\"']+)[\"']").find(html)
        ?.groupValues?.getOrNull(1)
        ?.let { normalizeImageUrl(it) }
}

/**
 * Normalises an image URL so it actually loads on Android: protocol-relative
 * `//host` → https, and **cleartext `http://` → `https://`** (Android blocks
 * cleartext by default, and the image hosts that use it — e.g. ESPNcricinfo's
 * `p.imgci.com` — also serve https).
 */
private fun normalizeImageUrl(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https://" + url.substring("http://".length)
    else -> url
}

/** Text of the first direct-or-descendant child element named [name]. */
private fun childText(parent: Element, name: String): String? {
    val nodes = parent.getElementsByTagName(name)
    return (nodes.item(0))?.textContent?.trim()
}

private fun firstChildText(parent: Element?, name: String): String? {
    parent ?: return null
    val nodes = parent.getElementsByTagName(name)
    return (0 until nodes.length).asSequence()
        .map { nodes.item(it) }
        .firstOrNull { it.nodeType == Node.ELEMENT_NODE }
        ?.textContent?.trim()
}
