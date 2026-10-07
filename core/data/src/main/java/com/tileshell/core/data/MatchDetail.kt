package com.tileshell.core.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One table on a match's detail page: a scorecard, a box score, stats or key
 * events. A cell may hold two lines separated by a newline (a cricket batter
 * and how they were out); the second line is shown dimmer. [widths] are
 * relative column widths (null: first column wide, the rest equal).
 */
data class DetailTable(
    val title: String,
    val header: List<String>,
    val rows: List<List<String>>,
    val widths: List<Float>? = null,
)

/**
 * What the sports hub shows when a match is opened, read from ESPN's summary
 * for it on demand. [now] is the "right now" block of a live match (the
 * batters at the crease and bowlers' figures in cricket, the last event in
 * football, the top performers in a box-score sport). [note] explains what's
 * missing or adds context (a cricket match's toss or result line, "no
 * scorecard for this match").
 */
data class MatchDetailData(
    val statusLine: String,
    val now: List<String>,
    val tables: List<DetailTable>,
    val note: String?,
    val webUrl: String?,
)

private const val ESPN_SITE = "https://site.api.espn.com/apis/site/v2/sports"
private const val ESPN_CRICKET = "https://site.web.api.espn.com/apis/site/v2/sports/cricket"

/** The detail page for [match], or null when ESPN has nothing for it or can't be reached. */
suspend fun fetchMatchDetailData(match: HubMatch): MatchDetailData? {
    val url = if (match.leagueSlug == CRICKET_LEAGUE_SLUG) {
        val leagueId = match.event.leagueId ?: return null
        "$ESPN_CRICKET/$leagueId/summary?event=${match.event.id}"
    } else {
        "$ESPN_SITE/${match.leagueSlug}/summary?event=${match.event.id}"
    }
    val body = httpGetText(url) ?: return null
    return runCatching { parseMatchDetail(JSONObject(body), match.leagueSlug) }.getOrNull()
}

/** Picks the parser for the sport. Pure. */
internal fun parseMatchDetail(root: JSONObject, leagueSlug: String): MatchDetailData = when {
    leagueSlug == CRICKET_LEAGUE_SLUG -> parseCricketDetail(root)
    leagueSlug.startsWith("soccer/") -> parseSoccerDetail(root)
    else -> parseBoxscoreDetail(root, leagueSlug)
}

private fun JSONObject.obj(name: String): JSONObject? = optJSONObject(name)

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun headerCompetition(root: JSONObject): JSONObject? =
    root.obj("header")?.optJSONArray("competitions")?.optJSONObject(0)

/** Home side first, whatever order ESPN lists them in. */
private fun competitors(root: JSONObject): List<JSONObject> =
    headerCompetition(root)?.optJSONArray("competitors").objects().sortedBy { if (it.optString("homeAway") == "home") 0 else 1 }

private fun abbr(competitor: JSONObject): String =
    competitor.obj("team")?.optString("abbreviation").orEmpty().ifEmpty { competitor.obj("team")?.optString("shortDisplayName").orEmpty() }

private fun statusOf(root: JSONObject): JSONObject? = headerCompetition(root)?.obj("status")?.obj("type")

/** "Full Time" for a finished match, ESPN's own detail ("68'", "Q3 4:12") while it is on. */
private fun statusLineOf(root: JSONObject): String {
    val type = statusOf(root) ?: return ""
    val state = type.optString("state")
    val text = if (state == SPORTS_STATE_LIVE) type.optString("detail") else type.optString("description")
    return text.ifEmpty { type.optString("detail") }
}

private fun isLive(root: JSONObject): Boolean = statusOf(root)?.optString("state") == SPORTS_STATE_LIVE

private fun webUrlOf(root: JSONObject): String? = findSummaryLink(root.obj("header")?.optJSONArray("links"))

// --- Football -------------------------------------------------------------

private val SOCCER_SKIPPED_EVENTS = setOf("Kickoff", "Halftime", "Start 2nd Half", "End Regular Time", "End Extra Time", "Start Extra Time", "Full Time", "Start Shootout")

private val SOCCER_STATS = listOf(
    "possessionPct" to "Possession",
    "totalShots" to "Shots",
    "shotsOnTarget" to "On target",
    "wonCorners" to "Corners",
    "foulsCommitted" to "Fouls",
    "offsides" to "Offsides",
    "yellowCards" to "Yellow cards",
    "redCards" to "Red cards",
    "saves" to "Saves",
)

