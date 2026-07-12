package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure RSS/Atom parser and feed formatters. */
class RssFeedTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <title>The Hindu</title>
            <item>
              <title>Budget &amp; markets react</title>
              <link>https://example.com/a</link>
              <description><![CDATA[<p>Some <b>html</b> snippet</p>]]></description>
              <category>business</category>
              <pubDate>Wed, 02 Oct 2024 13:00:00 +0530</pubDate>
              <media:content url="https://img.example.com/a.jpg"/>
            </item>
            <item>
              <title>Second story</title>
              <link>https://example.com/b</link>
              <enclosure url="https://img.example.com/b.jpg" type="image/jpeg"/>
              <pubDate>Wed, 02 Oct 2024 10:00:00 +0530</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private val atom = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Feed Title</title>
          <entry>
            <title>Atom entry one</title>
            <link rel="alternate" href="https://example.com/atom1"/>
            <published>2024-10-02T13:00:00Z</published>
            <summary>plain summary</summary>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `parses rss items with title link image category`() {
        val articles = parseFeed(rss, "fallback")
        assertEquals(2, articles.size)
        val first = articles[0]
        assertEquals("Budget & markets react", first.title) // entity unescaped
        assertEquals("https://example.com/a", first.link)
        assertEquals("The Hindu", first.source)             // channel title wins
        assertEquals("business", first.tag)
        assertEquals("https://img.example.com/a.jpg", first.imageUrl)
        assertTrue(first.publishedAtMillis > 0)
        assertEquals("https://img.example.com/b.jpg", articles[1].imageUrl) // enclosure
    }

    @Test
    fun `parses atom entries with href link and fallback source`() {
        val articles = parseFeed(atom, "fallback")
        assertEquals(1, articles.size)
        assertEquals("Atom entry one", articles[0].title)
        assertEquals("https://example.com/atom1", articles[0].link)
        assertEquals("Feed Title", articles[0].source)
        assertTrue(articles[0].publishedAtMillis > 0)
    }

    @Test
    fun `image falls back to content-encoded img and normalises protocol-relative`() {
        val feed = """
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>C</title>
                <item>
                  <title>Has inline image</title>
                  <link>https://e.com/x</link>
                  <content:encoded><![CDATA[<p>hi</p><img src="//cdn.e.com/p.jpg"/>]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        assertEquals("https://cdn.e.com/p.jpg", parseFeed(feed, "C").single().imageUrl)
    }

    @Test
    fun `cricinfo style media-content upgrades http to https`() {
        val feed = """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Cric</title>
                <item>
                  <title>Match preview</title>
                  <link>https://e.com/m</link>
                  <media:content medium="image" url="http://p.imgci.com/db/x.jpg" width="1400"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        assertEquals("https://p.imgci.com/db/x.jpg", parseFeed(feed, "Cric").single().imageUrl)
    }

    @Test
    fun `malformed xml yields empty list`() {
        assertEquals(emptyList<FeedArticle>(), parseFeed("not xml <<<", "x"))
    }

    @Test
    fun `strips html and entities`() {
        assertEquals("a & b", stripHtml("<p>a &amp; b</p>"))
        assertEquals("hi there", stripHtml("hi&nbsp;&nbsp;there"))
        assertEquals("", stripHtml(null))
    }

    @Test
    fun `feed date parses rfc822 and rfc3339`() {
        assertTrue((parseFeedDate("Wed, 02 Oct 2024 13:00:00 +0530") ?: 0) > 0)
        assertTrue((parseFeedDate("2024-10-02T13:00:00Z") ?: 0) > 0)
        assertNull(parseFeedDate("garbage"))
        assertNull(parseFeedDate(null))
    }

    @Test
    fun `feed ago buckets by minute hour day`() {
        val now = 10_000_000_000L
        assertEquals("now", feedAgo(now, now))
        assertEquals("5m", feedAgo(now - 5 * 60_000L, now))
        assertEquals("3h", feedAgo(now - 3 * 3_600_000L, now))
        assertEquals("2d", feedAgo(now - 2 * 86_400_000L, now))
        assertEquals("now", feedAgo(0L, now)) // missing date
    }

    @Test
    fun `default feed sources for country picks india list only for IN`() {
        assertEquals(DEFAULT_FEED_SOURCES, defaultFeedSourcesForCountry("IN"))
        assertEquals(DEFAULT_FEED_SOURCES, defaultFeedSourcesForCountry("in")) // case-insensitive
        assertEquals(countryFeedSources("US"), defaultFeedSourcesForCountry("US"))
        assertEquals(countryFeedSources("GB"), defaultFeedSourcesForCountry("GB"))
        assertEquals(INTERNATIONAL_FEED_SOURCES, defaultFeedSourcesForCountry("ZZ")) // unsupported code
        assertEquals(INTERNATIONAL_FEED_SOURCES, defaultFeedSourcesForCountry("")) // unresolved locale
    }

    @Test
    fun `country feed sources are per-country google news urls tagged by category`() {
        val sources = countryFeedSources("us")
        assertEquals(5, sources.size)
        assertTrue(sources.all { it.url.startsWith("https://news.google.com/rss") })
        assertTrue(sources.all { it.url.contains("gl=US") && it.url.contains("ceid=US:en") })
        assertEquals(setOf("nation", "entertainment", "sports", "tech", "business"), sources.map { it.category }.toSet())
        assertEquals("Google News · United States", sources.first { it.category == "nation" }.name)
    }

    @Test
    fun `region display name covers india international and named countries`() {
        assertEquals("India", regionDisplayName("IN"))
        assertEquals("International", regionDisplayName("INTL"))
        assertEquals("United States", regionDisplayName("US"))
        assertEquals("XX", regionDisplayName("XX")) // unknown code falls back to itself
    }
}
