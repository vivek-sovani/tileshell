package com.tileshell.feature.livetiles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One result from the iTunes podcast directory search — a show, not an episode. */
data class PodcastSearchResult(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
)

/**
 * Searches Apple's iTunes Search API for podcasts by name — free, no API key,
 * no account — the discovery mechanism for the music hub's "podcasts" tab.
 * There's no bundled podcast directory of our own, and this is the only
 * broadly-covering free podcast search available without a paid/keyed
 * service. Read-only and non-authenticated: it only ever resolves a show's
 * own public RSS feed URL, which is then fetched directly like any other
 * feed ([fetchPodcastFeed]) — Apple isn't in the loop for anything after
 * that, including actual playback.
 */
suspend fun searchPodcasts(query: String): List<PodcastSearchResult> {
    if (query.isBlank()) return emptyList()
    val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrNull() ?: return emptyList()
    val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=25&term=$encoded"
    val json = httpGetText(url) ?: return emptyList()
    return parsePodcastSearchResults(json)
}

/** Pure JSON parsing, split out from the network call so it's unit-testable
 * against a real captured response with no network/Android dependency. */
fun parsePodcastSearchResults(json: String): List<PodcastSearchResult> = runCatching {
    val results = JSONObject(json).optJSONArray("results") ?: JSONArray()
    (0 until results.length()).mapNotNull { i ->
        val obj = results.optJSONObject(i) ?: return@mapNotNull null
        val feedUrl = obj.optString("feedUrl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val title = obj.optString("collectionName").takeIf { it.isNotBlank() }
            ?: obj.optString("trackName").takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        PodcastSearchResult(
            feedUrl = feedUrl,
            title = title,
            author = obj.optString("artistName"),
            artworkUrl = obj.optString("artworkUrl600").takeIf { it.isNotBlank() }
                ?: obj.optString("artworkUrl100").takeIf { it.isNotBlank() },
        )
    }
}.getOrElse { emptyList() }

/** A browsable top-level Apple Podcasts genre — user-requested category
 * browsing ("can we show categories like genre, language etc."). IDs are
 * Apple's own stable genre identifiers. */
data class PodcastGenre(val id: Int, val label: String)

val PODCAST_GENRES = listOf(
    PodcastGenre(1489, "news"),
    PodcastGenre(1303, "comedy"),
    PodcastGenre(1488, "true crime"),
    PodcastGenre(1318, "technology"),
    PodcastGenre(1321, "business"),
    PodcastGenre(1512, "health & fitness"),
    PodcastGenre(1324, "society & culture"),
    PodcastGenre(1545, "sports"),
    PodcastGenre(1304, "education"),
    PodcastGenre(1301, "arts"),
    PodcastGenre(1314, "spiritual"),
)

/** A browsable Apple Podcasts storefront country — user-requested
 * ("country/region should be also welcome in podcast and radio search"). */
data class PodcastCountry(val code: String, val label: String)

val PODCAST_COUNTRIES = listOf(
    PodcastCountry("us", "united states"),
    PodcastCountry("gb", "united kingdom"),
    PodcastCountry("in", "india"),
    PodcastCountry("au", "australia"),
    PodcastCountry("ca", "canada"),
    PodcastCountry("de", "germany"),
    PodcastCountry("fr", "france"),
    PodcastCountry("jp", "japan"),
    PodcastCountry("br", "brazil"),
)

/**
 * The current top podcasts in [genreId] (any genre when null) and
 * [countryCode]'s own storefront — browsing by category rather than typing a
 * search term; genre and country combine freely (both are just separate
 * path segments on the same chart URL). Two iTunes calls, both free/no-key:
 * its "RSS Generator" charts endpoint lists the chart's current top shows by
 * collection id only (no feed URL), so each id is then resolved through the
 * plain Lookup API, which returns the exact same shape as [searchPodcasts]'s
 * own results — [parsePodcastSearchResults] is reused as-is for the second
 * step.
 */
suspend fun topPodcasts(genreId: Int?, countryCode: String = "us"): List<PodcastSearchResult> {
    val chartUrl = buildString {
        append("https://itunes.apple.com/$countryCode/rss/toppodcasts/limit=25/")
        if (genreId != null) append("genre=$genreId/")
        append("json")
    }
    val chartJson = httpGetText(chartUrl) ?: return emptyList()
    val ids = parseChartTrackIds(chartJson)
    if (ids.isEmpty()) return emptyList()
    val lookupUrl = "https://itunes.apple.com/lookup?id=${ids.joinToString(",")}"
    val lookupJson = httpGetText(lookupUrl) ?: return emptyList()
    return parsePodcastSearchResults(lookupJson)
}

/** Pure JSON parsing of the charts endpoint's own response shape
 * (`feed.entry[].id.attributes["im:id"]`) — unit-testable without a network
 * call. */
fun parseChartTrackIds(json: String): List<String> = runCatching {
    val entries = JSONObject(json).optJSONObject("feed")?.optJSONArray("entry") ?: JSONArray()
    (0 until entries.length()).mapNotNull { i ->
        entries.optJSONObject(i)
            ?.optJSONObject("id")
            ?.optJSONObject("attributes")
            ?.optString("im:id")
            ?.takeIf { it.isNotBlank() }
    }
}.getOrElse { emptyList() }

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
