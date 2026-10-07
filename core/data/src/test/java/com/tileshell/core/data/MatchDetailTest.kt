package com.tileshell.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsed against trimmed, real ESPN summary responses captured for each sport (src/test/resources/espn). */
class MatchDetailTest {

    private fun load(name: String): JSONObject =
        JSONObject(javaClass.getResource("/espn/$name")!!.readText())

    private fun DetailTable.column(i: Int) = rows.map { it.getOrElse(i) { "" } }

    // --- basketball -----------------------------------------------------------------

    @Test
    fun `basketball has a quarter by quarter score with the final total`() {
        val d = parseMatchDetail(load("nba_summary.json"), "basketball/nba")
        val score = d.tables.first { it.title == "score" }
        assertEquals(listOf("", "1", "2", "3", "4", "T"), score.header)
        assertEquals(listOf("CHA", "31", "21", "21", "17", "90"), score.rows[0])
        assertEquals(listOf("BKN", "33", "23", "36", "32", "124"), score.rows[1])
        assertEquals("Final", d.statusLine)
    }

    @Test
    fun `basketball team stats are the headline numbers, home first`() {
        val stats = parseMatchDetail(load("nba_summary.json"), "basketball/nba").tables.first { it.title == "team stats" }
        assertEquals(listOf("", "CHA", "BKN"), stats.header)
        val fg = stats.rows.first { it[0] == "FG" }
        assertEquals("44-80", fg[2])
        assertTrue(stats.rows.any { it[0] == "Assists" && it[2] == "31" })
        assertTrue(stats.rows.first { it[0].contains('%') }[1].endsWith("%"))
    }

    @Test
    fun `basketball box score lists players with minutes, points, rebounds, assists`() {
        val d = parseMatchDetail(load("nba_summary.json"), "basketball/nba")
        val box = d.tables.first { it.title == "BKN" }
        assertEquals(listOf("player", "MIN", "PTS", "REB", "AST"), box.header)
        assertTrue(box.rows.size in 1..12)
        assertTrue(box.rows.any { it[0] == "Julius Randle" && it[2] == "13" })
    }

    @Test
    fun `a finished game lists its top performers and has no live block`() {
        val d = parseMatchDetail(load("nba_summary.json"), "basketball/nba")
        assertTrue(d.tables.any { it.title == "top performers" })
        assertEquals(emptyList<String>(), d.now)
        assertNotNull(d.webUrl)
    }

    // --- football --------------------------------------------------------------------

    @Test
    fun `football lists key events without the kick-off and half-time markers`() {
        val d = parseMatchDetail(load("soccer_summary.json"), "soccer/eng.1")
        val events = d.tables.first { it.title == "key events" }
        assertEquals("Yellow Card", events.rows.first()[1])
        assertEquals("6'", events.rows.first()[0])
        assertTrue(events.rows.none { it[1] == "Kickoff" || it[1] == "Halftime" })
        assertTrue(events.rows.first()[2].contains("Lukic"))
        assertTrue(events.column(1).count { it == "Substitution" } >= 3)
    }

    @Test
    fun `football team stats add the percent to possession`() {
        val stats = parseMatchDetail(load("soccer_summary.json"), "soccer/eng.1").tables.first { it.title == "team stats" }
        val possession = stats.rows.first { it[0] == "Possession" }
        assertTrue(possession[1].endsWith("%") && possession[2].endsWith("%"))
        assertEquals("BRE", stats.header[1])
        assertEquals("FUL", stats.header[2])
    }

    @Test
    fun `football lists each side's starters`() {
        val d = parseMatchDetail(load("soccer_summary.json"), "soccer/eng.1")
        val lineups = d.tables.filter { it.title.contains("line-up") }
        assertEquals(2, lineups.size)
        assertTrue(lineups.all { it.rows.size == 11 })
        assertTrue(lineups[0].rows.any { it[0].contains("Kelleher") || it[1] == "G" })
    }

    @Test
    fun `a live football match shows its latest event`() {
        val root = load("soccer_summary.json")
        root.getJSONObject("header").getJSONArray("competitions").getJSONObject(0).getJSONObject("status").getJSONObject("type")
            .put("state", "in").put("detail", "67'")
        val d = parseMatchDetail(root, "soccer/eng.1")
        assertEquals("67'", d.statusLine)
        assertEquals(1, d.now.size)
        assertTrue(d.now[0].startsWith("latest:"))
    }

