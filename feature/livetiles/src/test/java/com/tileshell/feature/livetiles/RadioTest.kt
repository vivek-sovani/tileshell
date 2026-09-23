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

class RadioMultiWordFallbackTest {

    private fun station(id: String, name: String, tags: String = "") =
        RadioStation(stationId = id, name = name, streamUrl = "https://x/$id", faviconUrl = null, country = "", tags = tags)

    @Test
    fun `short filler words are dropped from the fallback word list`() {
        assertEquals(listOf("hindi", "desi", "bollywood"), significantQueryWords("hindi desi bollywood"))
        assertEquals(listOf("bollywood", "hits"), significantQueryWords("of bollywood hits"))
    }

    @Test
    fun `duplicate words are only searched once`() {
        assertEquals(listOf("desi"), significantQueryWords("desi desi"))
    }

    @Test
    fun `a single-word query has no multi-word fallback`() {
        assertEquals(listOf("bollywood"), significantQueryWords("bollywood"))
    }

    @Test
    fun `merged results are deduped by station id, first occurrence wins`() {
        val a = station("1", "Desi Hits Radio")
        val dup = station("1", "duplicate name should be ignored")
        val b = station("2", "Bollywood FM")
        val merged = rankMergedStations(listOf(listOf(a), listOf(dup, b)), listOf("desi", "bollywood"))
        assertEquals(2, merged.size)
        assertEquals("Desi Hits Radio", merged.first { it.stationId == "1" }.name)
    }

    @Test
    fun `a station matching every query word ranks above one matching only one`() {
        val allWords = station("both", "Hindi Desi Bollywood Hits")
        val oneWord = station("one", "Hindi FM")
        val ranked = rankMergedStations(listOf(listOf(oneWord, allWords)), listOf("hindi", "desi", "bollywood"))
        assertEquals(listOf("both", "one"), ranked.map { it.stationId })
    }

    @Test
    fun `a tag match counts the same as a name match`() {
        val byTag = station("tag", "Generic FM", tags = "bollywood,desi")
        val byNameOnly = station("name", "Bollywood Radio")
        val ranked = rankMergedStations(listOf(listOf(byNameOnly, byTag)), listOf("bollywood", "desi"))
        assertEquals("tag", ranked.first().stationId)
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
