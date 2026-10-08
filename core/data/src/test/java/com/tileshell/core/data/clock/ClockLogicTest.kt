package com.tileshell.core.data.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ClockLogicTest {

    private val kriya = TimerSet(
        "k", "shambhavi mahamudra kriya",
        listOf(
            TimerPart("preparatory", List(4) { TimerStep(seconds = 120) }),
            TimerPart(
                "kriya",
                listOf(TimerStep(seconds = 360), TimerStep(seconds = 300), TimerStep(seconds = 240), TimerStep("remaining", remainder = true)),
                totalSeconds = 21 * 60,
            ),
        ),
    )

    // --- timer sets ---------------------------------------------------------------

    @Test
    fun `the kriya set is eight steps and twenty nine minutes, the remainder being six`() {
        val steps = flatten(kriya)
        assertEquals(8, steps.size)
        assertEquals(listOf(120, 120, 120, 120, 360, 300, 240, 360).map { it * 1000L }, steps.map { it.ms })
        assertEquals(29 * 60 * 1000L, totalMs(kriya))
    }

    @Test
    fun `steps are labelled by part and position`() {
        val steps = flatten(kriya)
        assertEquals("preparatory · step 1 of 4", steps[0].label)
        assertEquals("kriya · step 2 of 4", steps[5].label)
        assertEquals("kriya · remaining", steps[7].label)
    }

    @Test
    fun `changing a part's total only changes its remainder step`() {
        val longer = kriya.copy(parts = listOf(kriya.parts[0], kriya.parts[1].copy(totalSeconds = 25 * 60)))
        assertEquals(10 * 60 * 1000L, flatten(longer).last().ms)
        assertEquals(8, flatten(longer).size)
    }

    @Test
    fun `a remainder never goes below zero and a zero step is dropped`() {
        val tooShort = kriya.copy(parts = listOf(kriya.parts[1].copy(totalSeconds = 10 * 60)))
        val steps = flatten(tooShort)
        assertEquals(3, steps.size)
        assertEquals("kriya · step 3 of 3", steps.last().label)
    }

    @Test
    fun `a set round trips through its storage form`() {
        val decoded = decodeTimerSets(encodeTimerSets(listOf(kriya)))
        assertEquals(listOf(kriya), decoded)
    }

    @Test
    fun `junk storage decodes to nothing`() {
        assertEquals(emptyList<TimerSet>(), decodeTimerSets(null))
        assertEquals(emptyList<TimerSet>(), decodeTimerSets("not json"))
    }

    @Test
    fun `durations read as minutes and hours`() {
        assertEquals("2:00", formatDuration(120))
        assertEquals("29:00", formatDuration(29 * 60))
        assertEquals("1:05:09", formatDuration(3909))
        assertEquals("0:00", formatDuration(-5))
    }

    // --- sessions ----------------------------------------------------------------------

    private val t0 = 1_800_000_000_000L
    private fun session() = startSession("s", "kriya", flatten(kriya).map { SessionStep(it.label, it.ms) }, t0)!!

    @Test
    fun `a session starts on its first step and counts the whole set`() {
        val s = session()
        assertEquals(0, s.index)
        assertEquals(t0 + 120_000, s.stepEndsAt)
        assertEquals(29 * 60 * 1000L, s.totalRemainingMs(t0))
        assertEquals(29 * 60 * 1000L - 30_000, s.totalRemainingMs(t0 + 30_000))
    }

    @Test
    fun `a session with no length does not start`() {
        assertNull(startSession("s", "x", listOf(SessionStep("a", 0)), t0))
    }

    @Test
    fun `when a step ends the next begins exactly where it ended`() {
        val adv = session().advance(t0 + 121_000)
        assertEquals(1, adv.stepsEnded)
        assertEquals(1, adv.session!!.index)
        assertEquals(t0 + 240_000, adv.session!!.stepEndsAt)
    }

    @Test
    fun `a late alarm catches up over several steps`() {
        val adv = session().advance(t0 + 5 * 60_000)
        assertEquals(2, adv.stepsEnded)
        assertEquals(2, adv.session!!.index)
        assertEquals(t0 + 6 * 60_000, adv.session!!.stepEndsAt)
    }

    @Test
    fun `the last step ending finishes the session`() {
        val adv = session().advance(t0 + 30 * 60_000)
        assertNull(adv.session)
        assertEquals(8, adv.stepsEnded)
    }

    @Test
    fun `nothing moves before the step ends`() {
        val adv = session().advance(t0 + 60_000)
        assertEquals(0, adv.stepsEnded)
        assertEquals(0, adv.session!!.index)
    }

    @Test
    fun `pausing keeps what was left and resuming restarts it from now`() {
        val paused = session().pause(t0 + 45_000)
        assertTrue(paused.paused)
        assertEquals(75_000, paused.remainingMs(t0 + 999_999))
        val resumed = paused.resume(t0 + 100_000)
        assertFalse(resumed.paused)
        assertEquals(t0 + 175_000, resumed.stepEndsAt)
    }

    @Test
    fun `a paused session does not advance`() {
        val adv = session().pause(t0 + 45_000).advance(t0 + 10 * 60_000)
        assertEquals(0, adv.stepsEnded)
        assertEquals(0, adv.session!!.index)
    }

    @Test
    fun `skipping starts the next step now, or ends the session on the last`() {
        val skipped = session().skip(t0 + 10_000)!!
        assertEquals(1, skipped.index)
        assertEquals(t0 + 130_000, skipped.stepEndsAt)
        val last = session().copy(index = 7)
        assertNull(last.skip(t0))
    }

    @Test
    fun `skipping while paused stays paused at the next step's full length`() {
        val skipped = session().pause(t0 + 10_000).skip(t0 + 20_000)!!
        assertTrue(skipped.paused)
        assertEquals(120_000, skipped.remainingMs(t0 + 99_999))
    }

    @Test
    fun `sessions round trip, running and paused`() {
        val running = session()
        val paused = session().copy(id = "p").pause(t0 + 5_000)
        assertEquals(listOf(running, paused), decodeSessions(encodeSessions(listOf(running, paused))))
        assertEquals(emptyList<Session>(), decodeSessions("[{\"id\":\"x\"}]"))
    }

    // --- stopwatch ------------------------------------------------------------------------

    @Test
    fun `a stopwatch counts while running, keeps its time when stopped, and resumes`() {
        var s = StopwatchState().start(1_000)
        assertEquals(500, s.elapsedMs(1_500))
        s = s.stop(2_000)
        assertEquals(1_000, s.elapsedMs(50_000))
        s = s.start(10_000)
        assertEquals(1_700, s.elapsedMs(10_700))
    }

    @Test
    fun `laps record the elapsed time and their own lengths`() {
        var s = StopwatchState().start(0)
        s = s.lap(3_710).lap(8_330).lap(12_430)
        assertEquals(listOf(3_710L, 8_330L, 12_430L), s.laps)
        assertEquals(listOf(3_710L, 4_620L, 4_100L), s.lapLengths())
        assertEquals(StopwatchState(), s.reset())
    }

    @Test
    fun `a stopped watch ignores a lap`() {
        assertEquals(emptyList<Long>(), StopwatchState().lap(100).laps)
    }

    @Test
    fun `stopwatch time reads with hundredths`() {
        assertEquals("00:12.43", formatStopwatch(12_430))
        assertEquals("01:05.07", formatStopwatch(65_070))
        assertEquals("1:02:03.40", formatStopwatch(3_723_400))
    }

    @Test
    fun `a stopwatch round trips through storage`() {
        val s = StopwatchState(runningSince = 5_000, accumulatedMs = 900, laps = listOf(100, 400))
        assertEquals(s, decodeStopwatch(encodeStopwatch(s)))
        assertEquals(StopwatchState(), decodeStopwatch(encodeStopwatch(StopwatchState())))
        assertEquals(StopwatchState(), decodeStopwatch("garbage"))
    }

    // --- world clock ------------------------------------------------------------------------

    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(zone: String, y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.of(zone)).toInstant().toEpochMilli()

    @Test
    fun `home shows no offset and no day note`() {
        val row = worldRow(WorldCity("Pune", "Asia/Kolkata"), at("Asia/Kolkata", 2026, 10, 7, 16, 12), ist)
        assertEquals("4:12 pm", row.time)
        assertEquals("", row.offsetNote)
        assertEquals("", row.dayNote)
        assertTrue(row.day)
    }

    @Test
    fun `london in october is four and a half hours behind`() {
        val now = at("Asia/Kolkata", 2026, 10, 7, 16, 12)
        val row = worldRow(WorldCity("London", "Europe/London"), now, ist)
        assertEquals("11:42 am", row.time)
        assertEquals("−4h 30m", row.offsetNote)
    }

    @Test
    fun `a city ahead of home on the next day says which day`() {
        val now = at("Asia/Kolkata", 2026, 10, 7, 22, 0) // Wed night in Pune
        val row = worldRow(WorldCity("Tokyo", "Asia/Tokyo"), now, ist)
        assertEquals("1:30 am", row.time)
        assertEquals("thu", row.dayNote)
        assertEquals("+3h 30m", row.offsetNote)
        assertFalse(row.day)
    }

    @Test
    fun `twelve o'clock reads twelve`() {
        val row = worldRow(WorldCity("Pune", "Asia/Kolkata"), at("Asia/Kolkata", 2026, 10, 7, 0, 5), ist)
        assertEquals("12:05 am", row.time)
        assertEquals("12:00 pm", worldRow(WorldCity("Pune", "Asia/Kolkata"), at("Asia/Kolkata", 2026, 10, 7, 12, 0), ist).time)
    }

    @Test
    fun `city search matches part of a name and ignores blanks`() {
        assertTrue(searchCities("york").any { it.name == "New York" })
        assertEquals(emptyList<WorldCity>(), searchCities("  "))
        assertNotNull(WORLD_CITIES.firstOrNull { it.name == "Pune" })
        assertTrue(WORLD_CITIES.all { runCatching { ZoneId.of(it.zoneId) }.isSuccess })
    }

    // --- buzz ------------------------------------------------------------------------------

    @Test
    fun `a step buzz is one pulse, the last is a longer double`() {
        val step = buzzPattern(BuzzStrength.SHORT, finish = false)
        assertEquals(listOf(0L, 250L), step.timings.toList())
        val end = buzzPattern(BuzzStrength.SHORT, finish = true)
        assertTrue(end.timings.size > step.timings.size)
        assertEquals(end.timings.size, end.amplitudes.size)
        assertTrue(end.timings.sum() > step.timings.sum() * 2)
    }

    @Test
    fun `strengths run from gentle to strong`() {
        val gentle = buzzPattern(BuzzStrength.GENTLE, false)
        val strong = buzzPattern(BuzzStrength.STRONG, false)
        assertTrue(gentle.amplitudes.max() < strong.amplitudes.max())
        assertTrue(gentle.timings.sum() < strong.timings.sum())
        assertTrue(strong.amplitudes.max() <= 255)
    }

    @Test
    fun `typed durations are minutes unless they say otherwise`() {
        assertEquals(360, parseDuration("6"))
        assertEquals(390, parseDuration("6:30"))
        assertEquals(3900, parseDuration("1:05:00"))
        assertEquals(90, parseDuration("90s"))
        assertEquals(0, parseDuration("0"))
        assertNull(parseDuration(""))
        assertNull(parseDuration("abc"))
        assertNull(parseDuration("6:"))
        assertNull(parseDuration("-3"))
        assertNull(parseDuration("1:2:3:4"))
    }

    @Test
    fun `a quick timer is told from a set by its id`() {
        assertTrue(session().copy(id = "timer-1").isTimer)
        assertFalse(session().copy(id = "set-1").isTimer)
    }

    // --- dial and ring -----------------------------------------------------------------------

    @Test
    fun `hands sit where the time puts them`() {
        val a = dialAngles(4, 12, 30)
        assertEquals(126f + 0.25f, a.hour, 0.01f)
        assertEquals(72f + 3f, a.minute, 0.01f)
        assertEquals(180f, a.second, 0.01f)
        assertEquals(0f, dialAngles(12, 0, 0).hour, 0.01f)
        assertEquals(0f, dialAngles(0, 0, 0).hour, 0.01f)
    }

    @Test
    fun `an alarm is an arc clockwise from the hour hand, wrapping past twelve`() {
        assertEquals(69f, sweepBetween(dialAngle(4, 12), dialAngle(6, 30)), 0.5f)
        assertEquals(291f, sweepBetween(dialAngle(6, 30), dialAngle(4, 12)), 1f)
        assertEquals(0f, sweepBetween(10f, 10f), 0.001f)
    }

    private fun kriyaSteps() = flatten(kriya).map { SessionStep(it.label, it.ms, it.partName) }

    @Test
    fun `the ring has an arc per step sized by its share, gaps between`() {
        val arcs = ringArcs(kriyaSteps())
        assertEquals(8, arcs.size)
        // 322 degrees of arc for 29 minutes: a 2 minute step is about 22.2, a 6 minute one 66.6.
        assertEquals(22.21f, arcs[0].sweepDeg, 0.05f)
        assertEquals(66.62f, arcs[4].sweepDeg, 0.05f)
        assertEquals(322f, arcs.sumOf { it.sweepDeg.toDouble() }.toFloat(), 0.1f)
        assertEquals(5f, arcs[0].startDeg, 0.01f)
    }

    @Test
    fun `gaps are wider between parts than between steps`() {
        val arcs = ringArcs(kriyaSteps())
        fun gap(i: Int) = arcs[i + 1].startDeg - (arcs[i].startDeg + arcs[i].sweepDeg)
        assertEquals(3f, gap(0), 0.01f)
        assertEquals(10f, gap(3), 0.01f)
        assertEquals(3f, gap(4), 0.01f)
        // The ring closes with the wide gap at the top.
        assertEquals(355f, arcs.last().startDeg + arcs.last().sweepDeg, 0.1f)
    }

    @Test
    fun `an empty set has no ring`() {
        assertEquals(emptyList<RingArc>(), ringArcs(emptyList()))
    }

    @Test
    fun `a world row says how far through its day a city is`() {
        val row = worldRow(WorldCity("Pune", "Asia/Kolkata"), at("Asia/Kolkata", 2026, 10, 7, 18, 0), ist)
        assertEquals(0.75f, row.dayFraction, 0.001f)
        assertEquals(0.5f, worldRow(WorldCity("Pune", "Asia/Kolkata"), at("Asia/Kolkata", 2026, 10, 7, 12, 0), ist).dayFraction, 0.001f)
    }

    @Test
    fun `a session step keeps its part through storage`() {
        val s = startSession("x", "t", kriyaSteps(), t0)!!
        assertEquals("kriya", decodeSessions(encodeSessions(listOf(s))).single().steps.last().part)
    }
}

