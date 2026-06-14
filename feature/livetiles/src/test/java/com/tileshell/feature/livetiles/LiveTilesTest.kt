package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LiveFaceTest {

    @Test
    fun `clock icon at medium and up resolves to clock face`() {
        assertEquals(LiveFace.CLOCK, LiveFace.forIconKey("clock", TileSize.MEDIUM))
        assertEquals(LiveFace.CLOCK, LiveFace.forIconKey("clock", TileSize.WIDE))
        assertEquals(LiveFace.CLOCK, LiveFace.forIconKey("clock", TileSize.LARGE))
    }

    @Test
    fun `small tiles never have a live face`() {
        assertNull(LiveFace.forIconKey("clock", TileSize.SMALL))
    }

    @Test
    fun `unmapped icon keys stay static`() {
        assertNull(LiveFace.forIconKey("phone", TileSize.MEDIUM))
        assertNull(LiveFace.forIconKey(null, TileSize.LARGE))
    }

    @Test
    fun `weather and calendar icons resolve at medium and up`() {
        assertEquals(LiveFace.WEATHER, LiveFace.forIconKey("weather", TileSize.MEDIUM))
        assertEquals(LiveFace.CALENDAR, LiveFace.forIconKey("calendar", TileSize.LARGE))
    }

    @Test
    fun `weather and calendar are static at small`() {
        assertNull(LiveFace.forIconKey("weather", TileSize.SMALL))
        assertNull(LiveFace.forIconKey("calendar", TileSize.SMALL))
    }

    @Test
    fun `clock weather and calendar all flip`() {
        assertTrue(LiveFace.CLOCK.flips)
        assertTrue(LiveFace.WEATHER.flips)
        assertTrue(LiveFace.CALENDAR.flips)
    }
}

class FlipTargetTest {

    @Test
    fun `empty list flips nothing`() {
        assertNull(pickFlipTarget(emptyList()))
    }

    @Test
    fun `single id always picked`() {
        assertEquals("a", pickFlipTarget(listOf("a"), Random(1)))
    }

    @Test
    fun `picks only from the given ids`() {
        val ids = listOf("a", "b", "c")
        val random = Random(42)
        repeat(50) { assertTrue(pickFlipTarget(ids, random) in ids) }
    }
}

class ClockFaceTest {

    // 2026-06-14 is a Sunday (Calendar.DAY_OF_WEEK == 1), 14:05.
    private val sunday = clockFace(
        hour24 = 14, minute = 5, dayOfWeek = 1, dayOfMonth = 14, month0 = 5, year = 2026,
    )

    @Test
    fun `time is 24-hour with zero-padded minutes`() {
        assertEquals("14:05", sunday.hm)
    }

    @Test
    fun `hours are not padded`() {
        assertEquals("9:30", clockFace(9, 30, 1, 1, 0, 2026).hm)
    }

    @Test
    fun `weekday and date are lowercase full names`() {
        assertEquals("sunday", sunday.weekday)
        assertEquals("14 june 2026", sunday.fullDate)
    }

    @Test
    fun `saturday is the last weekday slot`() {
        assertEquals("saturday", clockFace(0, 0, 7, 1, 11, 2026).weekday)
        assertEquals("1 december 2026", clockFace(0, 0, 7, 1, 11, 2026).fullDate)
    }
}
