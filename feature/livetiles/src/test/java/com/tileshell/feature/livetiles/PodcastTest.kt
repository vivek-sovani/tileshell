package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastSearchResultParsingTest {

    @Test
    fun `parses a typical iTunes search response`() {
        val json = """
            {"resultCount":1,"results":[{
                "collectionName":"Radiolab",
                "artistName":"WNYC Studios",
                "feedUrl":"https://feeds.wnyc.org/radiolab",
                "artworkUrl100":"https://example.com/100.jpg",
                "artworkUrl600":"https://example.com/600.jpg"
            }]}
        """.trimIndent()
        val results = parsePodcastSearchResults(json)
        assertEquals(1, results.size)
        assertEquals("Radiolab", results[0].title)
        assertEquals("WNYC Studios", results[0].author)
        assertEquals("https://feeds.wnyc.org/radiolab", results[0].feedUrl)
        assertEquals("https://example.com/600.jpg", results[0].artworkUrl)
    }

    @Test
    fun `falls back to the 100px artwork when 600px is absent`() {
        val json = """{"results":[{"collectionName":"Show","feedUrl":"https://x/feed","artworkUrl100":"https://x/100.jpg"}]}"""
        assertEquals("https://x/100.jpg", parsePodcastSearchResults(json).single().artworkUrl)
    }

    @Test
    fun `a result with no feed url is skipped`() {
        val json = """{"results":[{"collectionName":"Show","artistName":"x"}]}"""
        assertTrue(parsePodcastSearchResults(json).isEmpty())
    }

    @Test
    fun `malformed json degrades to an empty list, not a crash`() {
        assertEquals(emptyList<PodcastSearchResult>(), parsePodcastSearchResults("not json at all"))
    }

    @Test
    fun `an empty results array yields an empty list`() {
        assertEquals(emptyList<PodcastSearchResult>(), parsePodcastSearchResults("""{"results":[]}"""))
    }
}

class PodcastChartParsingTest {

    @Test
    fun `extracts collection ids from a top-podcasts chart response`() {
        val json = """
            {"feed":{"entry":[
                {"id":{"label":"https://x","attributes":{"im:id":"111"}}},
                {"id":{"label":"https://x","attributes":{"im:id":"222"}}}
            ]}}
        """.trimIndent()
        assertEquals(listOf("111", "222"), parseChartTrackIds(json))
    }

    @Test
    fun `malformed chart json degrades to an empty list`() {
        assertEquals(emptyList<String>(), parseChartTrackIds("not json"))
    }

    @Test
    fun `an empty entry array yields an empty list`() {
        assertEquals(emptyList<String>(), parseChartTrackIds("""{"feed":{"entry":[]}}"""))
    }
}

class PodcastFeedParsingTest {

    @Test
    fun `parses channel metadata and episodes with enclosures`() {
        val xml = """
            <?xml version="1.0"?>
            <rss><channel>
                <title>Test Show</title>
                <description>A show about tests</description>
                <itunes:image href="https://x/show.jpg" />
                <item>
                    <title>Episode One</title>
                    <description>The first episode</description>
                    <pubDate>Mon, 01 Jan 2024 12:00:00 GMT</pubDate>
                    <itunes:duration>01:02:03</itunes:duration>
                    <guid>ep-1</guid>
                    <enclosure url="https://x/ep1.mp3" type="audio/mpeg" />
                </item>
                <item>
                    <title>Episode Two</title>
                    <pubDate>Tue, 02 Jan 2024 12:00:00 GMT</pubDate>
                    <enclosure url="https://x/ep2.mp3" />
                </item>
            </channel></rss>
        """.trimIndent()
        val show = parsePodcastFeed(xml, "https://x/feed")
        requireNotNull(show)
        assertEquals("Test Show", show.title)
        assertEquals("A show about tests", show.description)
        assertEquals("https://x/show.jpg", show.imageUrl)
        assertEquals(2, show.episodes.size)

        val ep1 = show.episodes[0]
        assertEquals("Episode One", ep1.title)
        assertEquals("ep-1", ep1.guid)
        assertEquals("https://x/ep1.mp3", ep1.audioUrl)
        assertEquals(3723_000L, ep1.durationMs) // 1h2m3s
        assertTrue(ep1.publishedMillis > 0)

        // No itunes:duration and no explicit guid — falls back to the audio url.
        val ep2 = show.episodes[1]
        assertEquals("https://x/ep2.mp3", ep2.guid)
        assertNull(ep2.durationMs)
    }

    @Test
    fun `an item with no audio enclosure is skipped, not crashed on`() {
        val xml = """
            <rss><channel><title>Show</title>
                <item><title>Text-only post</title></item>
            </channel></rss>
        """.trimIndent()
        val show = parsePodcastFeed(xml, "https://x/feed")
        assertEquals(0, show?.episodes?.size)
    }

    @Test
    fun `malformed xml yields null, not a crash`() {
        assertNull(parsePodcastFeed("<not><valid", "https://x/feed"))
    }

    @Test
    fun `a channel with no title yields null`() {
        assertNull(parsePodcastFeed("<rss><channel></channel></rss>", "https://x/feed"))
    }
}

class ItunesDurationParsingTest {

    @Test
    fun `parses hh mm ss`() {
        assertEquals(3723_000L, parseItunesDuration("01:02:03"))
    }

    @Test
    fun `parses mm ss`() {
        assertEquals(125_000L, parseItunesDuration("2:05"))
    }

    @Test
    fun `parses a plain seconds count`() {
        assertEquals(90_000L, parseItunesDuration("90"))
    }

    @Test
    fun `blank or unparsable input yields null`() {
        assertNull(parseItunesDuration(null))
        assertNull(parseItunesDuration(""))
        assertNull(parseItunesDuration("not a duration"))
    }
}

class PodcastSubscriptionCodecTest {

    private fun sub(feedUrl: String = "https://x/feed", title: String = "Show", art: String? = "https://x/art.jpg") =
        PodcastSubscription(feedUrl = feedUrl, title = title, artworkUrl = art, subscribedAtMillis = 1_700_000_000_000L)

    @Test
    fun `a subscription round-trips`() {
        val list = listOf(sub())
        assertEquals(list, PodcastSubscriptionCodec.decode(PodcastSubscriptionCodec.encode(list)))
    }

    @Test
    fun `a null artwork url round-trips as null, not an empty string`() {
        val list = listOf(sub(art = null))
        assertNull(PodcastSubscriptionCodec.decode(PodcastSubscriptionCodec.encode(list)).single().artworkUrl)
    }

    @Test
    fun `several subscriptions round-trip in order`() {
        val list = listOf(sub(feedUrl = "https://a"), sub(feedUrl = "https://b", title = "Other"))
        assertEquals(list, PodcastSubscriptionCodec.decode(PodcastSubscriptionCodec.encode(list)))
    }

    @Test
    fun `a malformed line is skipped, not thrown`() {
        assertEquals(emptyList<PodcastSubscription>(), PodcastSubscriptionCodec.decode("not|enough"))
    }
}
