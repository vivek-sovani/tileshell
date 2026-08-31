package com.tileshell.core.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sentinel league slug for cricket in [SPORTS_LEAGUES] — deliberately not a
 * real `{sport}/{league}` ESPN path segment (see [SportsCatalog]'s original
 * reasoning): cricket has no single evergreen league id the way a club
 * league does, so [fetchCricketMatches] and [CRICKET_TEAMS] are used instead
 * of the generic [fetchSportsTeams]/[fetchSportsSchedule] pair whenever a
 * sports tile's league slug equals this.
 */
const val CRICKET_LEAGUE_SLUG = "cricket"

/**
 * The major international sides, each with ESPN's own stable numeric
 * cricket team id — found by querying `site.web.api.espn.com`'s cross-sport
 * search for each team name and keeping the cricket-sport (`s:200`) result
 * (verified live; these ids are a team's permanent identity, unlike a
 * tournament id, so they don't need updating season to season). Limited to
 * Test-playing/major associate nations rather than every domestic franchise,
 * since there's no single browsable "all cricket teams" endpoint to draw a
 * complete list from the way [fetchSportsTeams] does for club sports.
 */
val CRICKET_TEAMS: List<SportsTeam> = listOf(
    SportsTeam("6", "India", "IND"),
    SportsTeam("2", "Australia", "AUS"),
    SportsTeam("1", "England", "ENG"),
    SportsTeam("7", "Pakistan", "PAK"),
    SportsTeam("3", "South Africa", "SA"),
    SportsTeam("5", "New Zealand", "NZ"),
    SportsTeam("8", "Sri Lanka", "SL"),
    SportsTeam("25", "Bangladesh", "BAN"),
    SportsTeam("4", "West Indies", "WI"),
    SportsTeam("40", "Afghanistan", "AFG"),
    SportsTeam("9", "Zimbabwe", "ZIM"),
    SportsTeam("29", "Ireland", "IRE"),
)

/**
 * The 10 current IPL franchises, ids found the same way as [CRICKET_TEAMS]
 * (`site.web.api.espn.com/apis/search/v2`, filtered to the cricket sport's
 * `s:200` uid prefix, verified live) — kept separate from [CRICKET_TEAMS]
 * rather than merged in, so the picker sheet can label them as their own
 * "ipl" section instead of mixing league sides in with national ones.
 * [fetchCricketMatches] already surfaces IPL games under "ongoing now"
 * whenever the tournament is actually in season (its cross-tournament feed
 * isn't scoped to a fixed roster), so this list only matters for following a
 * franchise ahead of/between seasons, the same way [CRICKET_TEAMS] lets you
 * follow a country year-round.
 */
val IPL_TEAMS: List<SportsTeam> = listOf(
    SportsTeam("335978", "Mumbai Indians", "MI"),
    SportsTeam("335974", "Chennai Super Kings", "CSK"),
    SportsTeam("335970", "Royal Challengers Bengaluru", "RCB"),
    SportsTeam("335971", "Kolkata Knight Riders", "KKR"),
    SportsTeam("335975", "Delhi Capitals", "DC"),
    SportsTeam("335973", "Punjab Kings", "PBKS"),
    SportsTeam("335977", "Rajasthan Royals", "RR"),
    SportsTeam("628333", "Sunrisers Hyderabad", "SRH"),
    SportsTeam("1298769", "Gujarat Titans", "GT"),
    SportsTeam("1298768", "Lucknow Super Giants", "LSG"),
)

/**
 * Every cricket match ESPN currently has listed as live or imminent, across
 * *every* tournament worldwide — there's no per-team schedule endpoint for
 * cricket the way club sports have, so following a specific team means
 * fetching this whole cross-tournament feed and filtering by team id
 * ([pickRelevantMatch] + [snapshotFor] then work identically to any other
 * sport once that's done). Empty on any failure, or whenever nothing
 * cricket-related is currently active anywhere.
 */
suspend fun fetchCricketMatches(): List<SportsMatchEvent> {
    val body = httpGetText("https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=cricket") ?: return emptyList()
    return runCatching {
        val sports = JSONObject(body).getJSONArray("sports")
        val events = mutableListOf<SportsMatchEvent>()
        for (s in 0 until sports.length()) {
            val leagues = sports.getJSONObject(s).optJSONArray("leagues") ?: continue
            for (l in 0 until leagues.length()) {
                val league = leagues.getJSONObject(l)
                val leagueId = league.optString("id").ifEmpty { null }
                val leagueEvents = league.optJSONArray("events") ?: continue
                for (e in 0 until leagueEvents.length()) {
                    parseCricketEvent(leagueEvents.getJSONObject(e), leagueId)?.let { events.add(it) }
                }
            }
        }
        events
    }.getOrDefault(emptyList())
}