    // --- hockey (generic box score) --------------------------------------------------

    @Test
    fun `hockey shows period scores and each group of players, not the empty totals row`() {
        val d = parseMatchDetail(load("nhl_summary.json"), "hockey/nhl")
        val score = d.tables.first { it.title == "score" }
        assertEquals(listOf("", "1", "2", "3", "T"), score.header)
        val titles = d.tables.map { it.title }
        assertTrue(titles.any { it.endsWith("forwards") })
        assertTrue(titles.any { it.endsWith("defenses") })
        assertTrue(titles.any { it.endsWith("goalies") })
        assertTrue(titles.none { it.endsWith("skaters") })
        assertEquals(listOf("player", "G", "A", "+/-", "TOI"), d.tables.first { it.title.endsWith("forwards") }.header)
    }

    @Test
    fun `overtime periods are labelled`() {
        assertEquals(listOf("1", "2", "3", "4", "OT"), periodLabels("basketball/nba", 5))
        assertEquals(listOf("1", "2", "3", "OT1", "OT2"), periodLabels("hockey/nhl", 5))
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9"), periodLabels("baseball/mlb", 9))
    }

    // --- cricket ---------------------------------------------------------------------

    @Test
    fun `cricket lists the batting and bowling of each innings`() {
        val d = parseMatchDetail(load("cricket_summary.json"), CRICKET_LEAGUE_SLUG)
        val batting = d.tables.first { it.title.endsWith("batting") }
        assertEquals(listOf("batter", "R", "B", "4s", "6s"), batting.header)
        assertTrue(batting.rows.any { it[0].endsWith("*") })
        val bowling = d.tables.first { it.title.startsWith("bowling") }
        assertEquals(listOf("bowler", "O", "M", "R", "W", "econ"), bowling.header)
    }

    @Test
    fun `a live cricket match shows who is at the crease and the bowlers' figures`() {
        val d = parseMatchDetail(load("cricket_summary.json"), CRICKET_LEAGUE_SLUG)
        assertTrue(d.now.any { it.startsWith("at the crease:") && it.contains("*(") })
        assertTrue(d.now.any { it.startsWith("bowling:") })
    }

    @Test
    fun `cricket keeps the toss and close of play notes`() {
        val d = parseMatchDetail(load("cricket_summary.json"), CRICKET_LEAGUE_SLUG)
        assertTrue(d.note!!.contains("elected to field first"))
        assertTrue(d.note!!.contains("day 1"))
    }

    @Test
    fun `a cricket match with no scorecard says so and keeps the web link`() {
        val root = load("cricket_summary.json")
        root.remove("matchcards")
        val d = parseMatchDetail(root, CRICKET_LEAGUE_SLUG)
        assertEquals(emptyList<DetailTable>(), d.tables)
        assertEquals(emptyList<String>(), d.now)
        assertTrue(d.note!!.contains("no scorecard"))
        assertNotNull(d.webUrl)
    }

    @Test
    fun `a dismissed batter shows how they were out on a second line`() {
        val root = load("cricket_summary.json")
        val card = root.getJSONArray("matchcards").getJSONObject(0)
        card.getJSONArray("playerDetails").getJSONObject(0).put("dismissal", "c Smith b Jones")
        val d = parseMatchDetail(root, CRICKET_LEAGUE_SLUG)
        val first = d.tables.first { it.title.endsWith("batting") }.rows.first()[0]
        assertTrue(first.contains("\nc Smith b Jones"))
    }

    @Test
    fun `no web link is null rather than blank`() {
        val root = load("nba_summary.json")
        root.getJSONObject("header").remove("links")
        assertNull(parseMatchDetail(root, "basketball/nba").webUrl)
    }

    @Test
    fun `batters yet to bat are labelled`() {
        val d = parseMatchDetail(load("cricket_summary.json"), CRICKET_LEAGUE_SLUG)
        val rows = d.tables.first { it.title.endsWith("batting") }.rows
        assertTrue(rows.any { it[0].endsWith("\nyet to bat") })
        assertTrue(rows.none { it[0].contains("*\n") })
    }
}
