package com.tileshell.feature.livetiles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val trimmed = query.trim()
    if (trimmed.isBlank()) return emptyList()
    val direct = searchStationsByName(trimmed)
    if (direct.isNotEmpty()) return direct

    // Radio-Browser's `name` filter is a literal substring match, not a
    // word search — a multi-word query like "hindi desi bollywood" or
    // "bollywood hits" comes back empty whenever no single station's name
    // contains that exact phrase, even though stations matching the
    // individual words are right there in the directory (confirmed live:
    // "bollywood" alone returns 25 matches, "hindi desi bollywood" returns
    // none). Fall back to searching each significant word on its own and
    // merging/ranking by how many of the typed words a station matches.
    val words = significantQueryWords(trimmed)
    if (words.size <= 1) return direct
    val perWord = coroutineScope {
        words.map { async { searchStationsByName(it) } }.map { it.await() }
    }
    return rankMergedStations(perWord, words)
}

private suspend fun searchStationsByName(name: String): List<RadioStation> {
    val encoded = runCatching { URLEncoder.encode(name, "UTF-8") }.getOrNull() ?: return emptyList()
    val url = "https://all.api.radio-browser.info/json/stations/search?name=$encoded&limit=25&hidebroken=true"
    val json = httpGetText(url) ?: return emptyList()
    return parseRadioStations(json)
}

/** Query words worth searching individually as the multi-word fallback —
 * short filler words ("of", "fm", "the"…) are dropped so they don't flood
 * the per-word search with noise unrelated to what the user actually typed. */
fun significantQueryWords(query: String): List<String> =
    query.trim().split(Regex("\\s+")).filter { it.length >= 3 }.distinct()

/**
 * Merges several per-word search result lists (deduped by station id, first
 * occurrence wins) and ranks by how many of [words] each station's name or
 * tags actually contain — a station matching every typed word sorts above
 * one matching only one, so "hindi desi bollywood" surfaces a station like
 * "Desi Hits Bollywood Radio" (if any) ahead of a plain "Hindi FM" that only
 * matches one word. Ties keep first-seen order (stable sort).
 */
fun rankMergedStations(resultSets: List<List<RadioStation>>, words: List<String>): List<RadioStation> {
    val merged = LinkedHashMap<String, RadioStation>()
    resultSets.forEach { set -> set.forEach { merged.putIfAbsent(it.stationId, it) } }
    return merged.values.sortedByDescending { station ->
        words.count { w -> station.name.contains(w, ignoreCase = true) || station.tags.contains(w, ignoreCase = true) }
    }
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

/** Fixed, curated genre/language/country chips for the "radio" tab's
 * category browsing (user-requested: "can we show categories like genre,
 * language etc."; "country/region should be also welcome"). Radio-Browser's
 * own tag/language/country vocabulary is free-form and enormous — these are
 * simply the common, recognizable ones, not an exhaustive or live-fetched
 * list. Country uses the ISO 3166-1 alpha-2 `countrycode` field (more
 * reliable to filter on than the directory's own free-text country names,
 * which can be verbose/non-standard, e.g. "The United Kingdom Of Great
 * Britain And Northern Ireland"). */
val RADIO_GENRES = listOf("pop", "rock", "jazz", "classical", "news", "talk", "electronic", "sports", "chill")
val RADIO_LANGUAGES = listOf("english", "hindi", "spanish", "french", "german", "arabic", "chinese", "japanese")

data class RadioCountry(val code: String, val label: String)

val RADIO_COUNTRIES = listOf(
    RadioCountry("IN", "india"),
    RadioCountry("US", "united states"),
    RadioCountry("GB", "united kingdom"),
    RadioCountry("AU", "australia"),
    RadioCountry("CA", "canada"),
    RadioCountry("DE", "germany"),
    RadioCountry("FR", "france"),
    RadioCountry("JP", "japan"),
    RadioCountry("BR", "brazil"),
    RadioCountry("AE", "uae"),
)

/**
 * Stations matching any combination of [tag] (genre)/[language]/
 * [countryCode] — all three are independently selectable chips that combine
 * into one query, rather than mutually-exclusive single-category browsing
 * (user-requested: "genre and language should be combindly selectable").
 * Same result shape as [searchRadioStations], so [parseRadioStations] is
 * reused as-is. Returns empty when none are set — there is nothing to browse.
 */
suspend fun stationsByFilters(tag: String?, language: String?, countryCode: String?): List<RadioStation> {
    if (tag == null && language == null && countryCode == null) return emptyList()
    val params = buildList {
        tag?.let { add("tag=" + URLEncoder.encode(it, "UTF-8")) }
        language?.let { add("language=" + URLEncoder.encode(it, "UTF-8")) }
        countryCode?.let { add("countrycode=" + URLEncoder.encode(it, "UTF-8")) }
        add("limit=25")
        add("hidebroken=true")
    }
    val url = "https://all.api.radio-browser.info/json/stations/search?" + params.joinToString("&")
    return parseRadioStations(httpGetText(url) ?: return emptyList())
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