internal fun parseSoccerDetail(root: JSONObject): MatchDetailData {
    val comps = competitors(root)
    val tables = mutableListOf<DetailTable>()

    val events = root.optJSONArray("keyEvents").objects().filter { it.obj("type")?.optString("text") !in SOCCER_SKIPPED_EVENTS }
    val eventRows = events.take(40).map { e ->
        val type = e.obj("type")?.optString("text").orEmpty()
        val team = e.obj("team")?.optString("displayName").orEmpty()
        val who = if (type == "Substitution") {
            e.optString("text").substringAfter(". ", e.optString("text"))
        } else {
            val player = e.optJSONArray("participants").objects().firstOrNull()?.obj("athlete")?.optString("displayName").orEmpty()
            listOf(player, team).filter { it.isNotEmpty() }.joinToString(" · ")
        }
        listOf(e.obj("clock")?.optString("displayValue").orEmpty(), type, who)
    }
    if (eventRows.isNotEmpty()) tables += DetailTable("key events", listOf("min", "event", ""), eventRows, listOf(0.7f, 1.3f, 2.6f))

    val teams = root.obj("boxscore")?.optJSONArray("teams").objects().sortedBy { if (it.optString("homeAway") == "home") 0 else 1 }
    if (teams.size == 2) {
        fun stat(team: JSONObject, name: String): String? =
            team.optJSONArray("statistics").objects().firstOrNull { it.optString("name") == name }?.optString("displayValue")?.ifEmpty { null }
        val rows = SOCCER_STATS.mapNotNull { (name, label) ->
            val home = stat(teams[0], name) ?: return@mapNotNull null
            val away = stat(teams[1], name) ?: return@mapNotNull null
            val suffix = if (name == "possessionPct") "%" else ""
            listOf(label, home + suffix, away + suffix)
        }
        if (rows.isNotEmpty()) {
            tables += DetailTable("team stats", listOf("", comps.getOrNull(0)?.let(::abbr).orEmpty(), comps.getOrNull(1)?.let(::abbr).orEmpty()), rows, listOf(2f, 1f, 1f))
        }
    }

    val lineups = root.optJSONArray("rosters").objects().sortedBy { if (it.optString("homeAway") == "home") 0 else 1 }
    lineups.forEach { roster ->
        val starters = roster.optJSONArray("roster").objects().filter { it.optBoolean("starter") }
        if (starters.isNotEmpty()) {
            val teamName = roster.obj("team")?.optString("abbreviation").orEmpty().ifEmpty { roster.obj("team")?.optString("displayName").orEmpty() }
            tables += DetailTable(
                "$teamName line-up" + roster.optString("formation").let { if (it.isNotEmpty()) " · $it" else "" },
                listOf("player", "pos"),
                starters.map { p ->
                    listOf(
                        p.obj("athlete")?.optString("displayName").orEmpty().let { n -> p.optString("jersey").let { j -> if (j.isNotEmpty()) "$j  $n" else n } },
                        p.obj("position")?.optString("abbreviation").orEmpty(),
                    )
                },
                listOf(3f, 1f),
            )
        }
    }

    val live = isLive(root)
    val now = if (live) events.lastOrNull()?.let { e ->
        val clock = e.obj("clock")?.optString("displayValue").orEmpty()
        listOf("latest: $clock ${e.obj("type")?.optString("text").orEmpty()} · ${e.optString("shortText")}".replace("  ", " ").trim())
    }.orEmpty() else emptyList()
    return MatchDetailData(statusLineOf(root), now, tables, note = null, webUrl = webUrlOf(root))
}

// --- Box-score sports (basketball, hockey, American football, baseball) ---

private val BASKETBALL_TEAM_STATS = listOf(
    "fieldGoalsMade-fieldGoalsAttempted", "fieldGoalPct", "threePointFieldGoalsMade-threePointFieldGoalsAttempted",
    "threePointFieldGoalPct", "freeThrowsMade-freeThrowsAttempted", "totalRebounds", "assists", "steals", "blocks", "turnovers",
)

