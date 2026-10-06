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

    // Podcasts and radio. Defaults keep the library-only fakes in tests short.
    suspend fun subscriptions(): List<PodcastSubscription> = emptyList()
    suspend fun recentEpisodes(): List<RecentEpisode> = emptyList()
    suspend fun favoriteStations(): List<FavoriteStation> = emptyList()
    suspend fun recentStations(): List<FavoriteStation> = emptyList()

    /** A show with its episodes, or null when the feed can't be loaded. */
    suspend fun show(feedUrl: String): PodcastShow? = null
}

/** What a tapped id should start playing. */
sealed interface AutoPlayRequest {
    data class Tracks(val tracks: List<LocalTrack>, val startIndex: Int) : AutoPlayRequest
    data class Episodes(val show: PodcastSubscription, val episodes: List<PodcastEpisode>, val startIndex: Int) : AutoPlayRequest
    data class Station(val station: RadioStationRef, val favorites: List<RadioStationRef>) : AutoPlayRequest
}

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
    const val MSG_NO_PODCASTS = "no_podcasts"
    const val MSG_NO_STATIONS = "no_stations"
    const val MSG_OFFLINE = "offline"

    const val FAVORITES = "Favorites"
    const val RECENTLY_PLAYED = "Recently played"
    const val EPISODE_PAGE_SIZE = 20

    fun root(): List<AutoItem> = listOf(
        tab(AutoTab.LIBRARY, "Library"),
        tab(AutoTab.PLAYLISTS, "Playlists"),
        tab(AutoTab.PODCASTS, "Podcasts"),
        tab(AutoTab.RADIO, "Radio"),
    )

    private fun tab(tab: AutoTab, title: String) =
        AutoItem(AutoMediaId.Tab(tab), title, kind = AutoItemKind.BROWSABLE)

    suspend fun children(
        parent: AutoMediaId,
        src: AutoLibrarySource,
        now: Long = System.currentTimeMillis(),
    ): List<AutoItem> = when (parent) {
        AutoMediaId.Root -> root()
        is AutoMediaId.Tab -> when (parent.tab) {
            AutoTab.LIBRARY -> libraryTab(src)
            AutoTab.PLAYLISTS -> playlistsTab(src)
            AutoTab.PODCASTS -> podcastsTab(src)
            AutoTab.RADIO -> radioTab(src)
        }
        is AutoMediaId.Show -> episodeList(parent.feedUrl, 0, src, now)
        is AutoMediaId.EpisodesPage -> episodeList(parent.feedUrl, parent.page, src, now)
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

    private suspend fun podcastsTab(src: AutoLibrarySource): List<AutoItem> {
        val subs = src.subscriptions()
        val recents = src.recentEpisodes().take(MusicRecents.MAX)
        if (subs.isEmpty() && recents.isEmpty()) {
            return listOf(message(MSG_NO_PODCASTS, "Subscribe to a podcast in TileShell to listen here"))
        }
        val favorites = subs.take(MAX_ITEMS).map {
            AutoItem(
                AutoMediaId.Show(it.feedUrl), it.title, kind = AutoItemKind.BROWSABLE,
                art = it.artworkUrl?.let(AutoArt::Remote), group = FAVORITES,
            )
        }
        val recent = recents.map {
            AutoItem(
                AutoMediaId.Episode(it.show.feedUrl, it.episode.guid), it.episode.title, it.show.title,
                AutoItemKind.PLAYABLE, art = (it.episode.imageUrl ?: it.show.artworkUrl)?.let(AutoArt::Remote),
                group = RECENTLY_PLAYED,
            )
        }
        return favorites + recent
    }

    private suspend fun radioTab(src: AutoLibrarySource): List<AutoItem> {
        val favorites = src.favoriteStations()
        val recents = src.recentStations().take(MusicRecents.MAX)
        if (favorites.isEmpty() && recents.isEmpty()) {
            return listOf(message(MSG_NO_STATIONS, "Favorite a radio station in TileShell to listen here"))
        }
        fun row(s: FavoriteStation, group: String) = AutoItem(
            AutoMediaId.Station(s.stationId), s.name, kind = AutoItemKind.PLAYABLE,
            art = s.faviconUrl?.let(AutoArt::Remote), group = group,
        )
        return favorites.take(MAX_ITEMS).map { row(it, FAVORITES) } + recents.map { row(it, RECENTLY_PLAYED) }
    }

    /** Newest first; [page] 0 is the show screen (with "Play latest"), later pages are "more episodes". */
    private suspend fun episodeList(feedUrl: String, page: Int, src: AutoLibrarySource, now: Long): List<AutoItem> {
        val show = src.show(feedUrl) ?: return listOf(message(MSG_OFFLINE, "Couldn't load episodes. Check your connection."))
        val episodes = newestFirst(show.episodes)
        if (episodes.isEmpty()) return listOf(message(MSG_OFFLINE, "No episodes yet"))
        val from = page * EPISODE_PAGE_SIZE
        val slice = episodes.drop(from).take(EPISODE_PAGE_SIZE)
        if (slice.isEmpty()) return listOf(message(MSG_OFFLINE, "No more episodes"))
        val rows = slice.map {
            AutoItem(
                AutoMediaId.Episode(feedUrl, it.guid), it.title, episodeSubtitle(it.publishedMillis, it.durationMs, now),
                AutoItemKind.PLAYABLE, art = (it.imageUrl ?: show.imageUrl)?.let(AutoArt::Remote),
            )
        }
        val head = if (page == 0) {
            listOf(AutoItem(AutoMediaId.PlayLatest(feedUrl), "Play latest", kind = AutoItemKind.PLAYABLE))
        } else {
            emptyList()
        }
        val more = if (from + EPISODE_PAGE_SIZE < episodes.size) {
            listOf(AutoItem(AutoMediaId.EpisodesPage(feedUrl, page + 1), "More episodes", kind = AutoItemKind.BROWSABLE))
        } else {
            emptyList()
        }
        return head + rows + more
    }

    private fun newestFirst(episodes: List<PodcastEpisode>) = episodes.sortedByDescending { it.publishedMillis }

    /** "2 days ago · 1h 12m" (either part may be missing). */
    fun episodeSubtitle(publishedMillis: Long, durationMs: Long?, now: Long): String? =
        listOfNotNull(relativeAge(publishedMillis, now), durationMs?.let(::durationLabel)).joinToString(" · ").ifEmpty { null }

    fun relativeAge(publishedMillis: Long, now: Long): String? {
        if (publishedMillis <= 0) return null
        val days = ((now - publishedMillis) / DAY_MS).toInt()
        return when {
            days <= 0 -> "today"
            days == 1 -> "yesterday"
            days < 14 -> "$days days ago"
            days < 60 -> "${days / 7} weeks ago"
            days < 730 -> "${days / 30} months ago"
            else -> "${days / 365} years ago"
        }
    }

    fun durationLabel(ms: Long): String? {
        val minutes = (ms / 60_000).toInt()
        if (minutes <= 0) return null
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }

    private const val DAY_MS = 86_400_000L

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
                if (index < 0) null else AutoPlayRequest.Tracks(list, index)
            }
            is AutoMediaId.PlayAll -> containerTracks(id.container, src).takeIf { it.isNotEmpty() }
                ?.let { AutoPlayRequest.Tracks(it, 0) }
            is AutoMediaId.Shuffle -> containerTracks(id.container, src).takeIf { it.isNotEmpty() }
                ?.let { AutoPlayRequest.Tracks(it.shuffled(random), 0) }
            is AutoMediaId.PlayLatest -> episodes(id.feedUrl, null, src)
            is AutoMediaId.Episode -> episodes(id.feedUrl, id.guid, src)
            is AutoMediaId.Station -> station(id.stationId, src)
            else -> null
        }

    /**
     * An episode queue is the show newest first, starting at the picked
     * episode, so "next" goes to the next older one. A recently played
     * episode still plays when the feed can't be loaded (it carries its show).
     */
    private suspend fun episodes(feedUrl: String, guid: String?, src: AutoLibrarySource): AutoPlayRequest? {
        val show = src.show(feedUrl)
        if (show != null) {
            val list = newestFirst(show.episodes)
            val index = if (guid == null) 0 else list.indexOfFirst { it.guid == guid }
            if (index < 0 || list.isEmpty()) return null
            val sub = src.subscriptions().firstOrNull { it.feedUrl == feedUrl }
                ?: PodcastSubscription(feedUrl, show.title, show.imageUrl, 0L)
            return AutoPlayRequest.Episodes(sub, list, index)
        }
        val recent = src.recentEpisodes().firstOrNull { it.show.feedUrl == feedUrl && (guid == null || it.episode.guid == guid) }
            ?: return null
        return AutoPlayRequest.Episodes(recent.show, listOf(recent.episode), 0)
    }

    private suspend fun station(stationId: String, src: AutoLibrarySource): AutoPlayRequest? {
        val favorites = src.favoriteStations()
        val found = favorites.firstOrNull { it.stationId == stationId }
            ?: src.recentStations().firstOrNull { it.stationId == stationId }
            ?: return null
        return AutoPlayRequest.Station(RadioStationRef(found), favorites.map(::RadioStationRef))
    }

    private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}