private fun parseCricketEvent(ev: JSONObject, leagueId: String?): SportsMatchEvent? {
    val epochMillis = parseEspnInstant(ev.optString("date")) ?: return null
    val competitors = ev.optJSONArray("competitors") ?: return null
    if (competitors.length() < 2) return null
    val c0 = competitors.getJSONObject(0)
    val c1 = competitors.getJSONObject(1)
    val c0IsHome = c0.optString("homeAway") == "home"
    val home = if (c0IsHome) c0 else c1
    val away = if (c0IsHome) c1 else c0
    val statusType = ev.optJSONObject("fullStatus")?.optJSONObject("type")
    return SportsMatchEvent(
        id = ev.optString("id"),
        epochMillis = epochMillis,
        state = statusType?.optString("state")?.ifEmpty { null } ?: ev.optString("status"),
        statusDetail = statusType?.optString("shortDetail")?.ifEmpty { null } ?: ev.optString("summary"),
        homeId = home.optString("id"),
        homeAbbr = home.optString("abbreviation"),
        homeName = home.optString("displayName"),
        homeScore = home.optString("score", "-"),
        awayId = away.optString("id"),
        awayAbbr = away.optString("abbreviation"),
        awayName = away.optString("displayName"),
        awayScore = away.optString("score", "-"),
        leagueId = leagueId,
    )
}

/**
 * Live scorecard detail for a cricket match: the not-out batsmen (currently
 * at the crease) and the standout bowler from the most recent innings'
 * "matchcards" — ESPN's own name for the per-innings batting/bowling/
 * partnerships breakdown (`site.web.api.espn.com`, the same host
 * [fetchCricketMatches] uses; the Akamai-fronted `site.api.espn.com` blocks
 * this call the same way it blocks cricket search). [leagueId] is the
 * tournament id [fetchCricketMatches] already tagged the event with — there's
 * no evergreen league id to hardcode here the way club sports have one.
 */
suspend fun fetchCricketMatchDetail(leagueId: String, eventId: String): SportsMatchDetail {
    val body = httpGetText("https://site.web.api.espn.com/apis/site/v2/sports/cricket/$leagueId/summary?event=$eventId")
        ?: return SportsMatchDetail(webUrl = null)
    return runCatching {
        val root = JSONObject(body)
        val webUrl = root.optJSONObject("header")?.optJSONArray("links")?.optJSONObject(0)
            ?.optString("href")?.ifEmpty { null }
        val contributors = parseCricketContributors(root.optJSONArray("matchcards"))
        SportsMatchDetail(webUrl = webUrl, battingLines = contributors.batting, bowlingLines = contributors.bowling)
    }.getOrDefault(SportsMatchDetail(webUrl = null))
}

/**
 * The current innings' contributors, one player per line so the tile's back
 * face ([SportsBack]) can lay each out on its own row instead of one
 * cramped, comma-joined blob: every not-out batsman ("A 45*(32)") and the
 * top 3 bowlers by wickets, tie-broken by economy ("C 2/18 (6.0)").
 */
internal data class CricketContributors(val batting: List<String> = emptyList(), val bowling: List<String> = emptyList())

internal fun parseCricketContributors(cards: JSONArray?): CricketContributors {
    if (cards == null || cards.length() == 0) return CricketContributors()
    val allCards = (0 until cards.length()).map { cards.getJSONObject(it) }
    val currentInnings = allCards.maxOfOrNull { it.optString("inningsNumber").toIntOrNull() ?: 0 } ?: return CricketContributors()
    // findLast, not find: ESPN's live `matchcards` array can carry more than
    // one "Batting"/"Bowling" card for the same innings as the match
    // progresses (a fresh snapshot appended rather than the old one mutated
    // in place) — `find` would freeze on whichever card happened to be
    // first, which reads exactly like "batting/bowling never updates" even
    // though the tile keeps re-polling every cycle. The most recently
    // appended card for this innings is always the last one matching.
    fun cardFor(headline: String) = allCards.findLast {
        it.optString("headline") == headline && it.optString("inningsNumber").toIntOrNull() == currentInnings
    }

    val batting = cardFor("Batting")?.optJSONArray("playerDetails")?.let { players ->
        (0 until players.length()).map { players.getJSONObject(it) }
            .filter { it.optString("dismissal") == "not out" && it.optString("runs").isNotEmpty() }
            .map { p -> "${p.optString("playerName")} ${p.optString("runs")}*(${p.optString("ballsFaced")})" }
    }.orEmpty()

    val bowling = cardFor("Bowling")?.optJSONArray("playerDetails")?.let { players ->
        (0 until players.length()).map { players.getJSONObject(it) }
            .sortedWith(
                compareByDescending<JSONObject> { it.optString("wickets").toIntOrNull() ?: -1 }
                    .thenBy { it.optString("economyRate").toDoubleOrNull() ?: Double.MAX_VALUE },
            )
            .take(3)
            .map { p -> "${p.optString("playerName")} ${p.optString("wickets")}/${p.optString("conceded")} (${p.optString("overs")})" }
    }.orEmpty()

    return CricketContributors(batting, bowling)
}
