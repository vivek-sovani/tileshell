package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors [PhotosCodecTest] for the wallpaper-slideshow URI list codec. */
class WallpaperSlideshowCodecTest {

    @Test
    fun `round-trips a list of uris`() {
        val data = WallpaperSlideshowData(listOf("content://a/1", "content://b/2"))
        assertEquals(data, WallpaperSlideshowCodec.decode(WallpaperSlideshowCodec.encode(data)))
    }

    @Test
    fun `drops blank lines and trims`() {
        val decoded = WallpaperSlideshowCodec.decode("content://a/1\n\n  content://b/2  \n")
        assertEquals(listOf("content://a/1", "content://b/2"), decoded.uris)
    }

    @Test
    fun `empty text is empty selection`() {
        assertTrue(WallpaperSlideshowCodec.decode("").uris.isEmpty())
    }
}
