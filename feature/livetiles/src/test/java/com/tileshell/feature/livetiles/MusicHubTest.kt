package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayedTrackCodecTest {

    private fun track(title: String, artist: String = "an artist", pkg: String = "x.spotify", at: Long = 1_700_000_000_000L) =
        PlayedTrack(title = title, artist = artist, packageName = pkg, playedAtMillis = at)

    @Test
    fun `a single track round-trips`() {
        val list = listOf(track("saawan aaya hai"))
        assertEquals(list, PlayedTrackCodec.decode(PlayedTrackCodec.encode(list)))
    }

    @Test
    fun `several tracks round-trip in order`() {
        val list = listOf(track("one"), track("two", pkg = "x.ytmusic"), track("three", artist = ""))
        assertEquals(list, PlayedTrackCodec.decode(PlayedTrackCodec.encode(list)))
    }

    @Test
    fun `empty list round-trips to empty`() {
        assertEquals(emptyList<PlayedTrack>(), PlayedTrackCodec.decode(PlayedTrackCodec.encode(emptyList())))
        assertEquals(emptyList<PlayedTrack>(), PlayedTrackCodec.decode(""))
    }

    @Test
    fun `a pipe in the title does not split into extra fields`() {
        val list = listOf(track("call | response"))
        val decoded = PlayedTrackCodec.decode(PlayedTrackCodec.encode(list))
        assertEquals(1, decoded.size)
        // The stray pipe is sanitized on the way in, not preserved verbatim —
        // the important thing is it doesn't corrupt the line into 5 fields.
        assertTrue(decoded[0].title.contains("call"))
    }

    @Test
    fun `a malformed line is skipped, not thrown`() {
        assertEquals(emptyList<PlayedTrack>(), PlayedTrackCodec.decode("not|enough|fields"))
    }

    @Test
    fun `localTrackId round-trips for a local-library entry`() {
        val list = listOf(
            PlayedTrack(
                title = "saawan aaya hai",
                artist = "an artist",
                packageName = PlayedTrack.LOCAL_LIBRARY_MARKER,
                playedAtMillis = 1_700_000_000_000L,
                localTrackId = 42L,
            ),
        )
        val decoded = PlayedTrackCodec.decode(PlayedTrackCodec.encode(list))
        assertEquals(list, decoded)
        assertTrue(decoded[0].isLocal)
    }

    @Test
    fun `a pre-existing 4-field history line decodes with a null localTrackId`() {
        // The exact shape a history file written before localTrackId existed
        // would have on disk.
        val decoded = PlayedTrackCodec.decode("saawan aaya hai|an artist|tileshell.local|1700000000000")
        assertEquals(1, decoded.size)
        assertEquals(null, decoded[0].localTrackId)
        assertTrue(decoded[0].isLocal)
    }
}

class TrackDurationTest {

    @Test
    fun `formats minutes and zero-padded seconds`() {
        assertEquals("3:07", formatTrackDuration(187_000L))
        assertEquals("0:05", formatTrackDuration(5_000L))
        assertEquals("10:00", formatTrackDuration(600_000L))
    }

    @Test
    fun `a negative or zero duration never crashes`() {
        assertEquals("0:00", formatTrackDuration(0L))
        assertEquals("0:00", formatTrackDuration(-500L))
    }
}
