package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionsTest {
    @Test
    fun `a position inside the first seconds starts from the beginning`() {
        assertEquals(0L, resumeStartMs(3_000, 600_000))
        assertEquals(0L, resumeStartMs(0, 600_000))
    }

    @Test
    fun `a position in the last seconds counts as finished`() {
        assertEquals(0L, resumeStartMs(595_000, 600_000))
        assertEquals(0L, resumeStartMs(600_000, 600_000))
    }

    @Test
    fun `a position in the middle is kept`() {
        assertEquals(125_000L, resumeStartMs(125_000, 600_000))
        assertEquals(RESUME_MIN_MS, resumeStartMs(RESUME_MIN_MS, 600_000))
    }

    @Test
    fun `an unknown length keeps any position past the start`() {
        assertEquals(900_000L, resumeStartMs(900_000, 0))
    }

    @Test
    fun `pruning drops the least recently played over the cap`() {
        val entries = mapOf("a" to 10L, "b" to 30L, "c" to 20L, "d" to 40L)
        assertEquals(emptyList<String>(), positionsToPrune(entries, 4))
        assertEquals(listOf("a", "c"), positionsToPrune(entries, 2))
    }

    @Test
    fun `episode lines say played, time left, or position`() {
        assertEquals(null, episodeProgressLabel(null, 600_000))
        assertEquals("played", episodeProgressLabel(EPISODE_PLAYED, 600_000))
        assertEquals("8:20 left", episodeProgressLabel(100_000, 600_000))
        assertEquals("at 2:05", episodeProgressLabel(125_000, null))
    }
}
