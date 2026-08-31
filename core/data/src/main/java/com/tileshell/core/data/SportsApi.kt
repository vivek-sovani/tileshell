package com.tileshell.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

private const val ESPN_BASE = "https://site.api.espn.com/apis/site/v2/sports"

/** One team in a league's roster, as returned by ESPN's `/teams` endpoint. */
data class SportsTeam(val id: String, val displayName: String, val abbreviation: String)

/**
 * One game, either from a team's own schedule or a league-wide scoreboard.
 * [epochMillis]/[state] ("pre"/"in"/"post") come straight from ESPN;
 * [statusDetail] is their own human-readable phrase ("Final", "Q3 4:12",
 * "Sat 7:00 PM EDT"). Matching a followed team against [homeId]/[awayId] (not
 * the abbreviation, which cricket's international sides don't expose as
 * reliably) is what lets [snapshotFor] work identically for both club sports
 * and [fetchCricketMatches]'s cross-tournament feed.
 */
data class SportsMatchEvent(
    val id: String,
    val epochMillis: Long,
    val state: String,
    val statusDetail: String,
    val homeId: String,
    val homeAbbr: String,
    val homeName: String,
    val homeScore: String,
    val awayId: String,
    val awayAbbr: String,
    val awayName: String,
    val awayScore: String,
    // Only set by fetchCricketMatches — cricket's summary endpoint is keyed by
    // the tournament's own league id, unlike a club sport's fixed leagueSlug,
    // so this is how fetchMatchDetail knows what to ask for.
    val leagueId: String? = null,
)

/**
 * Extra detail worth showing on a sports tile's back face beyond the plain
 * score: who's contributing right now (see [fetchMatchDetail]), plus the
 * match's own ESPN web page for "tap the tile to see the full page."
 * [homeScore]/[awayScore] are a **corrected** re-read of the score straight
 * from this same summary call — ESPN's per-team `/schedule` endpoint (what
 * [fetchSportsSchedule] uses) never inlines a soccer score at all (it's a
 * `$ref` link to a separate call) and only sometimes inlines other sports'
 * (an MLB/NBA schedule score is `{value, displayValue}`, not a plain
 * string) — so [SportsTileFace]'s poll loop patches the schedule-derived
 * snapshot with these once the summary call (already being made for the web
 * link/contributors) resolves the real number. [battingLines]/[bowlingLines]
 * are cricket-only (see `parseCricketContributors`); [contributorLines] is
 * soccer's goal-scorer list — kept separate rather than one shared list so
 * the tile's back face can give cricket's batting/bowling their own
 * sections instead of interleaving unrelated lines.
 */
data class SportsMatchDetail(
    val webUrl: String?,
    val contributorLines: List<String> = emptyList(),
    val battingLines: List<String> = emptyList(),
    val bowlingLines: List<String> = emptyList(),
    val homeScore: String? = null,
    val awayScore: String? = null,
)

/**
 * A schedule event reframed from the followed team's own point of view.
 * [teamAbbr]/[teamName] are the followed team's own identity — kept
 * alongside the opponent's so the front face can show both sides
 * symmetrically (team vs. opponent), not just "us."
 */
data class SportsSnapshot(
    val isHome: Boolean,
    val teamAbbr: String,
    val teamName: String,
    val teamScore: String,
    val opponentAbbr: String,
    val opponentName: String,
    val opponentScore: String,
    val state: String,
    val statusDetail: String,
)

/**
 * Picks which of a set of games is worth showing right now: a live game
 * first, else the most recently finished one, else the soonest upcoming
 * one. Pure — plain [SportsMatchEvent] values in, no clock/network call
 * inside — so this three-way priority is unit-testable directly.
 */
fun pickRelevantMatch(events: List<SportsMatchEvent>, nowMillis: Long): SportsMatchEvent? {
    events.firstOrNull { it.state == "in" }?.let { return it }
    events.filter { it.state == "post" && it.epochMillis <= nowMillis }
        .maxByOrNull { it.epochMillis }
        ?.let { return it }
    return events.filter { it.epochMillis > nowMillis }.minByOrNull { it.epochMillis }
}

/**
 * Reframes [event] from the perspective of the team identified by [ourId]
 * (ESPN's own team id, not the abbreviation) — pure, so the home/away and
 * score-ordering logic is unit-testable without a real event object from
 * the network.
 */
fun snapshotFor(event: SportsMatchEvent, ourId: String): SportsSnapshot {
    val weAreHome = event.homeId == ourId
    return SportsSnapshot(
        isHome = weAreHome,
        teamAbbr = if (weAreHome) event.homeAbbr else event.awayAbbr,
        teamName = if (weAreHome) event.homeName else event.awayName,
        teamScore = if (weAreHome) event.homeScore else event.awayScore,
        opponentAbbr = if (weAreHome) event.awayAbbr else event.homeAbbr,
        opponentName = if (weAreHome) event.awayName else event.homeName,
        opponentScore = if (weAreHome) event.awayScore else event.homeScore,
        state = event.state,
        statusDetail = event.statusDetail,
    )
}

