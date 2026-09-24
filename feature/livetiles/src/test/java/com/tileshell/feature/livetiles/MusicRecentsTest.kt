package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRecentTest {

    @Test
    fun `a new item goes first`() {
        assertEquals(listOf("c", "a", "b"), pushRecent(listOf("a", "b"), "c") { it })
    }

    @Test
    fun `replaying an item moves it to the front instead of duplicating it`() {
        assertEquals(listOf("b", "a", "c"), pushRecent(listOf("a", "b", "c"), "b") { it })
    }

    @Test
    fun `caps at ten by default`() {
        val current = (1..10).map { "s$it" }
        val result = pushRecent(current, "new") { it }
        assertEquals(MusicRecents.MAX, result.size)
        assertEquals("new", result.first())
        assertEquals("s9", result.last())
    }

    @Test
    fun `dedupes by key, keeping the newer entry`() {
        val old = FavoriteStation("id1", "Old name", "https://a", null, 1L)
        val other = FavoriteStation("id2", "Other", "https://b", null, 2L)
        val fresh = FavoriteStation("id1", "New name", "https://a", null, 3L)
        val result = pushRecent(listOf(old, other), fresh) { it.stationId }
        assertEquals(listOf(fresh, other), result)
    }
}

class RecentEpisodeCodecTest {

    private fun recent(guid: String, image: String? = "https://img", duration: Long? = 60_000L) = RecentEpisode(
        show = PodcastSubscription("https://feed", "Show | Title", "https://art", 0L),
        episode = PodcastEpisode(
            guid = guid,
            title = "Episode\nOne",
            description = "",
            audioUrl = "https://audio.mp3",
            durationMs = duration,
            publishedMillis = 1234L,
            imageUrl = image,
        ),
        playedAtMillis = 99L,
    )

    @Test
    fun `round trips, with delimiters in text sanitised`() {
        val decoded = RecentEpisodeCodec.decode(RecentEpisodeCodec.encode(listOf(recent("g1"), recent("g2"))))
        assertEquals(2, decoded.size)
        assertEquals("g1", decoded[0].episode.guid)
        assertEquals("Show   Title", decoded[0].show.title)
        assertEquals("Episode One", decoded[0].episode.title)
        assertEquals(60_000L, decoded[0].episode.durationMs)
        assertEquals("https://img", decoded[0].episode.imageUrl)
        assertEquals(99L, decoded[0].playedAtMillis)
    }

    @Test
    fun `optional fields decode to null`() {
        val decoded = RecentEpisodeCodec.decode(RecentEpisodeCodec.encode(listOf(recent("g", image = null, duration = null))))
        assertNull(decoded.single().episode.imageUrl)
        assertNull(decoded.single().episode.durationMs)
    }

    @Test
    fun `malformed lines are skipped`() {
        val good = RecentEpisodeCodec.encode(listOf(recent("g")))
        assertEquals(1, RecentEpisodeCodec.decode("garbage\n$good\na|b|c").size)
        assertTrue(RecentEpisodeCodec.decode("").isEmpty())
    }
}
