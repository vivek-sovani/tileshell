package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class SportsHubTest {

    private val now = 1_800_000_000_000L // 2027-01-15 08:00 UTC
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun match(
        id: String, state: String, at: Long, home: String = "1", away: String = "2",
        league: String = "soccer/eng.1", homeScore: String = "1", awayScore: String = "0",
    ) = HubMatch(
        SportsMatchEvent(
            id = id, epochMillis = at, state = state, statusDetail = "", homeId = home, homeAbbr = "H", homeName = "Home",
            homeScore = homeScore, awayId = away, awayAbbr = "A", awayName = "Away", awayScore = awayScore,
        ),
        leagueSlug = league, leagueName = "League",
    )

    private fun team(league: String, id: String) = SportsTile.Selection(league, id, "Team $id")

    // --- live page ---------------------------------------------------------------

    @Test
    fun `today lists live first, then scheduled soonest first, then finished newest first`() {
        val m = listOf(
            match("done1", "post", now - 4 * hour),
            match("done2", "post", now - 2 * hour),
            match("later", "pre", now + 5 * hour),
            match("soon", "pre", now + hour),
            match("live", "in", now - hour),
        )
        assertEquals(listOf("live", "soon", "later", "done2", "done1"), hubTodayMatches(m, now).map { it.event.id })
    }

    @Test
    fun `a match in play stays even when it began on an earlier day`() {
        val m = listOf(match("test", "in", now - 3 * day), match("old", "post", now - 3 * day))
        assertEquals(listOf("test"), hubTodayMatches(m, now).map { it.event.id })
    }

    @Test
    fun `today is the calendar day in the user's zone`() {
        // now is 08:00 UTC; at +5:30 it is 13:30 on the 15th. 20:00 UTC on the 14th is 01:30 on the 15th there.
        val offset = 5 * hour + 30 * 60_000L
        val m = listOf(
            match("early", "post", now - 12 * hour),
            match("yesterdayThere", "post", now - 20 * hour),
        )
        assertEquals(listOf("early"), hubTodayMatches(m, now, offset).map { it.event.id })
    }

    @Test
    fun `a match listed twice shows once and the same id in two leagues is two matches`() {
        assertEquals(1, hubTodayMatches(listOf(match("a", "in", now), match("a", "in", now)), now).size)
        assertEquals(2, hubTodayMatches(listOf(match("1", "in", now, league = "soccer/eng.1"), match("1", "in", now, league = "soccer/esp.1")), now).size)
    }

    // --- scopes ------------------------------------------------------------------

    @Test
    fun `my teams shows only games a favourite team plays`() {
        val fav = SportsFavorites(listOf("soccer/eng.1"), listOf(team("soccer/eng.1", "359")))
        assertTrue(inScope(match("a", "in", now, home = "359"), HubScope.MY_TEAMS, fav))
        assertTrue(inScope(match("b", "in", now, away = "359"), HubScope.MY_TEAMS, fav))
        assertFalse(inScope(match("c", "in", now), HubScope.MY_TEAMS, fav))
        assertFalse(inScope(match("d", "in", now, home = "359", league = "soccer/esp.1"), HubScope.MY_TEAMS, fav))
    }

    @Test
    fun `my sports shows every game of the favourite sports`() {
        val fav = SportsFavorites(listOf("soccer/eng.1"))
        assertTrue(inScope(match("a", "in", now), HubScope.MY_SPORTS, fav))
        assertFalse(inScope(match("b", "in", now, league = "basketball/nba"), HubScope.MY_SPORTS, fav))
    }

    @Test
    fun `my sports is everything until a sport is marked`() {
        assertTrue(inScope(match("a", "in", now, league = "basketball/nba"), HubScope.MY_SPORTS, SportsFavorites()))
    }

    // --- results page ------------------------------------------------------------

    @Test
    fun `results are finished games inside the window, newest first`() {
        val m = listOf(
            match("week", "post", now - 6 * day),
            match("recent", "post", now - hour),
            match("month", "post", now - 20 * day),
            match("ancient", "post", now - 40 * day),
            match("live", "in", now - hour),
            match("next", "pre", now + hour),
        )
        assertEquals(listOf("recent", "week"), hubResults(m, ResultsRange.WEEK, now).map { it.event.id })
        assertEquals(listOf("recent", "week", "month"), hubResults(m, ResultsRange.MONTH, now).map { it.event.id })
    }

    @Test
    fun `result days run from today back, as yyyyMMdd`() {
        // 2027-01-15 08:00 UTC
        assertEquals(listOf("20270115", "20270114", "20270113"), resultDays(now, 3))
        assertEquals(30, resultDays(now, 30).distinct().size)
        // Across a month boundary.
        assertEquals("20261231", resultDays(now, 16).last())
    }

    @Test
    fun `result days follow the user's zone`() {
        // 22:00 UTC on the 15th is already the 16th at +5:30.
        val lateUtc = now + 14 * hour
        assertEquals("20270115", resultDays(lateUtc, 1).first())
        assertEquals("20270116", resultDays(lateUtc, 1, 5 * hour + 30 * 60_000L).first())
    }

    // --- kick-off label ------------------------------------------------------------

    @Test
    fun `kick-off reads day and local time`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val nowIst = java.time.ZonedDateTime.of(2026, 10, 7, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun at(d: Int, h: Int, min: Int) = java.time.ZonedDateTime.of(2026, 10, d, h, min, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("today 7:30 pm", kickoffLabel(at(7, 19, 30), nowIst, zone))
        assertEquals("tomorrow 1 am", kickoffLabel(at(8, 1, 0), nowIst, zone))
        assertEquals("today 12 pm", kickoffLabel(at(7, 12, 0), nowIst, zone))
        assertEquals("12 oct 12:15 am", kickoffLabel(at(12, 0, 15), nowIst, zone))
        assertEquals("yesterday 9 pm", kickoffLabel(at(6, 21, 0), nowIst, zone))
    }

    // --- favourites ----------------------------------------------------------------

    @Test
    fun `marking a team also marks its sport, unmarking a sport drops its teams`() {
        var fav = SportsFavorites()
        fav = toggleFavoriteTeam(fav, team("soccer/eng.1", "359"))
        assertEquals(listOf("soccer/eng.1"), fav.sports)
        fav = toggleFavoriteTeam(fav, team("basketball/nba", "17"))
        assertEquals(listOf("soccer/eng.1", "basketball/nba"), fav.sports)
        fav = toggleFavoriteSport(fav, "soccer/eng.1")
        assertEquals(listOf("basketball/nba"), fav.sports)
        assertEquals(listOf("17"), fav.teams.map { it.teamId })
    }

    @Test
    fun `unmarking a team keeps its sport`() {
        val t = team("soccer/eng.1", "359")
        val fav = toggleFavoriteTeam(toggleFavoriteTeam(SportsFavorites(), t), t)
        assertEquals(emptyList<SportsTile.Selection>(), fav.teams)
        assertEquals(listOf("soccer/eng.1"), fav.sports)
    }

    @Test
    fun `favourites round trip through their codecs`() {
        val teams = listOf(team("soccer/eng.1", "359"), SportsTile.Selection("cricket", "6", "India | men"))
        assertEquals(teams, decodeFavoriteTeams(encodeFavoriteTeams(teams)))
        assertEquals(listOf("cricket", "soccer/eng.1"), decodeFavoriteSports(encodeFavoriteSports(listOf("cricket", "soccer/eng.1"))))
        assertEquals(emptyList<String>(), decodeFavoriteSports(null))
        assertEquals(emptyList<SportsTile.Selection>(), decodeFavoriteTeams(""))
    }

    @Test
    fun `result letter reads plain scores and skips cricket innings`() {
        fun snap(ours: String, theirs: String, state: String = "post") =
            SportsSnapshot(true, "A", "A", ours, "B", "B", theirs, state, "")
        assertEquals("W", resultLetter(snap("3", "1")))
        assertEquals("L", resultLetter(snap("0", "2")))
        assertEquals("D", resultLetter(snap("1", "1")))
        assertNull(resultLetter(snap("247/4", "250/9")))
        assertNull(resultLetter(snap("3", "1", state = "in")))
    }

    @Test
    fun `a cricket score splits into runs and its note`() {
        assertEquals("352/5" to "48.2/50 ov, target 352", splitScoreNote("352/5 (48.2/50 ov, target 352)"))
        assertEquals("278 & 111" to "28.4 ov, target 279", splitScoreNote("278 & 111 (28.4 ov, target 279)"))
        assertEquals("351/7" to null, splitScoreNote("351/7"))
        assertEquals("3" to null, splitScoreNote("3"))
    }
}