/**
 * Splits a multi-innings cricket score like `"290 & 177/7 (46.5 ov)"` into
 * per-innings display lines (`["290", "177/7 (46.5 ov)"]`) — a single-innings
 * score (any club sport, or a cricket team yet to bat a second time) just
 * comes back as its own one-element list, unchanged.
 */
fun splitInningsScore(score: String): List<String> =
    score.split(" & ").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(score) }

/** ESPN's schedule dates omit seconds ("2026-08-29T11:30Z"), which [Instant.parse] rejects outright. */
internal fun parseEspnInstant(iso: String): Long? = runCatching { Instant.parse(iso).toEpochMilli() }
    .recoverCatching { Instant.parse(iso.removeSuffix("Z") + ":00Z").toEpochMilli() }
    .getOrNull()

/**
 * Blocking-IO HTTP GET returning the body text, or null on any failure — same
 * shape as OpenMeteoWeather's. [headers] is empty by default (every existing
 * caller here is unaffected); [fetchStockQuote]/[fetchStockSearch] pass a
 * browser `User-Agent` — verified live that Yahoo Finance's endpoints 429 a
 * generic non-browser one, unlike ESPN's, which never needed this.
 */
internal suspend fun httpGetText(url: String, headers: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

/** Every team in [leagueSlug] (e.g. `"basketball/nba"`) — empty on any failure. */
suspend fun fetchSportsTeams(leagueSlug: String): List<SportsTeam> {
    val body = httpGetText("$ESPN_BASE/$leagueSlug/teams?limit=200") ?: return emptyList()
    return runCatching {
        val root = JSONObject(body)
        val teams = root.getJSONArray("sports").getJSONObject(0)
            .getJSONArray("leagues").getJSONObject(0)
            .getJSONArray("teams")
        (0 until teams.length()).mapNotNull { i ->
            val t = teams.getJSONObject(i).optJSONObject("team") ?: return@mapNotNull null
            val id = t.optString("id").ifEmpty { return@mapNotNull null }
            SportsTeam(
                id = id,
                displayName = t.optString("displayName"),
                abbreviation = t.optString("abbreviation"),
            )
        }
    }.getOrDefault(emptyList())
}

/** [teamId]'s full schedule in [leagueSlug] — empty on any failure. */
suspend fun fetchSportsSchedule(leagueSlug: String, teamId: String): List<SportsMatchEvent> {
    val body = httpGetText("$ESPN_BASE/$leagueSlug/teams/$teamId/schedule") ?: return emptyList()
    return runCatching {
        val events = JSONObject(body).getJSONArray("events")
        (0 until events.length()).mapNotNull { i -> parseEvent(events.getJSONObject(i)) }
    }.getOrDefault(emptyList())
}

/**
 * Every game [leagueSlug] has on today's board (ESPN's own default date
 * window) — used to surface "ongoing now" matches in the team picker so a
 * user can jump straight to a team that's currently playing, per the same
 * idea [fetchCricketMatches] already gives cricket for free.
 */
suspend fun fetchSportsScoreboard(leagueSlug: String): List<SportsMatchEvent> {
    val body = httpGetText("$ESPN_BASE/$leagueSlug/scoreboard") ?: return emptyList()
    return runCatching {
        val events = JSONObject(body).getJSONArray("events")
        (0 until events.length()).mapNotNull { i -> parseEvent(events.getJSONObject(i)) }
    }.getOrDefault(emptyList())
}

/**
 * The match's own ESPN web page, plus — for a soccer match — its goal
 * scorers (`keyEvents` entries of type `"goal"`, newest first). Other club
 * sports get the web link only for now; [fetchCricketMatchDetail] is the
 * cricket equivalent. Empty/null fields on any failure, never a crash — same
 * "degrade to just the score" contract every other opt-in tile follows.
 */
suspend fun fetchMatchDetail(leagueSlug: String, eventId: String): SportsMatchDetail {
    val body = httpGetText("$ESPN_BASE/$leagueSlug/summary?event=$eventId")
        ?: return SportsMatchDetail(webUrl = null)
    return runCatching {
        val root = JSONObject(body)
        val webUrl = findSummaryLink(root.optJSONObject("header")?.optJSONArray("links"))
        val scorers = if (leagueSlug.startsWith("soccer/")) parseSoccerScorers(root) else emptyList()
        val scores = parseHeaderScores(root)
        SportsMatchDetail(
            webUrl = webUrl,
            contributorLines = scorers,
            homeScore = scores["home"],
            awayScore = scores["away"],
        )
    }.getOrDefault(SportsMatchDetail(webUrl = null))
}

/**
 * The real, always-inline score for each side straight from the summary
 * endpoint's own header — `home`/`away` keys, not team ids, since that's all
 * [SportsTileFace] needs to patch a [SportsMatchEvent] copy. Empty map on any
 * failure or a shape that doesn't match (never a crash — same "degrade to
 * whatever the schedule call already had" contract as everything else here).
 */
internal fun parseHeaderScores(root: JSONObject): Map<String, String> {
    val competitors = root.optJSONObject("header")
        ?.optJSONArray("competitions")?.optJSONObject(0)
        ?.optJSONArray("competitors") ?: return emptyMap()
    val out = mutableMapOf<String, String>()
    for (i in 0 until competitors.length()) {
        val c = competitors.getJSONObject(i)
        val homeAway = c.optString("homeAway").ifEmpty { continue }
        val score = c.optString("score").ifEmpty { continue }
        out[homeAway] = score
    }
    return out
}

internal fun findSummaryLink(links: JSONArray?): String? {
    if (links == null) return null
    for (i in 0 until links.length()) {
        val link = links.getJSONObject(i)
        val rels = link.optJSONArray("rel") ?: continue
        for (r in 0 until rels.length()) {
            if (rels.getString(r) == "summary") return link.optString("href").ifEmpty { null }
        }
    }
    return links.optJSONObject(0)?.optString("href")?.ifEmpty { null }
}

/** Goal scorers newest-first, e.g. "Dan Ndoye 24' (Nottingham Forest)" — up to 4 lines. */
internal fun parseSoccerScorers(root: JSONObject): List<String> {
    val events = root.optJSONArray("keyEvents") ?: return emptyList()
    val goals = mutableListOf<String>()
    for (i in 0 until events.length()) {
        val ev = events.getJSONObject(i)
        if (ev.optJSONObject("type")?.optString("type") != "goal") continue
        val scorer = ev.optJSONArray("participants")?.optJSONObject(0)
            ?.optJSONObject("athlete")?.optString("displayName")?.ifEmpty { null } ?: continue
        val minute = ev.optJSONObject("clock")?.optString("displayValue").orEmpty()
        val team = ev.optJSONObject("team")?.optString("displayName").orEmpty()
        goals.add("$scorer $minute ($team)".replace("  ", " "))
    }
    return goals.asReversed().take(4)
}

private fun parseEvent(ev: JSONObject): SportsMatchEvent? {
    val epochMillis = parseEspnInstant(ev.optString("date")) ?: return null
    val comp = ev.optJSONArray("competitions")?.optJSONObject(0) ?: return null
    val statusType = comp.optJSONObject("status")?.optJSONObject("type") ?: return null
    val competitors = comp.optJSONArray("competitors") ?: return null
    var home: JSONObject? = null
    var away: JSONObject? = null
    for (i in 0 until competitors.length()) {
        val c = competitors.getJSONObject(i)
        if (c.optString("homeAway") == "home") home = c else away = c
    }
    val homeTeam = home?.optJSONObject("team") ?: return null
    val awayTeam = away?.optJSONObject("team") ?: return null
    return SportsMatchEvent(
        id = ev.optString("id"),
        epochMillis = epochMillis,
        state = statusType.optString("state"),
        statusDetail = statusType.optString("shortDetail", statusType.optString("detail")),
        homeId = homeTeam.optString("id"),
        homeAbbr = homeTeam.optString("abbreviation"),
        homeName = homeTeam.optString("shortDisplayName", homeTeam.optString("displayName")),
        homeScore = scoreOf(home),
        awayId = awayTeam.optString("id"),
        awayAbbr = awayTeam.optString("abbreviation"),
        awayName = awayTeam.optString("shortDisplayName", awayTeam.optString("displayName")),
        awayScore = scoreOf(away),
    )
}

/**
 * A competitor's score, however ESPN chose to shape it this time: a plain
 * string on `/scoreboard` (every sport), `{value, displayValue}` on a club
 * sport's own `/teams/{id}/schedule` (MLB/NBA/NHL), or a bare `{"$ref": ...}`
 * link with no inline value at all on a *soccer* team's `/schedule` — the
 * last of those has no number to show at all here, so it degrades to "-"
 * rather than [JSONObject.toString]'ing the reference into the tile (the bug
 * this guards against: a raw `{"$ref":"http:\/\/sports.core.api..."}` blob
 * was rendering as the score). [SportsTileFace] patches soccer's "-" with
 * the real number from [fetchMatchDetail]'s [SportsMatchDetail.homeScore]/
 * [SportsMatchDetail.awayScore] once that call resolves.
 */
internal fun scoreOf(competitor: JSONObject): String = when (val raw = competitor.opt("score")) {
    is String -> raw
    is JSONObject -> raw.optString("displayValue").ifEmpty { "-" }
    else -> "-"
}