/** Player columns worth a phone's width, in the order they read best. The first of these a group has are used. */
private val PLAYER_COLUMNS = listOf(
    "MIN", "PTS", "REB", "AST", "G", "A", "+/-", "TOI", "SV", "SA", "GA", "H", "R", "RBI", "AB", "IP", "ER", "K", "C/ATT", "YDS", "TD", "CAR", "REC",
)

internal fun periodLabels(leagueSlug: String, count: Int): List<String> {
    val regulation = when {
        leagueSlug.startsWith("basketball/") || leagueSlug.startsWith("football/") -> 4
        leagueSlug.startsWith("hockey/") -> 3
        else -> count
    }
    return (1..count).map { i ->
        when {
            i <= regulation -> i.toString()
            count - regulation == 1 -> "OT"
            else -> "OT${i - regulation}"
        }
    }
}

internal fun parseBoxscoreDetail(root: JSONObject, leagueSlug: String): MatchDetailData {
    val comps = competitors(root)
    val tables = mutableListOf<DetailTable>()

    val periods = comps.maxOfOrNull { it.optJSONArray("linescores")?.length() ?: 0 } ?: 0
    if (periods > 0) {
        val labels = periodLabels(leagueSlug, periods)
        val rows = comps.map { c ->
            val scores = c.optJSONArray("linescores").objects().map { it.optString("displayValue") }
            listOf(abbr(c)) + (0 until periods).map { scores.getOrElse(it) { "" } } + c.optString("score")
        }
        tables += DetailTable("score", listOf("") + labels + "T", rows, listOf(1.6f) + List(periods) { 1f } + 1.2f)
    }

    val teams = root.obj("boxscore")?.optJSONArray("teams").objects().sortedBy { if (it.optString("homeAway") == "home") 0 else 1 }
    if (teams.size == 2) {
        val homeStats = teams[0].optJSONArray("statistics").objects().filter { it.optString("label").isNotEmpty() }
        val chosen = if (leagueSlug.startsWith("basketball/")) {
            BASKETBALL_TEAM_STATS.mapNotNull { name -> homeStats.firstOrNull { it.optString("name") == name } }
        } else {
            homeStats.take(8)
        }
        val rows = chosen.mapNotNull { s ->
            val name = s.optString("name")
            val away = teams[1].optJSONArray("statistics").objects().firstOrNull { it.optString("name") == name } ?: return@mapNotNull null
            val suffix = if (s.optString("label").contains('%')) "%" else ""
            listOf(s.optString("label"), s.optString("displayValue") + suffix, away.optString("displayValue") + suffix)
        }
        if (rows.isNotEmpty()) {
            tables += DetailTable("team stats", listOf("", comps.getOrNull(0)?.let(::abbr).orEmpty(), comps.getOrNull(1)?.let(::abbr).orEmpty()), rows, listOf(2.2f, 1f, 1f))
        }
    }

    val players = root.obj("boxscore")?.optJSONArray("players").objects().sortedBy { p ->
        if (teams.firstOrNull { it.obj("team")?.optString("id") == p.obj("team")?.optString("id") }?.optString("homeAway") == "home") 0 else 1
    }
    players.forEach { teamPlayers ->
        val teamAbbr = teamPlayers.obj("team")?.optString("abbreviation").orEmpty()
        val groups = teamPlayers.optJSONArray("statistics").objects()
        fun kind(group: JSONObject) = group.optString("type").ifEmpty { group.optString("name") }
        // Groups with no players (hockey's "skaters" is only a totals row) are skipped below.
        groups.forEach { group ->
            val labels = group.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val picks = PLAYER_COLUMNS.mapNotNull { col -> labels.indexOf(col).takeIf { it >= 0 } }.take(4).ifEmpty { labels.indices.take(4).toList() }
            val athletes = group.optJSONArray("athletes").objects().filter { !it.optBoolean("didNotPlay") && (it.optJSONArray("stats")?.length() ?: 0) > 0 }
            if (athletes.isEmpty() || picks.isEmpty()) return@forEach
            val title = listOf(teamAbbr, kind(group).takeIf { it.isNotEmpty() && groups.size > 1 }).filterNotNull().joinToString(" ")
            tables += DetailTable(
                title,
                listOf("player") + picks.map { labels[it] },
                athletes.take(12).map { a ->
                    val stats = a.optJSONArray("stats")
                    listOf(a.obj("athlete")?.optString("displayName").orEmpty()) + picks.map { stats?.optString(it).orEmpty() }
                },
                listOf(2.4f) + List(picks.size) { 1f },
            )
        }
    }

    val leaders = root.optJSONArray("leaders").objects().flatMap { team ->
        val teamAbbr = team.obj("team")?.optString("abbreviation").orEmpty()
        team.optJSONArray("leaders").objects().take(3).mapNotNull { cat ->
            val top = cat.optJSONArray("leaders").objects().firstOrNull() ?: return@mapNotNull null
            val player = top.obj("athlete")?.optString("displayName").orEmpty()
            Triple(teamAbbr, cat.optString("displayName"), "$player  ${top.optString("displayValue")}")
        }
    }
    val now = if (isLive(root)) leaders.map { (t, cat, who) -> "$t · $cat: $who" } else emptyList()
    if (leaders.isNotEmpty() && !isLive(root)) {
        tables += DetailTable("top performers", listOf("", "", ""), leaders.map { (t, cat, who) -> listOf(t, cat, who) }, listOf(0.8f, 1.2f, 2.6f))
    }
    return MatchDetailData(statusLineOf(root), now, tables, note = null, webUrl = webUrlOf(root))
}

