package com.tileshell.feature.livetiles

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AutoBrowseTreeTest {
    private fun t(id: Long, title: String, artist: String, albumId: Long = 1) =
        LocalTrack(id, title, artist, "alb$albumId", albumId, 180_000)

    private class Fake(
        var access: Boolean = true,
        val tracks: List<LocalTrack> = emptyList(),
        val albums: List<LocalAlbum> = emptyList(),
        val playlists: List<LocalPlaylist> = emptyList(),
        val playlistTracks: Map<Long, List<LocalTrack>> = emptyMap(),
        val subs: List<PodcastSubscription> = emptyList(),
        val recentEps: List<RecentEpisode> = emptyList(),
        val favStations: List<FavoriteStation> = emptyList(),
        val recentStns: List<FavoriteStation> = emptyList(),
        val shows: Map<String, PodcastShow> = emptyMap(),
    ) : AutoLibrarySource {
        override suspend fun hasAudioAccess() = access
        override suspend fun tracks() = tracks
        override suspend fun albums() = albums
        override suspend fun playlists() = playlists
        override suspend fun tracksForAlbum(albumId: Long) = tracks.filter { it.albumId == albumId }
        override suspend fun tracksForPlaylist(playlistId: Long) = playlistTracks[playlistId].orEmpty()
        override suspend fun subscriptions() = subs
        override suspend fun recentEpisodes() = recentEps
        override suspend fun favoriteStations() = favStations
        override suspend fun recentStations() = recentStns
        override suspend fun show(feedUrl: String) = shows[feedUrl]
    }

    private fun kids(id: AutoMediaId, src: AutoLibrarySource) = runBlocking { AutoBrowseTree.children(id, src) }

    private val sample = Fake(
        tracks = listOf(t(1, "Zebra", "Bee", 1), t(2, "apple", "Ant", 2), t(3, "Mango", "ant", 2)),
        albums = listOf(LocalAlbum(2, "Zed", "Ant", 2), LocalAlbum(1, "Alpha", "Bee", 1)),
        playlists = listOf(LocalPlaylist(9, "road"), LocalPlaylist(8, "Chill")),
        playlistTracks = mapOf(9L to listOf(t(3, "Mango", "ant", 2), t(1, "Zebra", "Bee", 1))),
    )

    @Test fun rootIsFourTabsInOrder() {
        assertEquals(
            listOf("Library", "Playlists", "Podcasts", "Radio"),
            kids(AutoMediaId.Root, sample).map { it.title },
        )
        assertTrue(kids(AutoMediaId.Root, sample).all { it.kind == AutoItemKind.BROWSABLE })
    }

    @Test fun libraryTabHasThreeRowsWithCounts() {
        val rows = kids(AutoMediaId.Tab(AutoTab.LIBRARY), sample)
        assertEquals(listOf("Artists", "Albums", "All songs"), rows.map { it.title })
        assertEquals(listOf("3 artists", "2 albums", "3 songs"), rows.map { it.subtitle })
    }

    @Test fun missingPermissionAndEmptyLibraryGiveOneMessageRow() {
        val noAccess = kids(AutoMediaId.Tab(AutoTab.LIBRARY), Fake(access = false))
        assertEquals(listOf(AutoMediaId.Message(AutoBrowseTree.MSG_NO_ACCESS)), noAccess.map { it.id })
        val empty = kids(AutoMediaId.Tab(AutoTab.LIBRARY), Fake())
        assertEquals(listOf(AutoMediaId.Message(AutoBrowseTree.MSG_EMPTY_LIBRARY)), empty.map { it.id })
        assertEquals(AutoItemKind.INFO, empty.single().kind)
        assertEquals(1, kids(AutoMediaId.Tab(AutoTab.PLAYLISTS), Fake()).size)
    }

    @Test fun playlistsSortedIgnoringCase() {
        assertEquals(listOf("Chill", "road"), kids(AutoMediaId.Tab(AutoTab.PLAYLISTS), sample).map { it.title })
    }

    @Test fun artistsAndAlbumsSorted() {
        assertEquals(listOf("Ant", "ant", "Bee"), kids(AutoMediaId.LibArtists, sample).map { it.title }.let { it })
        assertEquals(listOf("Alpha", "Zed"), kids(AutoMediaId.LibAlbums, sample).map { it.title })
    }

    @Test fun songListsStartWithPlayAllAndShuffle() {
        val rows = kids(AutoMediaId.Album(2), sample)
        assertEquals("Play all", rows[0].title)
        assertEquals("Shuffle", rows[1].title)
        assertEquals(2, rows.count { it.kind == AutoItemKind.PLAYABLE && it.id is AutoMediaId.Track })
    }

    @Test fun allSongsAreCappedWithAMessage() {
        val many = Fake(tracks = (1L..250L).map { t(it, "song%03d".format(it), "a") })
        val rows = kids(AutoMediaId.LibSongs, many)
        assertEquals(2 + AutoBrowseTree.MAX_ITEMS + 1, rows.size)
        assertEquals(AutoMediaId.Message(AutoBrowseTree.MSG_MORE_SONGS), rows.last().id)
    }

    @Test fun podcastAndRadioTabsAreNeverEmpty() {
        assertEquals(AutoMediaId.Message(AutoBrowseTree.MSG_NO_PODCASTS), kids(AutoMediaId.Tab(AutoTab.PODCASTS), sample).single().id)
        assertEquals(AutoMediaId.Message(AutoBrowseTree.MSG_NO_STATIONS), kids(AutoMediaId.Tab(AutoTab.RADIO), sample).single().id)
    }

    @Test fun trackPlaysItsContainerFromThatPosition() {
        val req = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Track(AutoMediaId.Playlist(9), 1), sample) }
        assertNotNull(req)
        assertEquals(listOf(3L, 1L), (req!! as AutoPlayRequest.Tracks).tracks.map { it.id })
        assertEquals(1, req.startIndex)
    }

    @Test fun allSongsQueueFollowsTheListedOrder() {
        val req = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Track(AutoMediaId.LibSongs, 3), sample) } as AutoPlayRequest.Tracks
        assertEquals(listOf("apple", "Mango", "Zebra"), req.tracks.map { it.title })
        assertEquals(1, req.startIndex)
    }

    @Test fun playAllShuffleAndUnplayable() {
        val all = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.PlayAll(AutoMediaId.Album(2)), sample) } as AutoPlayRequest.Tracks
        assertEquals(0, all.startIndex)
        val shuffled = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Shuffle(AutoMediaId.LibSongs), sample, Random(1)) } as AutoPlayRequest.Tracks
        assertEquals(setOf(1L, 2L, 3L), shuffled.tracks.map { it.id }.toSet())
        assertNull(runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Tab(AutoTab.LIBRARY), sample) })
        assertNull(runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Track(AutoMediaId.Album(2), 1), sample) })
    }

    // ---- podcasts and radio

    private val day = 86_400_000L
    private val now = 100 * day
    private val feed = "https://x.com/feed"
    private fun ep(n: Int, ageDays: Int, durMin: Int? = 60) =
        PodcastEpisode("g$n", "Ep $n", "", "https://x.com/$n.mp3", durMin?.let { it * 60_000L }, now - ageDays * day, null)
    private val sub = PodcastSubscription(feed, "The Show", "https://x.com/a.jpg", 1)
    private fun podcastFake(count: Int = 3) = Fake(
        subs = listOf(sub),
        recentEps = listOf(RecentEpisode(sub, ep(2, 5), now)),
        shows = mapOf(feed to PodcastShow(feed, "The Show", "", null, (1..count).map { ep(it, count - it) })),
    )
    private fun podcastKids(id: AutoMediaId, src: Fake) = runBlocking { AutoBrowseTree.children(id, src, now) }

    @Test fun podcastsTabHasFavoritesThenRecentlyPlayed() {
        val rows = podcastKids(AutoMediaId.Tab(AutoTab.PODCASTS), podcastFake())
        assertEquals(listOf("Favorites", "Recently played"), rows.map { it.group })
        assertEquals(AutoMediaId.Show(feed), rows[0].id)
        assertEquals(AutoItemKind.BROWSABLE, rows[0].kind)
        assertEquals(AutoMediaId.Episode(feed, "g2"), rows[1].id)
        assertEquals(AutoItemKind.PLAYABLE, rows[1].kind)
    }

    @Test fun recentEpisodesCappedAtTen() {
        val recents = (1..15).map { RecentEpisode(sub, ep(it, it), now) }
        val rows = podcastKids(AutoMediaId.Tab(AutoTab.PODCASTS), Fake(recentEps = recents))
        assertEquals(10, rows.size)
    }

    @Test fun showListsPlayLatestThenEpisodesNewestFirst() {
        val rows = podcastKids(AutoMediaId.Show(feed), podcastFake())
        assertEquals(AutoMediaId.PlayLatest(feed), rows[0].id)
        assertEquals(listOf("Ep 3", "Ep 2", "Ep 1"), rows.drop(1).map { it.title })
        assertEquals("today · 1h 0m", rows[1].subtitle)
        assertEquals("yesterday · 1h 0m", rows[2].subtitle)
    }

    @Test fun longShowsPageWithMoreEpisodesRow() {
        val src = podcastFake(count = 45)
        val first = podcastKids(AutoMediaId.Show(feed), src)
        assertEquals(1 + AutoBrowseTree.EPISODE_PAGE_SIZE + 1, first.size)
        assertEquals(AutoMediaId.EpisodesPage(feed, 1), first.last().id)
        val second = podcastKids(AutoMediaId.EpisodesPage(feed, 1), src)
        assertEquals("Ep 25", second[0].title)
        assertEquals(AutoMediaId.EpisodesPage(feed, 2), second.last().id)
        val third = podcastKids(AutoMediaId.EpisodesPage(feed, 2), src)
        assertEquals(5, third.size)
        assertEquals(AutoItemKind.PLAYABLE, third.last().kind)
    }

    @Test fun unreachableFeedGivesOneMessageRow() {
        val rows = podcastKids(AutoMediaId.Show("https://other"), podcastFake())
        assertEquals(AutoMediaId.Message(AutoBrowseTree.MSG_OFFLINE), rows.single().id)
    }

    @Test fun episodePickQueuesNewestFirstFromThatEpisode() {
        val req = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Episode(feed, "g2"), podcastFake()) } as AutoPlayRequest.Episodes
        assertEquals(listOf("g3", "g2", "g1"), req.episodes.map { it.guid })
        assertEquals(1, req.startIndex)
        assertEquals(sub, req.show)
    }

    @Test fun playLatestStartsAtTheNewest() {
        val req = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.PlayLatest(feed), podcastFake()) } as AutoPlayRequest.Episodes
        assertEquals("g3", req.episodes[req.startIndex].guid)
    }

    @Test fun recentEpisodePlaysEvenWhenTheFeedIsDown() {
        val src = Fake(recentEps = listOf(RecentEpisode(sub, ep(2, 5), now)))
        val req = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Episode(feed, "g2"), src) } as AutoPlayRequest.Episodes
        assertEquals(listOf("g2"), req.episodes.map { it.guid })
        assertNull(runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Episode(feed, "zzz"), src) })
    }

    private fun st(id: String, name: String) = FavoriteStation(id, name, "https://s/$id", null, 1)

    @Test fun radioTabHasFavoritesThenRecents() {
        val src = Fake(favStations = listOf(st("a", "A"), st("b", "B")), recentStns = listOf(st("c", "C")))
        val rows = podcastKids(AutoMediaId.Tab(AutoTab.RADIO), src)
        assertEquals(listOf("A", "B", "C"), rows.map { it.title })
        assertEquals(listOf("Favorites", "Favorites", "Recently played"), rows.map { it.group })
        assertTrue(rows.all { it.kind == AutoItemKind.PLAYABLE })
    }

    @Test fun stationPlaysWithFavoritesAsQueue() {
        val src = Fake(favStations = listOf(st("a", "A"), st("b", "B")), recentStns = listOf(st("c", "C")))
        val fav = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Station("b"), src) } as AutoPlayRequest.Station
        assertEquals("b", fav.station.stationId)
        assertEquals(listOf("a", "b"), fav.favorites.map { it.stationId })
        val recent = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Station("c"), src) } as AutoPlayRequest.Station
        assertEquals("c", recent.station.stationId)
        assertNull(runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Station("zz"), src) })
    }

    @Test fun ageAndDurationLabels() {
        assertEquals("today", AutoBrowseTree.relativeAge(now - 1000, now))
        assertEquals("3 days ago", AutoBrowseTree.relativeAge(now - 3 * day, now))
        assertEquals("3 weeks ago", AutoBrowseTree.relativeAge(now - 23 * day, now))
        assertEquals("4 months ago", AutoBrowseTree.relativeAge(400 * day - 125 * day, 400 * day))
        assertNull(AutoBrowseTree.relativeAge(0, now))
        assertEquals("47m", AutoBrowseTree.durationLabel(47 * 60_000L))
        assertEquals("1h 12m", AutoBrowseTree.durationLabel(72 * 60_000L))
        assertNull(AutoBrowseTree.durationLabel(20_000))
        assertNull(AutoBrowseTree.episodeSubtitle(0, null, now))
    }
}
