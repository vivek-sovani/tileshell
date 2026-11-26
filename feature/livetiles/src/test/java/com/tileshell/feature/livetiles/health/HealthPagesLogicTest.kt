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
        val a = AscentTracker(hysteresis = 1.2, smoothing = 1.0)
        listOf(0.0, 0.5, 0.2, 0.6, 0.1).forEach { a.add(it) }
        assertEquals(0.0, a.ascentMeters, 0.0)
        listOf(1.5, 3.0, 6.0, 5.5, 2.0, 5.0).forEach { a.add(it) }   // up 6 m, down to 2, up 3
        assertEquals(9.0, a.ascentMeters, 0.01)
        assertEquals(3, a.floors)
    }

    @Test
    fun `sensor noise over a long walk adds no floors`() {
        val a = AscentTracker()
        val rnd = java.util.Random(7)
        repeat(5_000) { a.add(rnd.nextGaussian() * 0.8) }          // ~1 m jitter, 15+ minutes of readings
        assertEquals(0, a.floors)
    }

    @Test
    fun `a real climb counts, and pressure change while standing still does not`() {
        val up = AscentTracker()
        val rnd = java.util.Random(3)
        repeat(300) { up.add(it * 0.04 + rnd.nextGaussian() * 0.5) }   // 12 m of stairs with noise
        assertTrue(up.floors in 3..4)
        val still = AscentTracker()
        repeat(300) { still.add(it * 0.04, moving = false) }           // a lift: pressure moves, no steps
        assertEquals(0, still.floors)
    }

    @Test
    fun `route tracker drops poor fixes and jumps, and measures the route`() {
        val t = RouteTracker()
        assertTrue(t.add(18.5200, 73.8500, 8f, 0))
        assertEquals(false, t.add(18.5201, 73.8500, 80f, 2_000))       // poor accuracy
        assertEquals(false, t.add(18.52001, 73.8500, 5f, 2_000))       // 1 m: standing
        assertEquals(false, t.add(18.5300, 73.8500, 5f, 3_000))        // 1.1 km in a second
        assertTrue(t.add(18.5210, 73.8500, 5f, 20_000))                // ~111 m, fine
        assertEquals(111.0, t.meters, 3.0)
        t.breakSegment()
        assertTrue(t.add(18.5300, 73.8500, 5f, 600_000))               // after a pause: no distance for the gap
        assertEquals(111.0, t.meters, 3.0)
        assertTrue(t.points.last().newSegment)
        val back = decodeRoute(encodeRoute(t.points))
        assertEquals(t.points.size, back.size)
        assertEquals(t.points.last().newSegment, back.last().newSegment)
        assertEquals(18.5300, back.last().lat, 0.00001)
    }

    @Test
    fun `route marks, projection and the km announcement`() {
        val pts = (0..30).map { RoutePoint(18.5 + it * 0.0009, 73.85, it * 1000L, it == 0) }   // ~100 m a point, 3 km
        assertEquals(3, routeMarks(pts, 1000.0).size)
        val xy = projectRoute(pts, 300f, 200f, 10f)
        assertEquals(pts.size, xy.size)
        assertTrue(xy.all { it.first in 0f..300f && it.second in 0f..200f })
        assertEquals("1 kilometre in 9 minutes 12 seconds", kmAnnouncement(1, 552_000, 552_000, false))
        assertEquals("2 miles. 10 minutes for the last one, 19 minutes 30 seconds in all", kmAnnouncement(2, 600_000, 1_170_000, true))
        assertEquals("1 hour 5 minutes", spokenWords(65 * m)); assertEquals("45 seconds", spokenWords(45_000))
        assertEquals(listOf(550_000L, 600_000L), splitTimes(listOf(550_000L, 1_150_000L)))
    }

    @Test
    fun `walks keep their distance, splits and route flag, and older lines still read`() {
        val w = Walk(1000, 2000, 900, 1200, 2, 1520, listOf(540_000L, 1_100_000L), true)
        assertEquals(listOf(w), decodeWalks(encodeWalks(listOf(w))))
        val old = decodeWalks("1000\t2000\t900\t1200\t2").single()
        assertEquals(0, old.meters); assertEquals(false, old.hasRoute)
        assertEquals(1.52, walkKm(w, 170), 0.001); assertEquals(distanceKm(1200, 170), walkKm(old, 170), 0.001)
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

class HealthTileLogicTest {
    private val utc = ZoneId.of("UTC")
    private val h = 3_600_000L
    private fun walk(startMs: Long) = Walk(startMs, startMs + 30 * 60_000L, 30 * 60_000L, 3000, 2)

    @Test
    fun `tile kinds by size`() {
        assertEquals(HealthTileKind.SMALL, healthTileKind(com.tileshell.core.data.TileSize.SMALL))
        assertEquals(HealthTileKind.SMALL, healthTileKind(com.tileshell.core.data.TileSize.TALL))
        assertEquals(HealthTileKind.MEDIUM, healthTileKind(com.tileshell.core.data.TileSize.MEDIUM))
        assertEquals(HealthTileKind.WIDE, healthTileKind(com.tileshell.core.data.TileSize.WIDE))
        assertEquals(HealthTileKind.LARGE, healthTileKind(com.tileshell.core.data.TileSize.LARGE))
        assertEquals(HealthTileKind.LARGE, healthTileKind(com.tileshell.core.data.TileSize.XLARGE))
    }

    @Test
    fun `back faces keep their order, skip what has nothing to say and round robin`() {
        val all = backFaces(BackInputs(true, true, true, true))
        assertEquals(listOf(BackFace.SLEEP, BackFace.AIR, BackFace.LAST_WALK, BackFace.APPS), all)
        val some = backFaces(BackInputs(false, true, false, true))
        assertEquals(listOf(BackFace.AIR, BackFace.APPS), some)
        assertEquals(BackFace.AIR, backFaceFor(some, 0)); assertEquals(BackFace.APPS, backFaceFor(some, 1)); assertEquals(BackFace.AIR, backFaceFor(some, 2))
        assertNull(backFaceFor(emptyList(), 3)); assertEquals(BackFace.APPS, backFaceFor(some, -1))
    }

    @Test
    fun `sleep shows from waking until noon, air only when worth a look, last walk today or yesterday`() {
        val night = SleepEstimate(h * 23 - 24 * h + 24 * h, 24 * h + 7 * h, emptyList())   // ends 7:00 on day 1
        assertFalse(sleepFaceEligible(night, 24 * h + 6 * h, utc))       // still asleep
        assertTrue(sleepFaceEligible(night, 24 * h + 7 * h + 1, utc))
        assertTrue(sleepFaceEligible(night, 24 * h + 11 * h, utc))
        assertFalse(sleepFaceEligible(night, 24 * h + 12 * h, utc))
        assertFalse(sleepFaceEligible(night, 48 * h + 8 * h, utc))       // next morning: another night
        assertFalse(sleepFaceEligible(null, 0, utc))
        assertFalse(airFaceEligible(50, 5.9)); assertTrue(airFaceEligible(51, null)); assertTrue(airFaceEligible(20, 6.0)); assertFalse(airFaceEligible(null, null))
        val now = 10 * 24 * h + 9 * h
        assertNotNull(lastWalkEligible(listOf(walk(now - 2 * h)), now, utc))
        assertNotNull(lastWalkEligible(listOf(walk(9 * 24 * h + 23 * h)), now, utc))
        assertNull(lastWalkEligible(listOf(walk(8 * 24 * h + 12 * h)), now, utc)); assertNull(lastWalkEligible(emptyList(), now, utc))
    }

    @Test
    fun `labels`() {
        assertEquals("5 pm", hourLabel(17)); assertEquals("12 am", hourLabel(0))
        assertEquals("86 moderate", airBandShort(86)); assertEquals("120 unhealthy", airBandShort(120))
        assertNull(streakLabel(0)); assertEquals("1 day at goal", streakLabel(1)); assertEquals("4 days at goal", streakLabel(4))
        assertEquals("30 min · 2 floors", walkTimeFloors(walk(0))); assertEquals("30 min", walkTimeFloors(walk(0).copy(floors = 0)))
        assertEquals("+2,906 steps", plusSteps(2906))
    }

    @Test
    fun `walk confirm and saved flow`() {
        val w = walk(0)
        var s: WalkUi = WalkUi.None
        s = reduceWalkUi(s, WalkUiEvent.KeepGoing); assertEquals(WalkUi.None, s)
        s = reduceWalkUi(s, WalkUiEvent.StopTapped); assertEquals(WalkUi.Confirm, s)
        s = reduceWalkUi(s, WalkUiEvent.KeepGoing); assertEquals(WalkUi.None, s)
        s = reduceWalkUi(reduceWalkUi(s, WalkUiEvent.StopTapped), WalkUiEvent.Tick(5, running = false)); assertEquals(WalkUi.None, s)   // ended elsewhere
        s = reduceWalkUi(reduceWalkUi(WalkUi.None, WalkUiEvent.StopTapped), WalkUiEvent.SaveTapped(w, 1_000)); assertEquals(WalkUi.Saved(w, 1_000), s)
        assertEquals(s, reduceWalkUi(s, WalkUiEvent.Tick(1_000 + SAVED_MS - 1, running = false)))
        assertEquals(WalkUi.None, reduceWalkUi(s, WalkUiEvent.Tick(1_000 + SAVED_MS, running = false)))
        assertEquals(WalkUi.None, reduceWalkUi(s, WalkUiEvent.Tick(2_000, running = true)))         // a new walk replaces the summary
        assertEquals(WalkUi.None, reduceWalkUi(reduceWalkUi(WalkUi.None, WalkUiEvent.StopTapped), WalkUiEvent.SaveTapped(null, 0)))
        assertEquals(WalkUi.None, reduceWalkUi(WalkUi.None, WalkUiEvent.SaveTapped(w, 0)))             // a save needs a question first
    }
}
