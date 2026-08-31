package com.tileshell.feature.livetiles

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmFaceTest {

    @Test
    fun `no alarm scheduled`() {
        val face = alarmFace(
            hasAlarm = false, time = "", nowDayOfYear = 0, nowYear = 0,
            triggerDayOfYear = 0, triggerYear = 0, triggerWeekday = "",
        )
        assertEquals(AlarmFace(hasAlarm = false, time = "", dayLabel = "no alarm set"), face)
    }

    @Test
    fun `alarm later today`() {
        val face = alarmFace(
            hasAlarm = true, time = "6:30 am", nowDayOfYear = 200, nowYear = 2026,
            triggerDayOfYear = 200, triggerYear = 2026, triggerWeekday = "tuesday",
        )
        assertEquals("today", face.dayLabel)
        assertEquals("6:30 am", face.time)
    }

    @Test
    fun `alarm tomorrow`() {
        val face = alarmFace(
            hasAlarm = true, time = "6:30 am", nowDayOfYear = 200, nowYear = 2026,
            triggerDayOfYear = 201, triggerYear = 2026, triggerWeekday = "wednesday",
        )
        assertEquals("tomorrow", face.dayLabel)
    }

    @Test
    fun `alarm further out shows the weekday`() {
        val face = alarmFace(
            hasAlarm = true, time = "6:30 am", nowDayOfYear = 200, nowYear = 2026,
            triggerDayOfYear = 204, triggerYear = 2026, triggerWeekday = "saturday",
        )
        assertEquals("saturday", face.dayLabel)
    }

    @Test
    fun `alarm icon key maps to the alarm face at medium and up`() {
        assertEquals(LiveFace.ALARM, LiveFace.forIconKey("alarm", TileSize.MEDIUM))
        assertEquals(LiveFace.ALARM, LiveFace.forIconKey("alarm", TileSize.WIDE))
    }

    @Test
    fun `alarm tile stays static at small`() {
        assertNull(LiveFace.forIconKey("alarm", TileSize.SMALL))
    }

    @Test
    fun `alarm face flips`() {
        assertTrue(LiveFace.ALARM.flips)
    }
}
