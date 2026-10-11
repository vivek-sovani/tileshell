package com.tileshell.feature.livetiles

import com.tileshell.core.data.HubMatch
import com.tileshell.core.data.SportsFavorites
import com.tileshell.core.data.SportsMatchEvent
import com.tileshell.core.data.SportsTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsHubTileTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun match(id: String, state: String, at: Long, league: String = "soccer/eng.1", home: String = "1", away: String = "2") = HubMatch(
        SportsMatchEvent(
            id = id, epochMillis = at, state = state, statusDetail = "", homeId = home, homeAbbr = "H", homeName = "Home",
            homeScore = "1", awayId = away, awayAbbr = "A", awayName = "Away", awayScore = "0",
        ),
        leagueSlug = league, leagueName = "League",
    )

    @Test
    fun `followed teams decide the matches when any are followed`() {
        val fav = SportsFavorites(sports = listOf("soccer/eng.1"), teams = listOf(SportsTile.Selection("soccer/eng.1", "7", "Team")))
        val m = listOf(match("a", "in", now, home = "7"), match("b", "in", now, home = "8"))
        assertEquals(listOf("a"), sportsHubTileMatches(m, fav, now, 0L).map { it.event.id })
    }

    @Test
    fun `followed sports decide the matches when no team is followed, every sport when none is`() {
        val m = listOf(match("a", "in", now, league = "soccer/eng.1"), match("b", "in", now, league = "basketball/nba"))
        assertEquals(listOf("a"), sportsHubTileMatches(m, SportsFavorites(sports = listOf("soccer/eng.1")), now, 0L).map { it.event.id })
        assertEquals(setOf("a", "b"), sportsHubTileMatches(m, SportsFavorites(), now, 0L).map { it.event.id }.toSet())
    }

    @Test
    fun `live matches come first, then the next kick-off, then finished ones`() {
        val m = listOf(match("done", "post", now - 2 * hour), match("soon", "pre", now + hour), match("live", "in", now - hour))
        assertEquals(listOf("live", "soon", "done"), sportsHubTileMatches(m, SportsFavorites(), now, 0L).map { it.event.id })
    }

    @Test
    fun `pages hold what fits and an empty list has none`() {
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), sportsHubTilePages(listOf(1, 2, 3, 4, 5), 2))
        assertTrue(sportsHubTilePages(emptyList<Int>(), 3).isEmpty())
        assertEquals(1, sportsHubTilePages(listOf(1, 2), 0).first().size) // never a page of nothing
    }

    @Test
    fun `rows that fit grow with the height and are never fewer than one`() {
        assertEquals(1, sportsHubTileRows(60f))
        assertEquals(1, sportsHubTileRows(0f))
        assertTrue(sportsHubTileRows(180f) >= 2)
        assertTrue(sportsHubTileRows(360f) > sportsHubTileRows(180f))
    }
}
