package com.tileshell.core.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

private fun batter(name: String, dismissal: String, runs: String, balls: String) = JSONObject().apply {
    put("playerName", name)
    put("dismissal", dismissal)
    put("runs", runs)
    put("ballsFaced", balls)
}

private fun bowler(name: String, overs: String, conceded: String, wickets: String, economy: String) = JSONObject().apply {
    put("playerName", name)
    put("overs", overs)
    put("conceded", conceded)
    put("wickets", wickets)
    put("economyRate", economy)
}

private fun card(headline: String, innings: Int, players: List<JSONObject>) = JSONObject().apply {
    put("headline", headline)
    put("inningsNumber", innings.toString())
    put("playerDetails", JSONArray(players))
}

class ParseCricketContributorsTest {

    @Test
    fun `lists the not-out batsmen from the current (highest-numbered) innings, one per line`() {
        val cards = JSONArray(
            listOf(
                card("Batting", 1, listOf(batter("Old", "caught", "80", "60"))),
                card("Batting", 2, listOf(batter("Root", "not out", "24", "37"), batter("Brook", "not out", "18", "36"))),
            ),
        )
        val result = parseCricketContributors(cards)
        assertEquals(listOf("Root 24*(37)", "Brook 18*(36)"), result.batting)
        assertEquals(emptyList<String>(), result.bowling)
    }

    @Test
    fun `a dismissed batsman is excluded even in the current innings`() {
        val cards = JSONArray(listOf(card("Batting", 1, listOf(batter("Out", "lbw", "31", "74")))))
        assertEquals(emptyList<String>(), parseCricketContributors(cards).batting)
    }

    @Test
    fun `bowlers are ranked by wickets, economy breaks a tie, top 3 kept`() {
        val cards = JSONArray(
            listOf(
                card(
                    "Bowling", 3,
                    listOf(
                        bowler("Abbas", "11.3", "33", "1", "2.86"),
                        bowler("Shahzad", "12.0", "38", "2", "3.16"),
                        bowler("Ali", "6.0", "27", "2", "4.5"),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf("Shahzad 2/38 (12.0)", "Ali 2/27 (6.0)", "Abbas 1/33 (11.3)"),
            parseCricketContributors(cards).bowling,
        )
    }

    @Test
    fun `batting and bowling both come back when both are present for the current innings`() {
        val cards = JSONArray(
            listOf(
                card("Batting", 1, listOf(batter("A", "not out", "10", "20"))),
                card("Bowling", 1, listOf(bowler("B", "5.0", "20", "1", "4.0"))),
            ),
        )
        val result = parseCricketContributors(cards)
        assertEquals(listOf("A 10*(20)"), result.batting)
        assertEquals(listOf("B 1/20 (5.0)"), result.bowling)
    }

    @Test
    fun `an empty or null cards array yields nothing, not a crash`() {
        assertEquals(CricketContributors(), parseCricketContributors(null))
        assertEquals(CricketContributors(), parseCricketContributors(JSONArray()))
    }
}
