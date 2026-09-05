package com.tileshell.core.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

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
 *
 * [dateYyyymmdd] scopes the same feed to one specific past day instead of
 * "today" (verified live: the endpoint's own `dates=YYYYMMDD` param works
 * unchanged) — see [fetchRecentCricketMatchForTeam], which walks this
 * backward to find a followed team's actual last result once it's fallen out
 * of the default "right now" window (typically within a day of finishing).
 */
suspend fun fetchCricketMatches(dateYyyymmdd: String? = null): List<SportsMatchEvent> {
    val dateParam = dateYyyymmdd?.let { "&dates=$it" }.orEmpty()
    val body = httpGetText("https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=cricket$dateParam") ?: return emptyList()
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

/**
 * A followed cricket team's live game if it has one right now, else its
 * actual most recent result — not just "whatever [fetchCricketMatches]'s
 * default window still happens to contain," which for a senior international
 * side can go empty within a day or so of full-time (verified live: India's
 * last match against Sri Lanka had already dropped out of the undated feed
 * five days after full-time, on a day when 28 *other* cricket matches
 * worldwide were still listed). Checks today's default feed first — the
 * common case, covering a live match or something that finished within the
 * last day — then only pays for extra calls, one real HTTP request per day
 * walked backward, when that comes up empty; stops at the first (most
 * recent) day that has anything for this team. [maxDaysBack] bounds the
 * worst case (a team with genuinely nothing scheduled in a month) so this
 * can't spiral into dozens of requests.
 *
 * **The backward walk is cached** ([lookbackCache]). Only its result is —
 * today's undated feed is still fetched fresh on every call, so a live match
 * or a just-finished one is never served stale. That matters because the walk
 * is only ever reached when the fresh feed has *nothing* for this team, and
 * its answer is then either a finished match or nothing at all — both stable
 * until the team next plays, which the fresh check above would catch first.
 *
 * Without this, a followed team that is simply out of season cost the full
 * `1 + maxDaysBack` = 31 sequential HTTP requests on **every** refresh, every
 * 30 minutes, forever — the single most expensive thing this app could do to
 * a radio — to re-derive an answer that had not changed. The cache is
 * process-scoped rather than persisted, which is enough in practice because
 * TileShell is the Home app and its process is long-lived; a cold process
 * simply pays for one walk again.
 */
private class CricketLookback(val match: SportsMatchEvent?, val resolvedAtMillis: Long)

private val lookbackCache = ConcurrentHashMap<String, CricketLookback>()

private const val CRICKET_LOOKBACK_TTL_MS = 6L * 60 * 60 * 1000

suspend fun fetchRecentCricketMatchForTeam(teamId: String, nowMillis: Long, maxDaysBack: Int = 30): SportsMatchEvent? {
    val today = fetchCricketMatches().filter { it.homeId == teamId || it.awayId == teamId }
    pickRelevantMatch(today, nowMillis)?.let { return it }

    lookbackCache[teamId]
        ?.takeIf { nowMillis - it.resolvedAtMillis in 0 until CRICKET_LOOKBACK_TTL_MS }
        ?.let { return it.match }

    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
    repeat(maxDaysBack) {
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val dateParam = String.format(
            java.util.Locale.US,
            "%04d%02d%02d",
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
        )
        val events = fetchCricketMatches(dateParam).filter { it.homeId == teamId || it.awayId == teamId }
        pickRelevantMatch(events, nowMillis)?.let { found ->
            lookbackCache[teamId] = CricketLookback(found, nowMillis)
            return found
        }
    }
    // Cache the miss too — it is the expensive case, and "this team has played
    // nothing in 30 days" is the slowest-changing answer of all.
    lookbackCache[teamId] = CricketLookback(null, nowMillis)
    return null
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
