package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CountdownStatusTextTest {

    @Test
    fun `today, tomorrow and yesterday get their own words`() {
        assertEquals("today", countdownStatusText(0))
        assertEquals("tomorrow", countdownStatusText(1))
        assertEquals("yesterday", countdownStatusText(-1))
    }

    @Test
    fun `further out counts render as plain day counts`() {
        assertEquals("in 12 days", countdownStatusText(12))
        assertEquals("5 days ago", countdownStatusText(-5))
    }
}

class CountdownFaceTest {

    @Test
    fun `computes the day count between today and the target`() {
        val face = countdownFace(
            label = "birthday",
            target = LocalDate.of(2026, 9, 15),
            today = LocalDate.of(2026, 9, 1),
        )
        assertEquals(14L, face.daysRemaining)
        assertEquals("birthday", face.label)
        assertEquals("15 september 2026", face.dateText)
        assertEquals(true, face.hasDate)
    }

    @Test
    fun `a blank label falls back to a generic heading`() {
        val face = countdownFace(label = "", target = LocalDate.of(2026, 1, 1), today = LocalDate.of(2026, 1, 1))
        assertEquals("countdown", face.label)
    }

    @Test
    fun `a past target is negative`() {
        val face = countdownFace(
            label = "trip",
            target = LocalDate.of(2026, 1, 1),
            today = LocalDate.of(2026, 1, 10),
        )
        assertEquals(-9L, face.daysRemaining)
    }
}

class CountdownIconMappingTest {

    @Test
    fun `countdown icon key maps to the countdown face at medium and up`() {
        assertEquals(LiveFace.COUNTDOWN, LiveFace.forIconKey("countdown", TileSize.MEDIUM))
        assertEquals(LiveFace.COUNTDOWN, LiveFace.forIconKey("countdown", TileSize.WIDE))
    }

    @Test
    fun `countdown stays out of forIconKey at small — handled by its own small-face branch instead`() {
        assertNull(LiveFace.forIconKey("countdown", TileSize.SMALL))
    }

    @Test
    fun `countdown flips — front is the day count, back is the exact date`() {
        assertEquals(true, LiveFace.COUNTDOWN.flips)
    }
}
