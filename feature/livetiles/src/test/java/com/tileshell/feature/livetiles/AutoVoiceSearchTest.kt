package com.tileshell.feature.livetiles

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AutoVoiceSearchTest {
    private fun t(id: Long, title: String, artist: String, albumId: Long) = LocalTrack(id, title, artist, "a$albumId", albumId, 1000)
    private val feed = "https://x.com/f"
    private val sub = PodcastSubscription(feed, "The Ranveer Show", null, 1)

    private val src = object : AutoLibrarySource {
        private val all = listOf(t(1, "Tum Hi Ho", "Arijit Singh", 1), t(2, "Kesariya", "Arijit Singh", 2), t(3, "Arijit", "Other", 3))
        override suspend fun hasAudioAccess() = true
        override suspend fun tracks() = all
        override suspend fun albums() = listOf(LocalAlbum(1, "Aashiqui 2", "Arijit Singh", 1), LocalAlbum(2, "Brahmastra", "Pritam", 1))
        override suspend fun playlists() = emptyList<LocalPlaylist>()
        override suspend fun tracksForAlbum(albumId: Long) = all.filter { it.albumId == albumId }
        override suspend fun tracksForPlaylist(playlistId: Long) = emptyList<LocalTrack>()
        override suspend fun subscriptions() = listOf(sub)
        override suspend fun favoriteStations() = listOf(FavoriteStation("r1", "Radio City", "https://s/r1", null, 1))
        override suspend fun show(feedUrl: String) = PodcastShow(
            feedUrl, "The Ranveer Show", "", null,
            listOf(PodcastEpisode("g1", "Ep 1", "", "https://x/1.mp3", null, 1, null), PodcastEpisode("g2", "Ep 2", "", "https://x/2.mp3", null, 2, null)),
        )
    }

    private fun r(q: String) = runBlocking { AutoVoiceSearch.resolve(q, src, Random(1)) }

    @Test fun favoriteShowPlaysItsLatestEpisode() {
        val req = r("ranveer show") as AutoPlayRequest.Episodes
        assertEquals("g2", req.episodes[req.startIndex].guid)
    }

    @Test fun favoriteStationMatches() {
        assertEquals("r1", (r("RADIO city") as AutoPlayRequest.Station).station.stationId)
    }

    @Test fun artistBeatsSongWithTheSameWords() {
        val req = r("arijit") as AutoPlayRequest.Tracks
        assertEquals(setOf(1L, 2L), req.tracks.map { it.id }.toSet())
    }

    @Test fun albumThenSong() {
        assertEquals(listOf(1L), (r("aashiqui") as AutoPlayRequest.Tracks).tracks.map { it.id })
        val song = r("kesariya") as AutoPlayRequest.Tracks
        assertEquals(2L, song.tracks[song.startIndex].id)
    }

    @Test fun emptyQueryShufflesTheLibrary() {
        assertEquals(3, (r("  ") as AutoPlayRequest.Tracks).tracks.size)
    }

    @Test fun noMatchIsNull() = assertNull(r("zzzz nothing"))

    @Test fun exactNameBeatsPrefixMatch() {
        val req = r("arijit singh") as AutoPlayRequest.Tracks
        assertTrue(req.tracks.all { it.artist == "Arijit Singh" })
    }
}
