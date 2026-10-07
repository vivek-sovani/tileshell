package com.tileshell.core.data

/** A match with the competition it belongs to, for the sports hub's lists. */
data class HubMatch(val event: SportsMatchEvent, val leagueSlug: String, val leagueName: String) {
    val key: String get() = "$leagueSlug/${event.id}"
}

/** The sports hub's "fixtures" filters. */
enum class FixtureTab(val label: String) { TODAY("today"), WEEK("week"), RESULTS("results") }

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val WEEK_MS = 7 * DAY_MS

/**
 * What the "live" page shows: matches in play first (newest start first),
 * then those about to start, then those that finished recently — each group
 * within [SAME_DAY_MS] of now, the same window the tile uses. Pure.
 */
fun hubLiveMatches(matches: List<HubMatch>, nowMillis: Long): List<HubMatch> {
    val live = matches.filter { it.event.state == "in" }.sortedByDescending { it.event.epochMillis }
    val upcoming = matches.filter {
        it.event.state != "in" && it.event.state != "post" &&
            it.event.epochMillis > nowMillis && it.event.epochMillis - nowMillis <= SAME_DAY_MS
    }.sortedBy { it.event.epochMillis }
    val finished = matches.filter {
        it.event.state == "post" && it.event.epochMillis <= nowMillis && nowMillis - it.event.epochMillis <= SAME_DAY_MS
    }.sortedByDescending { it.event.epochMillis }
    return (live + upcoming + finished).distinctBy { it.key }
}

/** The "fixtures" page for [tab]: today's and the week's games soonest first, results newest first. */
fun hubFixtures(matches: List<HubMatch>, tab: FixtureTab, nowMillis: Long, zoneOffsetMillis: Long = 0L): List<HubMatch> {
    val notPlayed = matches.filter { it.event.state != "post" && it.event.state != "in" }
    val picked = when (tab) {
        FixtureTab.TODAY -> matches.filter { sameDay(it.event.epochMillis, nowMillis, zoneOffsetMillis) }
            .sortedBy { it.event.epochMillis }
        FixtureTab.WEEK -> notPlayed.filter { it.event.epochMillis > nowMillis && it.event.epochMillis - nowMillis <= WEEK_MS }
            .sortedBy { it.event.epochMillis }
        FixtureTab.RESULTS -> matches.filter { it.event.state == "post" && it.event.epochMillis <= nowMillis }
            .sortedByDescending { it.event.epochMillis }.take(10)
    }
    return picked.distinctBy { it.key }
}

private fun sameDay(a: Long, b: Long, offsetMillis: Long) = Math.floorDiv(a + offsetMillis, DAY_MS) == Math.floorDiv(b + offsetMillis, DAY_MS)

/** "W", "L" or "D" from the followed side's point of view, or null when the scores aren't plain numbers (cricket innings). */
fun resultLetter(snapshot: SportsSnapshot): String? {
    if (snapshot.state != "post") return null
    val ours = snapshot.teamScore.trim().toIntOrNull() ?: return null
    val theirs = snapshot.opponentScore.trim().toIntOrNull() ?: return null
    return when {
        ours > theirs -> "W"
        ours < theirs -> "L"
        else -> "D"
    }
}

/** The sports and teams the user marked as favourites on the sports hub. */
data class SportsFavorites(
    /** League slugs ([SportsLeague.slug], including [CRICKET_LEAGUE_SLUG]). */
    val sports: List<String> = emptyList(),
    val teams: List<SportsTile.Selection> = emptyList(),
) {
    fun isTeamFavorite(leagueSlug: String, teamId: String) = teams.any { it.leagueSlug == leagueSlug && it.teamId == teamId }
}

/** Marks or unmarks a sport; unmarking one also drops its teams, which would otherwise be hidden. */
fun toggleFavoriteSport(fav: SportsFavorites, slug: String): SportsFavorites =
    if (slug in fav.sports) {
        SportsFavorites(fav.sports - slug, fav.teams.filterNot { it.leagueSlug == slug })
    } else {
        fav.copy(sports = fav.sports + slug)
    }

