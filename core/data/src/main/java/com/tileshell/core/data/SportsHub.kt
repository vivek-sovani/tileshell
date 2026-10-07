package com.tileshell.core.data

/** A match with the competition it belongs to, for the sports hub's lists. */
data class HubMatch(val event: SportsMatchEvent, val leagueSlug: String, val leagueName: String) {
    val key: String get() = "$leagueSlug/${event.id}"
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Which matches a sports hub page lists: only those of the favourite teams, or all of the favourite sports. */
enum class HubScope(val label: String) { MY_TEAMS("my teams"), MY_SPORTS("my sports") }

/** How far back the results page looks. */
enum class ResultsRange(val label: String, val days: Int) { WEEK("last week", 7), MONTH("last month", 30) }

/**
 * Whether [match] belongs under [scope]: a favourite team plays in it, or it is
 * in a favourite sport. With no sport marked, "my sports" is every sport.
 */
fun inScope(match: HubMatch, scope: HubScope, fav: SportsFavorites): Boolean = when (scope) {
    HubScope.MY_TEAMS -> fav.teams.any { involves(match, it) }
    HubScope.MY_SPORTS -> fav.sports.isEmpty() || match.leagueSlug in fav.sports
}

/**
 * The "live" page: today's matches, live ones first (newest start first),
 * then those still to be played (soonest first, so the kick-off time reads in
 * order), then those already finished (newest first). A match in play stays
 * even if it began on an earlier day (a multi-day cricket match). "Today" is
 * the calendar day of [nowMillis] in the zone [zoneOffsetMillis] from UTC. Pure.
 */
fun hubTodayMatches(matches: List<HubMatch>, nowMillis: Long, zoneOffsetMillis: Long = 0L): List<HubMatch> {
    val live = matches.filter { it.event.state == SPORTS_STATE_LIVE }.sortedByDescending { it.event.epochMillis }
    val today = matches.filter { it.event.state != SPORTS_STATE_LIVE && sameDay(it.event.epochMillis, nowMillis, zoneOffsetMillis) }
    val scheduled = today.filter { it.event.state != SPORTS_STATE_FINAL }.sortedBy { it.event.epochMillis }
    val finished = today.filter { it.event.state == SPORTS_STATE_FINAL }.sortedByDescending { it.event.epochMillis }
    return (live + scheduled + finished).distinctBy { it.key }
}

/** The "results" page: finished matches from the last [range] days, newest first. */
fun hubResults(matches: List<HubMatch>, range: ResultsRange, nowMillis: Long): List<HubMatch> =
    matches.filter {
        it.event.state == SPORTS_STATE_FINAL && it.event.epochMillis <= nowMillis &&
            nowMillis - it.event.epochMillis <= range.days * DAY_MS
    }.distinctBy { it.key }.sortedByDescending { it.event.epochMillis }

/**
 * The calendar days (`yyyyMMdd`, newest first, starting with today) a results
 * lookup walks: [days] of them, in the zone [zoneOffsetMillis] from UTC. Pure.
 */
fun resultDays(nowMillis: Long, days: Int, zoneOffsetMillis: Long = 0L): List<String> =
    (0 until days).map { back ->
        val epochDay = Math.floorDiv(nowMillis + zoneOffsetMillis, DAY_MS) - back
        java.time.LocalDate.ofEpochDay(epochDay).let { String.format(java.util.Locale.US, "%04d%02d%02d", it.year, it.monthValue, it.dayOfMonth) }
    }

/** A match that has not started: "today 7:30 pm" / "tomorrow 1:30 am" / "12 oct 7:30 pm", in [zone]. */
fun kickoffLabel(epochMillis: Long, nowMillis: Long, zone: java.time.ZoneId): String {
    val at = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val day = when (at.toLocalDate()) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        today.minusDays(1) -> "yesterday"
        else -> "${at.dayOfMonth} ${at.month.name.take(3).lowercase()}"
    }
    val hour12 = (at.hour % 12).let { if (it == 0) 12 else it }
    val minutes = if (at.minute == 0) "" else ":%02d".format(at.minute)
    return "$day $hour12$minutes ${if (at.hour < 12) "am" else "pm"}"
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

fun involves(match: HubMatch, team: SportsTile.Selection): Boolean =
    match.leagueSlug == team.leagueSlug && (match.event.homeId == team.teamId || match.event.awayId == team.teamId)
