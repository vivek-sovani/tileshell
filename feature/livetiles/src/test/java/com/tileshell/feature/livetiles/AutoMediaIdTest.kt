package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoMediaIdTest {
    private fun roundTrip(id: AutoMediaId) = assertEquals(id, AutoMediaId.parse(id.encode()))

    @Test fun simpleIdsRoundTrip() {
        listOf(
            AutoMediaId.Root, AutoMediaId.LibArtists, AutoMediaId.LibAlbums, AutoMediaId.LibSongs,
            AutoMediaId.Album(12), AutoMediaId.Playlist(5),
        ).forEach(::roundTrip)
        AutoTab.values().forEach { roundTrip(AutoMediaId.Tab(it)) }
    }

    @Test fun namesWithSeparatorsRoundTrip() {
        roundTrip(AutoMediaId.Artist("AC/DC | live & more: 100%"))
        roundTrip(AutoMediaId.Show("https://x.com/feed?a=1&b=/2"))
        roundTrip(AutoMediaId.Episode("https://x.com/f|g", "guid:1/2 3"))
        roundTrip(AutoMediaId.Station("abc/def"))
        roundTrip(AutoMediaId.Message("no_access"))
    }

    @Test fun tracksCarryTheirContainer() {
        roundTrip(AutoMediaId.Track(AutoMediaId.Album(7), 99))
        roundTrip(AutoMediaId.Track(AutoMediaId.Artist("A/B"), 1))
        roundTrip(AutoMediaId.Track(AutoMediaId.LibSongs, 3))
        roundTrip(AutoMediaId.PlayAll(AutoMediaId.Playlist(2)))
        roundTrip(AutoMediaId.Shuffle(AutoMediaId.Album(2)))
    }

    @Test fun malformedIdsAreNull() {
        listOf("", "nope", "album", "album/x", "album/1/2", "tab/zzz", "lib/other", "track/album%2F1", "root/x")
            .forEach { assertNull(it, AutoMediaId.parse(it)) }
    }

    @Test fun onlySongListsAreContainers() {
        val bad = AutoMediaId.Tab(AutoTab.RADIO).encode()
        assertNull(AutoMediaId.parse("all/" + java.net.URLEncoder.encode(bad, "UTF-8")))
    }
}