class TimerSetTileTest {
    private fun session(id: String) = Session(id, "x", listOf(SessionStep("a", 1000)), 0, 5_000)

    @org.junit.Test
    fun `tile activity name round trips the set id`() {
        org.junit.Assert.assertEquals("1699", TimerSetTile.decode(TimerSetTile.encode("1699")))
        org.junit.Assert.assertNull(TimerSetTile.decode("com.android.clock/.Main"))
        org.junit.Assert.assertNull(TimerSetTile.decode("timerset:"))
        org.junit.Assert.assertNull(TimerSetTile.decode(null))
    }

    @org.junit.Test
    fun `a session finds its saved set by id`() {
        val mine = session(sessionIdForSet(1_700_000_000_000L, "42"))
        val other = session(sessionIdForSet(1_700_000_000_001L, "43"))
        val timer = session("timer-1700000000002")
        val old = session("set-1700000000003")
        org.junit.Assert.assertEquals("42", mine.setId())
        org.junit.Assert.assertNull(timer.setId())
        org.junit.Assert.assertNull(old.setId())
        org.junit.Assert.assertSame(mine, sessionForSet(listOf(timer, old, other, mine), "42"))
        org.junit.Assert.assertNull(sessionForSet(listOf(timer, old, other), "42"))
        org.junit.Assert.assertNull(sessionForSet(listOf(mine), null))
        org.junit.Assert.assertFalse(mine.isTimer)
    }
}
