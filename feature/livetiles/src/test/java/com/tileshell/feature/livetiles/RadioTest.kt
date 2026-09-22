package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationParsingTest {

    @Test
    fun `parses a typical radio-browser response`() {
        val json = """
            [{
                "stationuuid":"abc-123",
                "name":"Test FM",
                "url":"http://redirect.example/stream",
                "url_resolved":"https://stream.example/live.mp3",
                "favicon":"https://example.com/logo.png",
                "country":"India",
                "tags":"pop,talk"
            }]
        """.trimIndent()
        val stations = parseRadioStations(json)
        assertEquals(1, stations.size)
        val s = stations[0]
        assertEquals("abc-123", s.stationId)
        assertEquals("Test FM", s.name)
        // Prefers the already-resolved stream url over the raw (possibly
        // redirecting) one.
        assertEquals("https://stream.example/live.mp3", s.streamUrl)
        assertEquals("https://example.com/logo.png", s.faviconUrl)
        assertEquals("India", s.country)
        assertEquals("pop,talk", s.tags)
    }

    @Test
    fun `falls back to the raw url when url_resolved is absent`() {
        val json = """[{"name":"Test","url":"https://x/stream"}]"""
        assertEquals("https://x/stream", parseRadioStations(json).single().streamUrl)
    }

    @Test
    fun `a station with no name or url is skipped`() {
        val json = """[{"country":"India"}]"""
        assertTrue(parseRadioStations(json).isEmpty())
    }

    @Test
    fun `malformed json degrades to an empty list, not a crash`() {
        assertEquals(emptyList<RadioStation>(), parseRadioStations("not json"))
    }

    @Test
    fun `an empty array yields an empty list`() {
        assertEquals(emptyList<RadioStation>(), parseRadioStations("[]"))
    }
}

class FavoriteStationCodecTest {

    private fun station(id: String = "s1", favicon: String? = "https://x/icon.png") = FavoriteStation(
        stationId = id,
        name = "Test FM",
        streamUrl = "https://x/stream.mp3",
        faviconUrl = favicon,
        favoritedAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun `a favorite round-trips`() {
        val list = listOf(station())
        assertEquals(list, FavoriteStationCodec.decode(FavoriteStationCodec.encode(list)))
    }

    @Test
    fun `a null favicon round-trips as null, not an empty string`() {
        val list = listOf(station(favicon = null))
        assertNull(FavoriteStationCodec.decode(FavoriteStationCodec.encode(list)).single().faviconUrl)
    }

    @Test
    fun `several favorites round-trip in order`() {
        val list = listOf(station(id = "a"), station(id = "b"))
        assertEquals(list, FavoriteStationCodec.decode(FavoriteStationCodec.encode(list)))
    }

    @Test
    fun `a malformed line is skipped, not thrown`() {
        assertEquals(emptyList<FavoriteStation>(), FavoriteStationCodec.decode("not|enough|fields"))
    }
}
