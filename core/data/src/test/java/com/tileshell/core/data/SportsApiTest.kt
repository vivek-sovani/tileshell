package com.tileshell.core.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun event(
    id: String,
    epochMillis: Long,
    state: String,
    homeId: String = "1",
    awayId: String = "2",
    homeScore: String = "0",
    awayScore: String = "0",
) = SportsMatchEvent(
    id = id,
    epochMillis = epochMillis,
    state = state,
    statusDetail = state,
    homeId = homeId,
    homeAbbr = "HOME",
    homeName = "Home Team",
    homeScore = homeScore,
    awayId = awayId,
    awayAbbr = "AWAY",
    awayName = "Away Team",
    awayScore = awayScore,
)

class PickRelevantMatchTest {

    @Test
    fun `a live game always wins, regardless of order`() {
        val past = event("1", 100, "post")
        val live = event("2", 200, "in")
        val future = event("3", 300, "pre")
        assertEquals(live, pickRelevantMatch(listOf(past, future, live), nowMillis = 250))
    }

    @Test
    fun `with no live game, the most recently finished one wins`() {
        val older = event("1", 100, "post")
        val newer = event("2", 200, "post")
        val future = event("3", 300, "pre")
        assertEquals(newer, pickRelevantMatch(listOf(older, newer, future), nowMillis = 250))
    }

    @Test
    fun `with no live or finished game, the soonest upcoming one wins`() {
        val far = event("1", 500, "pre")
        val soon = event("2", 300, "pre")
        assertEquals(soon, pickRelevantMatch(listOf(far, soon), nowMillis = 250))
    }

    @Test
    fun `an empty schedule has nothing to show`() {
        assertNull(pickRelevantMatch(emptyList(), nowMillis = 100))
    }
}

class SnapshotForTest {

    @Test
    fun `home team's own perspective, matched by id`() {
        val ev = event("1", 0, "in", homeId = "6", awayId = "2", homeScore = "102", awayScore = "98")
        val snap = snapshotFor(ev, ourId = "6")
        assertEquals(true, snap.isHome)
        assertEquals("HOME", snap.teamAbbr)
        assertEquals("AWAY", snap.opponentAbbr)
        assertEquals("102", snap.teamScore)
        assertEquals("98", snap.opponentScore)
    }

    @Test
    fun `away team's own perspective, matched by id`() {
        val ev = event("1", 0, "in", homeId = "6", awayId = "2", homeScore = "102", awayScore = "98")
        val snap = snapshotFor(ev, ourId = "2")
        assertEquals(false, snap.isHome)
        assertEquals("AWAY", snap.teamAbbr)
        assertEquals("HOME", snap.opponentAbbr)
        assertEquals("98", snap.teamScore)
        assertEquals("102", snap.opponentScore)
    }
}

class SplitInningsScoreTest {

    @Test
    fun `a two-innings cricket score splits into two lines`() {
        assertEquals(listOf("290", "177/7 (46.5 ov)"), splitInningsScore("290 & 177/7 (46.5 ov)"))
    }

    @Test
    fun `a single-innings score (any club sport, or cricket before a second innings) stays one line`() {
        assertEquals(listOf("110"), splitInningsScore("110"))
    }

    @Test
    fun `a blank score still comes back as one (empty) line, never an empty list`() {
        assertEquals(listOf(""), splitInningsScore(""))
    }
}

class ScoreOfTest {

    @Test
    fun `a plain string score (the scoreboard endpoint's own shape) passes through`() {
        val competitor = JSONObject().put("score", "3")
        assertEquals("3", scoreOf(competitor))
    }

    @Test
    fun `an MLB-NBA-NHL schedule score object uses its displayValue`() {
        val competitor = JSONObject().put("score", JSONObject().put("value", 8.0).put("displayValue", "8"))
        assertEquals("8", scoreOf(competitor))
    }

    @Test
    fun `a soccer schedule score with only a $ref link has nothing to show`() {
        val competitor = JSONObject().put(
            "score",
            JSONObject().put("\$ref", "http://sports.core.api.espn.pvt/v2/sports/soccer/leagues/eng.2/events/1/..."),
        )
        assertEquals("-", scoreOf(competitor))
    }

    @Test
    fun `no score field at all is also just a dash, never a crash`() {
        assertEquals("-", scoreOf(JSONObject()))
    }
}

private fun headerCompetitor(homeAway: String, score: String) = JSONObject().apply {
    put("homeAway", homeAway)
    put("score", score)
}

class ParseHeaderScoresTest {

