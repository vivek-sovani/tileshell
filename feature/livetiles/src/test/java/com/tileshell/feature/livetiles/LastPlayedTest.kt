package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class LastPlayedTest {
    private fun roundTrip(item: PlayableAudio) = LastPlayed.decode(LastPlayed.encode(item))

    @Test fun localTrack() {
        val item = PlayableAudio.Local(LocalTrack(7, "Song", "Artist", "Album", 3, 1000))
        assertEquals(item, roundTrip(item))
    }

    @Test fun episode() {
        val item = PlayableAudio.Episode(
            PodcastSubscription("https://f/x", "Show", null, 5),
            PodcastEpisode("g1", "Ep", "d", "https://a/b.mp3", null, 99, "https://i/j.png"),
        )
        assertEquals(item, roundTrip(item))
    }

    @Test fun radio() {
        val item = PlayableAudio.RadioStream(RadioStationRef("s1", "Station", "https://r/s", null))
        assertEquals(item, roundTrip(item))
    }
}
