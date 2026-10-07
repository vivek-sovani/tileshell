package com.tileshell.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SportsHubTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun match(id: String, state: String, at: Long, league: String = "soccer/eng.1") = HubMatch(
        SportsMatchEvent(
            id = id, epochMillis = at, state = state, statusDetail = "", homeId = "1", homeAbbr = "H", homeName = "Home",
            homeScore = "0", awayId = "2", awayAbbr = "A", awayName = "Away", awayScore = "0",
        ),
        leagueSlug = league, leagueName = "League",
    )

    @Test
    fun `live comes first then upcoming then finished`() {
        val m = listOf(
            match("done", "post", now - 3 * hour),
            match("soon", "pre", now + 2 * hour),
            match("live", "in", now - hour),
        )
        assertEquals(listOf("live", "soon", "done"), hubLiveMatches(m, now).map { it.event.id })
    }

    @Test
    fun `matches outside the same-day window are left off the live page`() {
        val m = listOf(match("old", "post", now - 40 * hour), match("later", "pre", now + 40 * hour))
        assertEquals(emptyList<String>(), hubLiveMatches(m, now).map { it.event.id })
    }

    @Test
    fun `a match listed twice shows once`() {
        val m = listOf(match("a", "in", now), match("a", "in", now))
        assertEquals(1, hubLiveMatches(m, now).size)
    }

    @Test
    fun `same id in different leagues are different matches`() {
        val m = listOf(match("1", "in", now, "soccer/eng.1"), match("1", "in", now, "soccer/esp.1"))
        assertEquals(2, hubLiveMatches(m, now).size)
    }

    @Test
    fun `week fixtures are the unplayed ones in the next seven days, soonest first`() {
        val day = 24 * hour
        val m = listOf(
            match("d3", "pre", now + 3 * day),
            match("d1", "pre", now + day),
            match("d9", "pre", now + 9 * day),
            match("played", "post", now - day),
        )
        assertEquals(listOf("d1", "d3"), hubFixtures(m, FixtureTab.WEEK, now).map { it.event.id })
    }

    @Test
    fun `results are the finished games newest first`() {
        val m = listOf(match("r1", "post", now - 5 * hour), match("r2", "post", now - hour), match("up", "pre", now + hour))
        assertEquals(listOf("r2", "r1"), hubFixtures(m, FixtureTab.RESULTS, now).map { it.event.id })
    }

    @Test
    fun `today means the same calendar day in the given zone`() {
        val noon = 1_800_000_000_000L - (1_800_000_000_000L % (24 * hour)) + 12 * hour
        val m = listOf(match("same", "pre", noon + 3 * hour), match("next", "pre", noon + 20 * hour))
        assertEquals(listOf("same"), hubFixtures(m, FixtureTab.TODAY, noon).map { it.event.id })
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

    private fun teamMatch(id: String, state: String, at: Long, home: String, away: String, league: String = "soccer/eng.1") = HubMatch(
        SportsMatchEvent(
            id = id, epochMillis = at, state = state, statusDetail = "", homeId = home, homeAbbr = "H", homeName = "Home",
            homeScore = "1", awayId = away, awayAbbr = "A", awayName = "Away", awayScore = "0",
        ),
        leagueSlug = league, leagueName = "League",
    )

    private fun team(league: String, id: String) = SportsTile.Selection(league, id, "Team $id")

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
    fun `live page shows only favourite sports, favourite teams first`() {
        val m = listOf(
            teamMatch("a", "in", now - hour, "1", "2", "soccer/eng.1"),
            teamMatch("b", "in", now - 2 * hour, "359", "9", "soccer/eng.1"),
            teamMatch("c", "in", now - hour, "5", "6", "basketball/nba"),
        )
        val fav = SportsFavorites(sports = listOf("soccer/eng.1"), teams = listOf(team("soccer/eng.1", "359")))
        assertEquals(listOf("b", "a"), hubFavoriteMatches(m, fav, now).map { it.event.id })
    }

    @Test
    fun `live page shows everything when nothing is marked`() {
        val m = listOf(teamMatch("a", "in", now, "1", "2", "soccer/eng.1"), teamMatch("c", "in", now, "5", "6", "basketball/nba"))
        assertEquals(2, hubFavoriteMatches(m, SportsFavorites(), now).size)
    }

    @Test
    fun `team history is its finished games newest first`() {
        val m = listOf(
            teamMatch("old", "post", now - 50 * hour, "359", "9"),
            teamMatch("new", "post", now - 5 * hour, "9", "359"),
            teamMatch("other", "post", now - hour, "1", "2"),
            teamMatch("next", "pre", now + hour, "359", "9"),
        )
        assertEquals(listOf("new", "old"), teamHistory(m, "359", now).map { it.event.id })
        assertEquals(1, teamHistory(m, "359", now, limit = 1).size)
    }

    @Test
    fun `sport results are finished games in favourite sports within the window`() {
        val day = 24 * hour
        val m = listOf(
            teamMatch("keep", "post", now - day, "1", "2", "soccer/eng.1"),
            teamMatch("tooOld", "post", now - 5 * day, "1", "2", "soccer/eng.1"),
            teamMatch("otherSport", "post", now - day, "1", "2", "basketball/nba"),
            teamMatch("live", "in", now, "1", "2", "soccer/eng.1"),
        )
        assertEquals(listOf("keep"), recentSportResults(m, listOf("soccer/eng.1"), now).map { it.event.id })
    }

    private fun ev(state: String, at: Long) = teamMatch("e$at", state, at, "1", "2").event

    @Test
    fun `hub refreshes at the user's rate while something is live`() {
        assertEquals(90_000L, hubSportsRefreshDelayMs(listOf(ev("in", now - hour)), now, 90_000L))
    }

    @Test
    fun `hub waits for the run-up to the next kick-off`() {
        val kickoff = now + 2 * hour
        assertEquals(2 * hour - SPORTS_PREGAME_WAKE_MS, hubSportsRefreshDelayMs(listOf(ev("pre", kickoff)), now, 90_000L))
    }

    @Test
    fun `hub is back at the user's rate once the kick-off is close`() {
        assertEquals(90_000L, hubSportsRefreshDelayMs(listOf(ev("pre", now + 10 * 60_000L)), now, 90_000L))
    }

    @Test
    fun `hub is idle with nothing live or ahead`() {
        assertEquals(SPORTS_IDLE_REFRESH_MS, hubSportsRefreshDelayMs(listOf(ev("post", now - hour)), now, 90_000L))
        assertEquals(SPORTS_IDLE_REFRESH_MS, hubSportsRefreshDelayMs(emptyList(), now, 90_000L))
    }

    @Test
    fun `a far kick-off never sleeps past the idle cap`() {
        assertEquals(SPORTS_IDLE_REFRESH_MS, hubSportsRefreshDelayMs(listOf(ev("pre", now + 30 * hour)), now, 90_000L))
    }
}
