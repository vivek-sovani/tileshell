package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsHubTest {

    @Test
    fun `starter channels are those of the followed regions`() {
        val shown = effectiveLiveChannels(setOf("IN"), null, emptyList())
        assertTrue(shown.isNotEmpty())
        assertTrue(shown.all { it.region == "IN" && it.default })
        assertTrue(shown.any { it.handle == "zee24taas" })
        assertFalse(shown.any { it.handle == "TimesNow" })
    }

    @Test
    fun `no followed region falls back to international`() {
        val shown = effectiveLiveChannels(emptySet(), null, emptyList())
        assertTrue(shown.isNotEmpty())
        assertTrue(shown.all { it.region == INTERNATIONAL_REGION_CODE })
    }

    @Test
    fun `a chosen set shows exactly those handles, from any region, with custom ones`() {
        val custom = listOf(LiveChannel("MyChannel", "My channel", "", "BR"))
        val shown = effectiveLiveChannels(setOf("IN"), setOf("TimesNow", "MyChannel"), custom)
        assertEquals(setOf("TimesNow", "MyChannel"), shown.map { it.handle }.toSet())
    }

    @Test
    fun `a handle in two regions shows in both`() {
        val shown = effectiveLiveChannels(setOf("DE", INTERNATIONAL_REGION_CODE), null, emptyList())
        assertEquals(setOf("DE", INTERNATIONAL_REGION_CODE), shown.filter { it.handle == "DWNews" }.map { it.region }.toSet())
    }

    @Test
    fun `every catalogued region is a region the news feed knows`() {
        val known = SELECTABLE_COUNTRIES.map { it.code } + INDIA_COUNTRY_CODE + INTERNATIONAL_REGION_CODE
        assertTrue(LIVE_CHANNELS.all { it.region in known })
    }

    @Test
    fun `a typed youtube name becomes a handle`() {
        assertEquals("NDTV", youtubeHandleOf("@NDTV"))
        assertEquals("NDTV", youtubeHandleOf("  NDTV "))
        assertEquals("aajtak", youtubeHandleOf("https://www.youtube.com/@aajtak/live"))
        assertEquals("Zee24Taas", youtubeHandleOf("Zee 24 Taas"))
        assertEquals("", youtubeHandleOf("   "))
    }

    @Test
    fun `the live link points at the current stream`() {
        assertEquals("https://www.youtube.com/@NDTV/live", liveUrl("NDTV"))
        assertEquals("https://www.youtube.com/@NDTV/live", liveUrl("@NDTV"))
    }

    @Test
    fun `a live page is read for its on air flag and stream id`() {
        val live = parseLiveStatus("""..."isLiveNow":true,"videoId":"AWvnolwC9MM"...""")
        assertTrue(live.live)
        assertEquals("AWvnolwC9MM", live.videoId)
        val off = parseLiveStatus("""..."videoId":"AWvnolwC9MM" no flag""")
        assertFalse(off.live)
        assertNull(off.videoId)
    }

    @Test
    fun `custom channels round trip`() {
        val list = listOf(LiveChannel("A", "Channel A", "hindi", "IN"), LiveChannel("B", "B", "", "BR"))
        assertEquals(list, decodeCustom(encodeCustom(list)))
        assertEquals(emptyList<LiveChannel>(), decodeCustom(null))
    }

    @Test
    fun `saved articles round trip and toggle`() {
        val a = FeedArticle("One\ttitle", "https://a/1", "A", "tech", "https://img/1", 5L)
        val b = FeedArticle("Two", "https://a/2", "B", "nation", null, 9L)
        val decoded = decodeSaved(encodeSaved(listOf(a, b)))
        assertEquals(listOf("https://a/1", "https://a/2"), decoded.map { it.link })
        assertNull(decoded[1].imageUrl)
        val on = toggleSaved(emptyList(), a)
        assertEquals(listOf(a), on)
        assertEquals(listOf(b, a), toggleSaved(on, b))
        assertEquals(emptyList<FeedArticle>(), toggleSaved(on, a))
    }

    @Test
    fun `read links are remembered, newest last, and bounded`() {
        assertEquals(listOf("a", "b"), markRead(listOf("a"), "b"))
        assertEquals(listOf("b", "a"), markRead(listOf("a", "b"), "a"))
        val many = (1..MAX_READ_NEWS).map { "l$it" }
        val next = markRead(many, "new")
        assertEquals(MAX_READ_NEWS, next.size)
        assertEquals("new", next.last())
        assertFalse("l1" in next)
    }

    @Test
    fun `a story's topic is its feed's category`() {
        val sources = listOf(FeedSource("https://a/feed", "A", "sports"), FeedSource("https://b/feed", "B", "tech"))
        val a = FeedArticle("t", "https://x/1", "A", "paris", null, 1L, feedUrl = "https://a/feed")
        assertEquals("sports", topicOf(a, sources))
        assertEquals("other", topicOf(a.copy(feedUrl = "https://gone/feed"), sources))
        assertEquals("other", topicOf(a.copy(feedUrl = ""), sources))
    }

    @Test
    fun `the stream is the video id just before the live flag`() {
        val page = """"videoId":"AAAAAAAAAAA" ${"x".repeat(20_000)} "videoId":"BBBBBBBBBBB" ${"y".repeat(500)} "isLiveNow":true """
        val s = parseLiveStatus(page)
        assertTrue(s.live)
        assertEquals("BBBBBBBBBBB", s.videoId)
    }
}
