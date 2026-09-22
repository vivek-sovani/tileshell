package com.tileshell.feature.livetiles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One internet radio station, from a Radio-Browser directory search. */
data class RadioStation(
    val stationId: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String?,
    val country: String,
    val tags: String,
)

/**
 * Searches the free, open, no-key Radio-Browser directory
 * (radio-browser.info — a community-maintained catalogue of internet radio
 * stations and their own live stream URLs) by station name — the discovery
 * mechanism for the music hub's "radio" tab, the same shape as podcasts'
 * iTunes search. `all.api.radio-browser.info` round-robins across the
 * project's own mirror servers, so no single server address needs to be
 * hardcoded/kept up to date here.
 */
suspend fun searchRadioStations(query: String): List<RadioStation> {
    if (query.isBlank()) return emptyList()
    val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrNull() ?: return emptyList()
    val url = "https://all.api.radio-browser.info/json/stations/search?name=$encoded&limit=25&hidebroken=true"
    val json = httpGetText(url) ?: return emptyList()
    return parseRadioStations(json)
}

/** Pure JSON parsing, split out from the network call so it's unit-testable
 * against a real captured response with no network/Android dependency. */
fun parseRadioStations(json: String): List<RadioStation> = runCatching {
    val array = JSONArray(json)
    (0 until array.length()).mapNotNull { i ->
        val obj = array.optJSONObject(i) ?: return@mapNotNull null
        // `url_resolved` is the directory's own already-followed-redirects
        // stream URL — preferred over the raw `url`, which is sometimes a
        // playlist/redirector rather than a directly playable stream.
        val streamUrl = obj.optString("url_resolved").takeIf { it.isNotBlank() }
            ?: obj.optString("url").takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        RadioStation(
            stationId = obj.optString("stationuuid").takeIf { it.isNotBlank() } ?: streamUrl,
            name = name,
            streamUrl = streamUrl,
            faviconUrl = obj.optString("favicon").takeIf { it.isNotBlank() },
            country = obj.optString("country"),
            tags = obj.optString("tags"),
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
