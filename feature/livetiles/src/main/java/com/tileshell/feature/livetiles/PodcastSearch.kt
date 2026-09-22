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
