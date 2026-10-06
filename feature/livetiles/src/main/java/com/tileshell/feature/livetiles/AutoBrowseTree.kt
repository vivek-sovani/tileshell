package com.tileshell.feature.livetiles

import java.util.Locale
import kotlin.random.Random

/** Artwork the car should show; mapped to a URI in [AutoMediaItems]. */
sealed interface AutoArt {
    data class LocalAlbum(val albumId: Long) : AutoArt
    data class Remote(val url: String) : AutoArt
}

enum class AutoItemKind { BROWSABLE, PLAYABLE, INFO }

/** One row on the car screen. [group] becomes a section heading. */
data class AutoItem(
    val id: AutoMediaId,
    val title: String,
    val subtitle: String? = null,
    val kind: AutoItemKind,
    val art: AutoArt? = null,
    val group: String? = null,
)

/** What the browse tree reads. The real one is [LocalLibrarySource]; tests use a fake. */
interface AutoLibrarySource {
    suspend fun hasAudioAccess(): Boolean
    suspend fun tracks(): List<LocalTrack>
    suspend fun albums(): List<LocalAlbum>
    suspend fun playlists(): List<LocalPlaylist>
    suspend fun tracksForAlbum(albumId: Long): List<LocalTrack>
    suspend fun tracksForPlaylist(playlistId: Long): List<LocalTrack>
}

data class AutoPlayRequest(val tracks: List<LocalTrack>, val startIndex: Int)

/**
 * The Android Auto browse tree for the on-device library (Library and
 * Playlists tabs), and how a tapped id turns into a play queue. Pure over
 * [AutoLibrarySource]. A list is never left empty: a missing permission or an
 * empty library shows one message row instead, which is what Android Auto's
 * review expects.
 */
object AutoBrowseTree {
    /** The car shows long lists badly (and a binder result has a size limit). */
    const val MAX_ITEMS = 200

    const val MSG_NO_ACCESS = "no_access"
    const val MSG_EMPTY_LIBRARY = "empty_library"
    const val MSG_NO_PLAYLISTS = "no_playlists"
    const val MSG_MORE_SONGS = "more_songs"
    const val MSG_SOON = "soon"

    fun root(): List<AutoItem> = listOf(
        tab(AutoTab.LIBRARY, "Library"),
        tab(AutoTab.PLAYLISTS, "Playlists"),
        tab(AutoTab.PODCASTS, "Podcasts"),
        tab(AutoTab.RADIO, "Radio"),
    )

    private fun tab(tab: AutoTab, title: String) =
        AutoItem(AutoMediaId.Tab(tab), title, kind = AutoItemKind.BROWSABLE)

    suspend fun children(parent: AutoMediaId, src: AutoLibrarySource): List<AutoItem> = when (parent) {
        AutoMediaId.Root -> root()
        is AutoMediaId.Tab -> when (parent.tab) {
            AutoTab.LIBRARY -> libraryTab(src)
            AutoTab.PLAYLISTS -> playlistsTab(src)
            AutoTab.PODCASTS, AutoTab.RADIO -> listOf(message(MSG_SOON, "Coming to the car soon"))
        }
        AutoMediaId.LibArtists -> artists(src)
        AutoMediaId.LibAlbums -> albums(src)
        AutoMediaId.LibSongs -> songList(parent, src)
        is AutoMediaId.Artist, is AutoMediaId.Album, is AutoMediaId.Playlist -> songList(parent, src)
        else -> emptyList()
    }

    private fun message(kind: String, text: String) =
        AutoItem(AutoMediaId.Message(kind), text, kind = AutoItemKind.INFO)

    private suspend fun libraryTab(src: AutoLibrarySource): List<AutoItem> {
        if (!src.hasAudioAccess()) return listOf(message(MSG_NO_ACCESS, "Allow music access in TileShell"))
        val tracks = src.tracks()
        if (tracks.isEmpty()) return listOf(message(MSG_EMPTY_LIBRARY, "No music found on this phone"))
        val artistCount = tracks.map { it.artist }.distinct().size
        return listOf(
            AutoItem(AutoMediaId.LibArtists, "Artists", count(artistCount, "artist"), AutoItemKind.BROWSABLE),
            AutoItem(AutoMediaId.LibAlbums, "Albums", count(src.albums().size, "album"), AutoItemKind.BROWSABLE),
            AutoItem(AutoMediaId.LibSongs, "All songs", count(tracks.size, "song"), AutoItemKind.BROWSABLE),
        )
    }