    @Test
    fun `reads both sides' inline score from the summary endpoint's header`() {
        val root = JSONObject().put(
            "header",
            JSONObject().put(
                "competitions",
                JSONArray(
                    listOf(
                        JSONObject().put(
                            "competitors",
                            JSONArray(listOf(headerCompetitor("home", "1"), headerCompetitor("away", "1"))),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(mapOf("home" to "1", "away" to "1"), parseHeaderScores(root))
    }

    @Test
    fun `missing header yields an empty map, not a crash`() {
        assertEquals(emptyMap<String, String>(), parseHeaderScores(JSONObject()))
    }
}

class ParseEspnInstantTest {

    @Test
    fun `parses ESPN's seconds-omitted date format`() {
        assertEquals(1788003000000L, parseEspnInstant("2026-08-29T11:30Z"))
    }

    @Test
    fun `parses a full ISO instant too`() {
        assertEquals(1788003000000L, parseEspnInstant("2026-08-29T11:30:00Z"))
    }

    @Test
    fun `returns null for garbage`() {
        assertNull(parseEspnInstant("not a date"))
    }
}

class SportsTileCodecTest {

    @Test
    fun `round-trips a league, team id and label`() {
        val encoded = SportsTile.encode("basketball/nba", "1", "Atlanta Hawks")
        assertEquals(SportsTile.Selection("basketball/nba", "1", "Atlanta Hawks"), SportsTile.decode(encoded))
    }

    @Test
    fun `decode rejects a string with no sports prefix`() {
        assertNull(SportsTile.decode("basketball/nba|1|Atlanta Hawks"))
    }

    @Test
    fun `decode rejects a not-yet-picked tile`() {
        assertNull(SportsTile.decode("sports:"))
        assertNull(SportsTile.decode("sports:||"))
    }
}

class SportsCatalogTest {

    @Test
    fun `cricket is in the catalog, routed to its own sentinel slug`() {
        val cricket = sportsLeagueFor(CRICKET_LEAGUE_SLUG)
        assertEquals("Cricket", cricket?.displayName)
    }

    @Test
    fun `every club league slug is a real sport-league path, not the cricket sentinel`() {
        SPORTS_LEAGUES.filter { it.slug != CRICKET_LEAGUE_SLUG }.forEach {
            assertTrue("${it.slug} should contain a '/'", it.slug.contains("/"))
        }
    }

    @Test
    fun `soccer leagues are grouped under the everyday 'football' category, not NFL`() {
        val soccerLeagues = SPORTS_LEAGUES.filter { it.slug.startsWith("soccer/") }
        assertTrue(soccerLeagues.isNotEmpty())
        assertTrue(soccerLeagues.all { it.category == "football" })
        assertEquals("other", SPORTS_LEAGUES.find { it.slug == "football/nfl" }?.category)
    }

    @Test
    fun `every league's category is one of the declared display-order categories`() {
        SPORTS_LEAGUES.forEach {
            assertTrue("${it.slug}'s category '${it.category}' isn't in the display order", it.category in SPORTS_LEAGUE_CATEGORY_ORDER)
        }
    }
}

class CricketTeamsTest {

    @Test
    fun `every major cricket nation has a unique, non-blank id`() {
        val ids = CRICKET_TEAMS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun `india's id matches the one found via live verification`() {
        assertEquals("6", CRICKET_TEAMS.find { it.displayName == "India" }?.id)
    }
}

class IplTeamsTest {

    @Test
    fun `all 10 current franchises have a unique, non-blank id`() {
        assertEquals(10, IPL_TEAMS.size)
        val ids = IPL_TEAMS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun `no id collides with a CRICKET_TEAMS (international) id`() {
        val overlap = IPL_TEAMS.map { it.id }.toSet().intersect(CRICKET_TEAMS.map { it.id }.toSet())
        assertTrue(overlap.isEmpty())
    }

    @Test
    fun `mumbai indians' id matches the one found via live verification`() {
        assertEquals("335978", IPL_TEAMS.find { it.displayName == "Mumbai Indians" }?.id)
    }
}

private fun link(href: String, vararg rels: String) = JSONObject().apply {
    put("href", href)
    put("rel", JSONArray(rels.toList()))
}

class FindSummaryLinkTest {

    @Test
    fun `picks the link tagged summary over other rels`() {
        val links = JSONArray(listOf(link("https://x/report", "recap"), link("https://x/summary", "summary")))
        assertEquals("https://x/summary", findSummaryLink(links))
    }

    @Test
    fun `falls back to the first link when nothing is tagged summary`() {
        val links = JSONArray(listOf(link("https://x/report", "recap")))
        assertEquals("https://x/report", findSummaryLink(links))
    }

    @Test
    fun `null links yields null, not a crash`() {
        assertNull(findSummaryLink(null))
    }
}

private fun goalEvent(scorer: String, minute: String, team: String) = JSONObject().apply {
    put("type", JSONObject().put("type", "goal"))
    put("clock", JSONObject().put("displayValue", minute))
    put("team", JSONObject().put("displayName", team))
    put(
        "participants",
        JSONArray(listOf(JSONObject().put("athlete", JSONObject().put("displayName", scorer)))),
    )
}

class ParseSoccerScorersTest {

    @Test
    fun `only goal-type key events become scorer lines, newest first`() {
        val root = JSONObject().put(
            "keyEvents",
            JSONArray(
                listOf(
                    goalEvent("Alice", "10'", "Home FC"),
                    JSONObject().put("type", JSONObject().put("type", "kickoff")),
                    goalEvent("Bob", "55'", "Away FC"),
                ),
            ),
        )
        assertEquals(listOf("Bob 55' (Away FC)", "Alice 10' (Home FC)"), parseSoccerScorers(root))
    }

    @Test
    fun `no keyEvents field yields an empty list`() {
        assertEquals(emptyList<String>(), parseSoccerScorers(JSONObject()))
    }
}
