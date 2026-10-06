package com.tileshell.feature.livetiles

import java.util.Locale
import kotlin.random.Random

/**
 * "Play <something> on TileShell" from the car. The query is matched against
 * favourite shows, then favourite stations, then artists, albums and songs;
 * the first group with a match wins, and within it an exact name beats one
 * that starts with the query, which beats one that merely contains it. An
 * empty query ("play some music") shuffles the library. Pure over
 * [AutoLibrarySource].
 */
object AutoVoiceSearch {

    suspend fun resolve(query: String, src: AutoLibrarySource, random: Random = Random.Default): AutoPlayRequest? {
        val q = normalize(query)
        if (q.isEmpty()) return AutoBrowseTree.resolvePlay(AutoMediaId.Shuffle(AutoMediaId.LibSongs), src, random)

        best(src.subscriptions(), q) { it.title }?.let {
            return AutoBrowseTree.resolvePlay(AutoMediaId.PlayLatest(it.feedUrl), src)
        }
        best(src.favoriteStations(), q) { it.name }?.let {
            return AutoBrowseTree.resolvePlay(AutoMediaId.Station(it.stationId), src)
        }
        if (!src.hasAudioAccess()) return null
        val tracks = src.tracks()
        best(tracks.map { it.artist }.distinct(), q) { it }?.let {
            return AutoBrowseTree.resolvePlay(AutoMediaId.PlayAll(AutoMediaId.Artist(it)), src)
        }
        best(src.albums(), q) { it.title }?.let {
            return AutoBrowseTree.resolvePlay(AutoMediaId.PlayAll(AutoMediaId.Album(it.id)), src)
        }
        best(tracks, q) { it.title }?.let {
            return AutoBrowseTree.resolvePlay(AutoMediaId.Track(AutoMediaId.Album(it.albumId), it.id), src)
                ?: AutoPlayRequest.Tracks(listOf(it), 0)
        }
        return null
    }

    private fun normalize(s: String) = s.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    /** Exact, then prefix, then substring; ties keep the list's own order. */
    private fun <T> best(items: List<T>, q: String, name: (T) -> String): T? {
        var prefix: T? = null
        var contains: T? = null
        for (item in items) {
            val n = normalize(name(item))
            if (n == q) return item
            if (prefix == null && n.startsWith(q)) prefix = item
            if (contains == null && n.contains(q)) contains = item
        }
        return prefix ?: contains
    }
}