    private suspend fun playlistsTab(src: AutoLibrarySource): List<AutoItem> {
        if (!src.hasAudioAccess()) return listOf(message(MSG_NO_ACCESS, "Allow music access in TileShell"))
        val lists = src.playlists()
        if (lists.isEmpty()) return listOf(message(MSG_NO_PLAYLISTS, "No playlists on this phone"))
        return lists.sortedBy { it.name.lowercase(Locale.ROOT) }.take(MAX_ITEMS)
            .map { AutoItem(AutoMediaId.Playlist(it.id), it.name, kind = AutoItemKind.BROWSABLE) }
    }

    private suspend fun artists(src: AutoLibrarySource): List<AutoItem> {
        val tracks = src.tracks()
        if (tracks.isEmpty()) return listOf(message(MSG_EMPTY_LIBRARY, "No music found on this phone"))
        return tracks.groupBy { it.artist }.entries
            .sortedBy { it.key.lowercase(Locale.ROOT) }.take(MAX_ITEMS)
            .map { (artist, list) ->
                AutoItem(
                    AutoMediaId.Artist(artist), artist, count(list.size, "song"), AutoItemKind.BROWSABLE,
                    art = AutoArt.LocalAlbum(list.first().albumId),
                )
            }
    }

    private suspend fun albums(src: AutoLibrarySource): List<AutoItem> {
        val albums = src.albums()
        if (albums.isEmpty()) return listOf(message(MSG_EMPTY_LIBRARY, "No music found on this phone"))
        return albums.sortedBy { it.title.lowercase(Locale.ROOT) }.take(MAX_ITEMS).map {
            AutoItem(
                AutoMediaId.Album(it.id), it.title, "${it.artist} · ${count(it.trackCount, "song")}",
                AutoItemKind.BROWSABLE, art = AutoArt.LocalAlbum(it.id),
            )
        }
    }

    /** Play all, shuffle, then the songs (capped at [MAX_ITEMS]). */
    private suspend fun songList(container: AutoMediaId, src: AutoLibrarySource): List<AutoItem> {
        val tracks = containerTracks(container, src)
        if (tracks.isEmpty()) return listOf(message(MSG_EMPTY_LIBRARY, "No songs here"))
        val rows = tracks.take(MAX_ITEMS).map {
            AutoItem(
                AutoMediaId.Track(container, it.id), it.title, it.artist, AutoItemKind.PLAYABLE,
                art = AutoArt.LocalAlbum(it.albumId),
            )
        }
        val head = listOf(
            AutoItem(AutoMediaId.PlayAll(container), "Play all", kind = AutoItemKind.PLAYABLE),
            AutoItem(AutoMediaId.Shuffle(container), "Shuffle", kind = AutoItemKind.PLAYABLE),
        )
        val tail = if (tracks.size > MAX_ITEMS) {
            listOf(message(MSG_MORE_SONGS, "Showing the first $MAX_ITEMS songs. Use voice search for the rest."))
        } else {
            emptyList()
        }
        return head + rows + tail
    }

    /** The songs of a container in the order the car lists them. */
    suspend fun containerTracks(container: AutoMediaId, src: AutoLibrarySource): List<LocalTrack> = when (container) {
        AutoMediaId.LibSongs -> src.tracks().sortedBy { it.title.lowercase(Locale.ROOT) }
        is AutoMediaId.Artist -> src.tracks().filter { it.artist == container.name }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
        is AutoMediaId.Album -> src.tracksForAlbum(container.id)
        is AutoMediaId.Playlist -> src.tracksForPlaylist(container.id)
        else -> emptyList()
    }

    /** What to play for a tapped id; null when it isn't something playable. */
    suspend fun resolvePlay(id: AutoMediaId, src: AutoLibrarySource, random: Random = Random.Default): AutoPlayRequest? =
        when (id) {
            is AutoMediaId.Track -> {
                val list = containerTracks(id.container, src)
                val index = list.indexOfFirst { it.id == id.id }
                if (index < 0) null else AutoPlayRequest(list, index)
            }
            is AutoMediaId.PlayAll -> containerTracks(id.container, src).takeIf { it.isNotEmpty() }
                ?.let { AutoPlayRequest(it, 0) }
            is AutoMediaId.Shuffle -> containerTracks(id.container, src).takeIf { it.isNotEmpty() }
                ?.let { AutoPlayRequest(it.shuffled(random), 0) }
            else -> null
        }

    private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}
