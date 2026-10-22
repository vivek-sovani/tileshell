package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoRecentsTest {
    private fun r(uri: String, at: Long) = VideoRecent(uri, "name of $uri", "video/mp4", at)

    @Test
    fun `opening a video again moves it to the top without a copy`() {
        val list = listOf(r("a", 3), r("b", 2), r("c", 1))
        val next = pushVideoRecent(list, r("c", 9))
        assertEquals(listOf("c", "a", "b"), next.map { it.uri })
    }

    @Test
    fun `the list is capped`() {
        var list = emptyList<VideoRecent>()
        for (i in 1..30) list = pushVideoRecent(list, r("v$i", i.toLong()))
        assertEquals(MAX_VIDEO_RECENTS, list.size)
        assertEquals("v30", list.first().uri)
    }

    @Test
    fun `encode and decode round trip, a tab in a name is flattened`() {
        val list = listOf(VideoRecent("content://x/1", "Trip\tto Goa", "video/mp4", 5L), r("b", 4))
        val back = decodeVideoRecents(encodeVideoRecents(list))
        assertEquals(listOf("content://x/1", "b"), back.map { it.uri })
        assertEquals("Trip to Goa", back.first().name)
        assertEquals(5L, back.first().openedAt)
    }

    @Test
    fun `a damaged line is skipped and the rest still read`() {
        val text = "garbage\n7\tcontent://ok\tOk\tvideo/mp4\nnotanumber\tx\ty\tz\n\n"
        assertEquals(listOf("content://ok"), decodeVideoRecents(text).map { it.uri })
    }

    @Test
    fun `duration shows hours only when there are some`() {
        assertEquals("0:48", formatVideoDuration(48_000))
        assertEquals("3:15", formatVideoDuration(195_000))
        assertEquals("1:12:08", formatVideoDuration(4_328_000))
        assertEquals("0:00", formatVideoDuration(-5))
    }
}