/** Marks or unmarks a team; marking one also marks its sport. */
fun toggleFavoriteTeam(fav: SportsFavorites, team: SportsTile.Selection): SportsFavorites =
    if (fav.isTeamFavorite(team.leagueSlug, team.teamId)) {
        fav.copy(teams = fav.teams.filterNot { it.leagueSlug == team.leagueSlug && it.teamId == team.teamId })
    } else {
        fav.copy(
            sports = if (team.leagueSlug in fav.sports) fav.sports else fav.sports + team.leagueSlug,
            teams = fav.teams + team,
        )
    }

fun encodeFavoriteSports(sports: List<String>): String = sports.joinToString("\n")

fun decodeFavoriteSports(raw: String?): List<String> =
    raw.orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** One `sports:<league>|<team id>|<label>` line per team, the sports tile's own encoding. */
fun encodeFavoriteTeams(teams: List<SportsTile.Selection>): String =
    teams.joinToString("\n") { SportsTile.encode(it.leagueSlug, it.teamId, it.teamLabel.replace('\n', ' ')) }

fun decodeFavoriteTeams(raw: String?): List<SportsTile.Selection> =
    raw.orEmpty().split("\n").mapNotNull { SportsTile.decode(it) }.distinctBy { it.leagueSlug to it.teamId }

/**
 * The "live" page for favourites: only matches from the favourite sports
 * (everything, when none is marked), in [hubLiveMatches] order but with
 * games a favourite team plays in kept ahead of the rest.
 */
fun hubFavoriteMatches(matches: List<HubMatch>, fav: SportsFavorites, nowMillis: Long): List<HubMatch> {
    val inSports = if (fav.sports.isEmpty()) matches else matches.filter { it.leagueSlug in fav.sports }
    val ordered = hubLiveMatches(inSports, nowMillis)
    return ordered.sortedByDescending { match -> fav.teams.any { involves(match, it) } }
}

fun involves(match: HubMatch, team: SportsTile.Selection): Boolean =
    match.leagueSlug == team.leagueSlug && (match.event.homeId == team.teamId || match.event.awayId == team.teamId)

/** A team's finished games, newest first, at most [limit]: its history. */
fun teamHistory(matches: List<HubMatch>, teamId: String, nowMillis: Long, limit: Int = 5): List<HubMatch> =
    matches.filter {
        it.event.state == SPORTS_STATE_FINAL && it.event.epochMillis <= nowMillis &&
            (it.event.homeId == teamId || it.event.awayId == teamId)
    }.distinctBy { it.key }.sortedByDescending { it.event.epochMillis }.take(limit)

/** Finished games across the favourite sports from the last [days] days, newest first. */
fun recentSportResults(matches: List<HubMatch>, sports: List<String>, nowMillis: Long, days: Int = 3, limit: Int = 15): List<HubMatch> =
    matches.filter {
        it.leagueSlug in sports && it.event.state == SPORTS_STATE_FINAL &&
            it.event.epochMillis <= nowMillis && nowMillis - it.event.epochMillis <= days * DAY_MS
    }.distinctBy { it.key }.sortedByDescending { it.event.epochMillis }.take(limit)

/**
 * How long the sports hub waits before refreshing while it is on screen:
 * [configuredMs] (the user's "live data refresh" rate for sports) whenever a
 * match is live or about to start, otherwise sleeping until the run-up to the
 * next kick-off, never longer than [SPORTS_IDLE_REFRESH_MS] — the same reasoning
 * as [shouldFetchSports]: a match that isn't in progress has nothing to poll
 * for. Pure.
 */
fun hubSportsRefreshDelayMs(events: List<SportsMatchEvent>, nowMillis: Long, configuredMs: Long): Long {
    if (events.any { it.state == SPORTS_STATE_LIVE }) return configuredMs
    val nextKickoff = events.filter { it.state != SPORTS_STATE_FINAL && it.epochMillis > nowMillis }.minOfOrNull { it.epochMillis }
        ?: return maxOf(configuredMs, SPORTS_IDLE_REFRESH_MS)
    val untilWake = nextKickoff - SPORTS_PREGAME_WAKE_MS - nowMillis
    return if (untilWake <= 0) configuredMs else untilWake.coerceIn(configuredMs, maxOf(configuredMs, SPORTS_IDLE_REFRESH_MS))
}
