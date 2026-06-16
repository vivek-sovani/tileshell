package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the [FeedCodec] round-trip and its tolerance to bad input. */
class FeedCodecTest {

    @Test
    fun `round-trips sources and articles`() {
        val data = FeedData(
            sources = listOf(
                FeedSource("https://a.com/rss", "A News", "nation", enabled = true),
                FeedSource("https://b.com/rss", "B News", "custom", enabled = false),
            ),
            articles = listOf(
                FeedArticle("Title one", "https://a.com/1", "A News", "tech", "https://a.com/1.jpg", 1234L),
                FeedArticle("Title two", "https://b.com/2", "B News", "news", null, 0L),
            ),
        )
        assertEquals(data, FeedCodec.decode(FeedCodec.encode(data)))
    }

    @Test
    fun `tabs and newlines in fields are sanitised`() {
        val data = FeedData(
            sources = emptyList(),
            articles = listOf(FeedArticle("a\tb\nc", "l", "s", "t", null, 5L)),
        )
        val decoded = FeedCodec.decode(FeedCodec.encode(data))
        assertEquals("a b c", decoded.articles.single().title)
    }

    @Test
    fun `malformed lines are skipped`() {
        val decoded = FeedCodec.decode("garbage\nS\t1\t\tNoUrl\nS\t1\thttps://ok.com\tOk")
        assertEquals(1, decoded.sources.size)
        assertEquals("https://ok.com", decoded.sources.single().url)
    }

    @Test
    fun `empty text decodes to empty lists`() {
        val decoded = FeedCodec.decode("")
        assertEquals(emptyList<FeedSource>(), decoded.sources)
        assertEquals(emptyList<FeedArticle>(), decoded.articles)
    }

    @Test
    fun `merge dedups by link sorts newest first and caps`() {
        val a1 = FeedArticle("old", "https://x/1", "s", "t", null, 100L)
        val a2 = FeedArticle("new", "https://x/2", "s", "t", null, 300L)
        val a2dup = a2.copy(title = "new again")
        val a3 = FeedArticle("mid", "https://x/3", "s", "t", null, 200L)
        val merged = mergeFeedArticles(listOf(listOf(a1, a2), listOf(a2dup, a3)), cap = 2)
        assertEquals(listOf("https://x/2", "https://x/3"), merged.map { it.link }) // newest 2, deduped
    }
}
