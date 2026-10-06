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
    ) : AutoLibrarySource {
        override suspend fun hasAudioAccess() = access
        override suspend fun tracks() = tracks
        override suspend fun albums() = albums
        override suspend fun playlists() = playlists
        override suspend fun tracksForAlbum(albumId: Long) = tracks.filter { it.albumId == albumId }
        override suspend fun tracksForPlaylist(playlistId: Long) = playlistTracks[playlistId].orEmpty()
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
        assertEquals(1, kids(AutoMediaId.Tab(AutoTab.PODCASTS), sample).size)
        assertEquals(1, kids(AutoMediaId.Tab(AutoTab.RADIO), sample).size)
    }

    @Test fun trackPlaysItsContainerFromThatPosition() {
        val req = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Track(AutoMediaId.Playlist(9), 1), sample) }
        assertNotNull(req)
        assertEquals(listOf(3L, 1L), req!!.tracks.map { it.id })
        assertEquals(1, req.startIndex)
    }

    @Test fun allSongsQueueFollowsTheListedOrder() {
        val req = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Track(AutoMediaId.LibSongs, 3), sample) }!!
        assertEquals(listOf("apple", "Mango", "Zebra"), req.tracks.map { it.title })
        assertEquals(1, req.startIndex)
    }

    @Test fun playAllShuffleAndUnplayable() {
        val all = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.PlayAll(AutoMediaId.Album(2)), sample) }!!
        assertEquals(0, all.startIndex)
        val shuffled = runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Shuffle(AutoMediaId.LibSongs), sample, Random(1)) }!!
        assertEquals(setOf(1L, 2L, 3L), shuffled.tracks.map { it.id }.toSet())
        assertNull(runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Tab(AutoTab.LIBRARY), sample) })
        assertNull(runBlocking { AutoBrowseTree.resolvePlay(AutoMediaId.Track(AutoMediaId.Album(2), 1), sample) })
    }
}