// --- Cricket ----------------------------------------------------------------

internal fun parseCricketDetail(root: JSONObject): MatchDetailData {
    val live = isLive(root)
    val cards = root.optJSONArray("matchcards").objects()
    val tables = mutableListOf<DetailTable>()
    cards.mapNotNull { it.optString("inningsNumber").toIntOrNull() }.distinct().sorted().forEach { innings ->
        fun cardFor(headline: String) = cards.findLast { it.optString("headline") == headline && it.optString("inningsNumber").toIntOrNull() == innings }
        val team = cardFor("Batting")?.optString("teamName").orEmpty().ifEmpty { "innings $innings" }
        cardFor("Batting")?.optJSONArray("playerDetails").objects().takeIf { it.isNotEmpty() }?.let { players ->
            tables += DetailTable(
                "$team batting",
                listOf("batter", "R", "B", "4s", "6s"),
                players.map { p ->
                    val out = p.optString("dismissal")
                    val name = p.optString("playerName") + if (out == "not out") "*" else ""
                    val second = when {
                        p.optString("runs").isEmpty() && out.isEmpty() -> "yet to bat"
                        out.isNotEmpty() && out != "not out" -> out
                        else -> null
                    }
                    listOf(if (second != null) "$name\n$second" else name, p.optString("runs"), p.optString("ballsFaced"), p.optString("fours"), p.optString("sixes"))
                },
                listOf(3f, 1f, 1f, 1f, 1f),
            )
        }
        cardFor("Bowling")?.optJSONArray("playerDetails").objects().takeIf { it.isNotEmpty() }?.let { players ->
            tables += DetailTable(
                "bowling in innings $innings",
                listOf("bowler", "O", "M", "R", "W", "econ"),
                players.map { p -> listOf(p.optString("playerName"), p.optString("overs"), p.optString("maidens"), p.optString("conceded"), p.optString("wickets"), p.optString("economyRate")) },
                listOf(3f, 1f, 1f, 1f, 1f, 1.2f),
            )
        }
    }

    val noteTexts = root.optJSONArray("notes").objects().filter { it.optString("type") in setOf("toss", "result", "closeofplay") }.map { it.optString("text") }.filter { it.isNotEmpty() }
    val contributors = parseCricketContributors(root.optJSONArray("matchcards"))
    val now = if (live) buildList {
        if (contributors.batting.isNotEmpty()) add("at the crease: " + contributors.batting.joinToString(", "))
        if (contributors.bowling.isNotEmpty()) add("bowling: " + contributors.bowling.joinToString(", "))
    } else emptyList()
    val missing = if (tables.isEmpty()) "no scorecard from espn for this match" else null
    val note = (noteTexts + listOfNotNull(missing)).joinToString("\n").ifEmpty { null }
    val type = statusOf(root)
    val status = type?.optString("description").orEmpty().ifEmpty { headerCompetition(root)?.obj("status")?.optString("summary").orEmpty() }
    return MatchDetailData(status, now, tables, note, webUrlOf(root))
}
