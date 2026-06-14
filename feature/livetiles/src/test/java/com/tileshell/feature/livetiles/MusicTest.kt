package com.tileshell.feature.livetiles

import android.media.session.PlaybackState
import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicMappingTest {

    @Test
    fun `music icon key maps to the music face at medium and up`() {
        assertEquals(LiveFace.MUSIC, LiveFace.forIconKey("music", TileSize.MEDIUM))
        assertEquals(LiveFace.MUSIC, LiveFace.forIconKey("music", TileSize.WIDE))
    }

    @Test
    fun `music tile stays static at small`() {
        assertNull(LiveFace.forIconKey("music", TileSize.SMALL))
    }

    @Test
    fun `music face flips`() {
        assertTrue(LiveFace.MUSIC.flips)
    }
}

class NowPlayingTest {

    @Test
    fun `title and artist are trimmed and kept`() {
        val np = nowPlayingFrom("  Midnight City ", " M83 ", PlaybackState.STATE_PLAYING)
        assertEquals(NowPlaying("Midnight City", "M83", playing = true), np)
    }

    @Test
    fun `playing flag is true while playing or buffering`() {
        assertTrue(nowPlayingFrom("t", "a", PlaybackState.STATE_PLAYING)!!.playing)
        assertTrue(nowPlayingFrom("t", "a", PlaybackState.STATE_BUFFERING)!!.playing)
    }

    @Test
    fun `playing flag is false while paused or stopped`() {
        assertFalse(nowPlayingFrom("t", "a", PlaybackState.STATE_PAUSED)!!.playing)
        assertFalse(nowPlayingFrom("t", "a", PlaybackState.STATE_STOPPED)!!.playing)
        assertFalse(nowPlayingFrom("t", "a", PlaybackState.STATE_NONE)!!.playing)
    }

    @Test
    fun `no title and no artist yields no face`() {
        assertNull(nowPlayingFrom(null, null, PlaybackState.STATE_PLAYING))
        assertNull(nowPlayingFrom("   ", "", PlaybackState.STATE_PLAYING))
    }

    @Test
    fun `missing title falls back to a placeholder when an artist exists`() {
        val np = nowPlayingFrom(null, "M83", PlaybackState.STATE_PLAYING)
        assertEquals("now playing", np!!.title)
        assertEquals("M83", np.artist)
    }
}
