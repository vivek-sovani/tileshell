package com.tileshell.feature.livetiles

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeLabelTest {
    @Test
    fun `minutes and seconds, then hours once past an hour`() {
        assertEquals("0:00", playbackTimeLabel(0))
        assertEquals("3:07", playbackTimeLabel(187_400))
        assertEquals("1:02:45", playbackTimeLabel(3_765_000))
        assertEquals("0:00", playbackTimeLabel(-5))
    }
}
