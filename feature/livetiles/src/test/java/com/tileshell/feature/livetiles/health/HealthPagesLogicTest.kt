package com.tileshell.feature.livetiles.health

import com.tileshell.feature.livetiles.BatterySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HealthPagesLogicTest {
    private val utc = ZoneId.of("UTC")
    private val h = 3_600_000L
    private val m = 60_000L

    // --- hourly -------------------------------------------------------------------

    @Test
    fun `deltas land in the hour of the reading and first reading sets the start`() {
        var st = HourlySteps()
        st = recordHourly(st, 10, 245, 100)       // first reading: nothing guessed
        assertTrue(st.hours.isEmpty())
        st = recordHourly(st, 10, 245, 160)
        st = recordHourly(st, 10, 246, 400)
        assertEquals(60, st.hours[245]); assertEquals(240, st.hours[246])
        st = recordHourly(st, 10, 247, 50)        // counter reset: no negative, starting point moves
        st = recordHourly(st, 10, 247, 80)
        assertEquals(30, st.hours[247])
    }

    @Test
    fun `old hours are dropped and codec round trips tolerantly`() {
        var st = HourlySteps(mapOf(1L to 5), mapOf(0L to 5))
        st = recordHourly(st, 100, 2400, 10)
        assertFalse(st.hours.containsKey(1L)); assertFalse(st.lastTotals.containsKey(0L))
        val back = decodeHourly("garbage\n" + encodeHourly(HourlySteps(mapOf(30L to 4), mapOf(2L to 9))))
        assertEquals(mapOf(30L to 4), back.hours); assertEquals(mapOf(2L to 9), back.lastTotals)
    }

    @Test
    fun `busiest hour and labels`() {
        val hours = List(24) { if (it == 8) 900 else 10 }
        assertEquals(8, busiestHour(hours)); assertNull(busiestHour(List(24) { 0 }))
        assertEquals("8–9 am", hourRangeLabel(8)); assertEquals("11 am–12 pm", hourRangeLabel(11))
        assertEquals("12–1 am", hourRangeLabel(0)); assertEquals("11 pm–12 am", hourRangeLabel(23))
        assertEquals(24, dayHours(HourlySteps(mapOf(48L + 3 to 7)), 2).size)
        assertEquals(7, dayHours(HourlySteps(mapOf(48L + 3 to 7)), 2)[3])
        assertEquals(24 * 2L + 5, hourIndex(2L * 86_400_000 + 5 * h + 1, utc))
    }

    // --- walk ---------------------------------------------------------------------

    @Test
    fun `ascent ignores wobble, counts climbs, and descents only move the low point`() {
        val a = AscentTracker()
        listOf(0.0, 0.5, 0.2, 0.6, 0.1).forEach(a::add)
        assertEquals(0.0, a.ascentMeters, 0.0)
        listOf(1.5, 3.0, 6.0, 5.5, 2.0, 5.0).forEach(a::add)   // up 6 m, down to 2, up 3
        assertEquals(9.0, a.ascentMeters, 0.01)
        assertEquals(3, a.floors)
    }

    @Test
    fun `pressure to height and pace wording`() {
        assertEquals(0.0, heightFromPressure(1013.25f), 0.01)
        assertTrue(heightFromPressure(1012.0f) in 9.0..12.0)
        assertEquals("6:00", paceLabel(36 * 60_000L, 6.0)); assertNull(paceLabel(5_000, 1.0)); assertNull(paceLabel(600_000, 0.01))
        assertEquals(100, stepsPerMinute(1000, 10 * m)); assertNull(stepsPerMinute(10, 5_000))
        assertEquals("1:05:09", clockDuration(65 * m + 9_000)); assertEquals("4:07", clockDuration(247_000))
        assertEquals("1 h 05 min", spokenDuration(65 * m)); assertEquals("24 min", spokenDuration(24 * m))
    }

    @Test
    fun `walks codec, cap and hours of the day`() {
        val w = Walk(10 * h + 40 * m, 11 * h + 20 * m, 30 * m, 3000, 4)
        assertEquals(listOf(w), decodeWalks("bad\n" + encodeWalks(listOf(w))))
        var list = emptyList<Walk>()
        repeat(60) { list = addWalk(list, w.copy(startMs = it * 1000L)) }
        assertEquals(KEEP_WALKS, list.size); assertEquals(59_000L, list.first().startMs)
        assertEquals(setOf(10, 11), walkHoursOfDay(listOf(w), 0, utc))
        assertEquals(emptySet<Int>(), walkHoursOfDay(listOf(w), 1, utc))
    }

    // --- sleep --------------------------------------------------------------------

    private fun at(day: Long, hour: Int, min: Int = 0) = (day * 24 + hour) * h + min * m

    @Test
    fun `a quiet night with one pickup`() {
        val use = listOf(UseSpan(at(0, 21), at(0, 23, 40)), UseSpan(at(1, 2, 10), at(1, 2, 13)), UseSpan(at(1, 7), at(1, 9)))
        val e = estimateSleep(use, at(0, 20), at(1, 12))!!
        assertEquals(at(0, 23, 40), e.start); assertEquals(at(1, 7), e.end); assertEquals(1, e.pickups.size)
        assertEquals("7 h 20 min", sleepDurationLabel(e.durationMs)); assertEquals("phone picked up once", pickupLabel(1))
    }

    @Test
    fun `a look at the edges does not stretch the night and a long wake breaks it`() {
        val edge = listOf(UseSpan(at(0, 21), at(0, 23)), UseSpan(at(0, 23, 10), at(0, 23, 12)), UseSpan(at(1, 6, 50), at(1, 8)))
        val e = estimateSleep(edge, at(0, 20), at(1, 12))!!
        assertEquals(at(0, 23, 12), e.start); assertEquals(at(1, 6, 50), e.end); assertEquals(0, e.pickups.size)
        // 90 minutes of use at 3 am splits the night; the longer half wins
        val split = listOf(UseSpan(at(0, 21), at(0, 22)), UseSpan(at(1, 3), at(1, 4, 30)))
        val s2 = estimateSleep(split, at(0, 20), at(1, 12))!!
        assertEquals(at(1, 4, 30), s2.start); assertEquals(at(1, 12), s2.end)
    }

    @Test
    fun `naps and busy nights give nothing, gaps in use merge`() {
        assertNull(estimateSleep(listOf(UseSpan(at(0, 20), at(1, 10)), UseSpan(at(1, 10, 1), at(1, 10, 2))), at(0, 20), at(1, 12)))
        val merged = mergeSpans(listOf(UseSpan(0, m), UseSpan(3 * m, 4 * m), UseSpan(20 * m, 21 * m)))
        assertEquals(2, merged.size)
        assertNull(estimateSleep(emptyList(), at(0, 20), at(0, 21)))   // an hour of window is no night
    }

    @Test
    fun `battery log to screen spans, night window across midnight, weekly average`() {
        val s = listOf(BatterySample(100, 80, false, true), BatterySample(100 + 5 * m, 79, false, false), BatterySample(100 + 9 * h, 70, false, false))
        assertEquals(listOf(UseSpan(100, 100 + 5 * m)), screenOnSpans(s, 100 + 10 * h))
        val (a, b) = nightWindow(1, utc)
        assertEquals(20 * h, a)   // 8 pm of day 0
        assertEquals(24 * h + 12 * h, b)
        val night = estimateSleep(emptyList(), a, b)
        val week = listOf(night, null, night)
        assertEquals(16 * h, averageSleep(week)); assertNull(averageSleep(listOf(null)))
        assertEquals(0f, timelineFraction(22 * h, 1, utc), 0.001f)
        assertEquals(0.5f, timelineFraction(24 * h + 3 * h, 1, utc), 0.01f)
    }

    @Test
    fun `next occurrence of bedtime`() {
        assertEquals(at(0, 22), nextOccurrence(22 * 60, at(0, 21), utc))
        assertEquals(at(1, 22), nextOccurrence(22 * 60, at(0, 22), utc))
        assertEquals("11:48 pm", clockLabel(at(0, 23, 48), utc)); assertEquals("12:05 am", clockLabel(at(1, 0, 5), utc))
        assertTrue(LocalDate.ofEpochDay(0).year == 1970)
    }

    // --- air ----------------------------------------------------------------------

    private val airJson = """{"utc_offset_seconds":19800,
 "current":{"time":"2026-11-24T10:00","us_aqi":88,"pm10":60.4,"pm2_5":31.2,"ozone":70.0,"nitrogen_dioxide":18.5,"uv_index":4.2},
 "hourly":{"time":["2026-11-24T06:00","2026-11-24T07:00","2026-11-24T08:00","2026-11-24T12:00","2026-11-24T22:00"],
 "us_aqi":[120,60,45,95,null],"uv_index":[0.0,0.8,2.1,7.5,0.0],"is_day":[1,1,1,1,0]}}"""

    @Test
    fun `air quality parses, bands and picks the best walking hour`() {
        val now = 1_000L
        val s = parseAirQuality(airJson, now)!!
        assertEquals(88, s.usAqi); assertEquals(31.2, s.pm25!!, 0.01); assertEquals(4.2, s.uvNow!!, 0.01)
        assertEquals(5, s.hours.size); assertNull(s.hours[4].aqi); assertFalse(s.hours[4].isDay)
        assertEquals(AqiBand.MODERATE, aqiBand(88)); assertEquals(AqiBand.GOOD, aqiBand(50)); assertEquals(AqiBand.HAZARDOUS, aqiBand(400))
        assertEquals("high", uvBand(7.0)); assertEquals("low", uvBand(2.9))
        val beforeAll = s.hours.first().timeMs - 1
        assertEquals(45, bestWalkHour(s.hours, beforeAll)!!.aqi)
        // the 12:00 hour has UV 7.5, so it is never picked even when it is the only one left
        assertNull(bestWalkHour(s.hours, s.hours[3].timeMs))
        assertEquals(7.5, uvPeak(s.hours)!!.uv!!, 0.01)
        assertNull(parseAirQuality("{}", now)); assertNull(parseAirQuality("nope", now))
        assertNotNull(decodeAirCache(encodeAirCache(18.5, 73.8, "{}", 5L))); assertNull(decodeAirCache("bad"))
    }
}
