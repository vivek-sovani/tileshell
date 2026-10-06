package com.tileshell.feature.livetiles

import java.net.URLDecoder
import java.net.URLEncoder

enum class AutoTab(val key: String) {
    LIBRARY("library"),
    PLAYLISTS("playlists"),
    PODCASTS("podcasts"),
    RADIO("radio"),
}

/**
 * Every node Android Auto can browse or play, as the string id the car hands
 * back to us. Pure (no Android types) so the format is unit-tested: each part
 * is URL-encoded, so names containing `/` or `|` can't break the parsing.
 * [Track], [PlayAll] and [Shuffle] carry the container they came from, so
 * playing a song from an album queues that album.
 */
sealed class AutoMediaId {
    object Root : AutoMediaId()
    data class Tab(val tab: AutoTab) : AutoMediaId()
    object LibArtists : AutoMediaId()
    object LibAlbums : AutoMediaId()
    object LibSongs : AutoMediaId()
    data class Artist(val name: String) : AutoMediaId()
    data class Album(val id: Long) : AutoMediaId()
    data class Playlist(val id: Long) : AutoMediaId()
    data class Track(val container: AutoMediaId, val id: Long) : AutoMediaId()
    data class PlayAll(val container: AutoMediaId) : AutoMediaId()
    data class Shuffle(val container: AutoMediaId) : AutoMediaId()
    data class Show(val feedUrl: String) : AutoMediaId()
    data class EpisodesPage(val feedUrl: String, val page: Int) : AutoMediaId()
    data class PlayLatest(val feedUrl: String) : AutoMediaId()
    data class Episode(val feedUrl: String, val guid: String) : AutoMediaId()
    data class Station(val stationId: String) : AutoMediaId()
    data class Message(val kind: String) : AutoMediaId()

    fun encode(): String = when (this) {
        Root -> "root"
        is Tab -> "tab/${tab.key}"
        LibArtists -> "lib/artists"
        LibAlbums -> "lib/albums"
        LibSongs -> "lib/songs"
        is Artist -> "artist/${enc(name)}"
        is Album -> "album/$id"
        is Playlist -> "playlist/$id"
        is Track -> "track/${enc(container.encode())}/$id"
        is PlayAll -> "all/${enc(container.encode())}"
        is Shuffle -> "shuffle/${enc(container.encode())}"
        is Show -> "show/${enc(feedUrl)}"
        is EpisodesPage -> "more/${enc(feedUrl)}/$page"
        is PlayLatest -> "latest/${enc(feedUrl)}"
        is Episode -> "episode/${enc(feedUrl)}/${enc(guid)}"
        is Station -> "station/${enc(stationId)}"
        is Message -> "msg/${enc(kind)}"
    }

    companion object {
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

        /** Null for anything that isn't one of our ids. */
        fun parse(raw: String): AutoMediaId? = runCatching { parseOrThrow(raw) }.getOrNull()

        private fun parseOrThrow(raw: String): AutoMediaId? {
            val p = raw.split("/")
            return when (p[0]) {
                "root" -> if (p.size == 1) Root else null
                "tab" -> if (p.size == 2) AutoTab.values().firstOrNull { it.key == p[1] }?.let { Tab(it) } else null
                "lib" -> when (p.getOrNull(1)) {
                    "artists" -> LibArtists
                    "albums" -> LibAlbums
                    "songs" -> LibSongs
                    else -> null
                }.takeIf { p.size == 2 }
                "artist" -> if (p.size == 2) Artist(dec(p[1])) else null
                "album" -> if (p.size == 2) Album(p[1].toLong()) else null
                "playlist" -> if (p.size == 2) Playlist(p[1].toLong()) else null
                "track" -> if (p.size == 3) container(p[1])?.let { Track(it, p[2].toLong()) } else null
                "all" -> if (p.size == 2) container(p[1])?.let { PlayAll(it) } else null
                "shuffle" -> if (p.size == 2) container(p[1])?.let { Shuffle(it) } else null
                "show" -> if (p.size == 2) Show(dec(p[1])) else null
                "more" -> if (p.size == 3) p[2].toInt().takeIf { it > 0 }?.let { EpisodesPage(dec(p[1]), it) } else null
                "latest" -> if (p.size == 2) PlayLatest(dec(p[1])) else null
                "episode" -> if (p.size == 3) Episode(dec(p[1]), dec(p[2])) else null
                "station" -> if (p.size == 2) Station(dec(p[1])) else null
                "msg" -> if (p.size == 2) Message(dec(p[1])) else null
                else -> null
            }
        }

        /** Only songs lists can hold tracks, so only they are valid containers. */
        private fun container(encoded: String): AutoMediaId? =
            parse(dec(encoded))?.takeIf { it is LibSongs || it is Album || it is Artist || it is Playlist }
    }
}
